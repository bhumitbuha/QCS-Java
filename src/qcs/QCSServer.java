package qcs;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * QCSServer is a multi-threaded server application with a Swing GUI for managing
 * user accounts and quantum circuit data.
 * <p>
 * The server listens for client connections, processes commands according to
 * the QCS protocol, and stores user/circuit data in a SQLite database.
 * It supports login, signup, circuit save, and circuit load operations.
 * </p>
 *
 * <h2>Protocol Commands</h2>
 * <ul>
 *     <li>{@code username#P0#password} → Login request (replies {@code username#P0#OK} or {@code FAIL})</li>
 *     <li>{@code username#P1#password} → Signup request (replies {@code username#P1#OK} or {@code FAIL})</li>
 *     <li>{@code username#P2#circuit}  → Save circuit (replies {@code username#P2#OK} or {@code FAIL})</li>
 *     <li>{@code username#P3}          → Load circuit (replies {@code username#P3#<circuit>} or {@code FAIL})</li>
 * </ul>
 *
 * <h2>GUI Controls</h2>
 * <ul>
 *     <li>Start – Start listening for clients on the specified port</li>
 *     <li>Create DB – Create/reset the SQLite database schema</li>
 *     <li>Show DB – Display all users and saved circuits in the log area</li>
 *     <li>Finalize – Placeholder for finalizing/closing client resources</li>
 *     <li>End – Shut down the server and exit the application</li>
 * </ul>
 *
 * <h2>Database Schema</h2>
 * <pre>
 * User(id INTEGER PK AUTOINCREMENT, username TEXT UNIQUE, password TEXT)
 * QuantumCircuits(username TEXT PK, circuit TEXT)
 * </pre>
 *
 * This server is intended for use with the QCS client and controller classes.
 */
public class QCSServer extends JFrame {
    // --- Networking / DB config
    private static final String DB_URL = "jdbc:sqlite:qcs.db";
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    // --- GUI components
    private final JTextField portField;
    private final JTextArea logArea;

    /**
     * Application entry point.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new QCSServer().setVisible(true));
    }

    /**
     * Constructs the QCSServer GUI and initializes action listeners.
     */
    public QCSServer() {
        super("Game Server - JAP (Summer 2025)");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { shutdown(); }
        });

        JPanel content = new JPanel(new BorderLayout(10,10));
        content.setBorder(new EmptyBorder(10,10,10,10));
        setContentPane(content);

        // Banner image
        JLabel banner = new JLabel();
        banner.setHorizontalAlignment(SwingConstants.CENTER);
        banner.setIcon(loadImage());
        content.add(banner, BorderLayout.NORTH);

        // Top control panel
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        top.add(new JLabel("Port:"));
        portField = new JTextField("12345", 6);
        top.add(portField);

        JButton btnStart = new JButton("Start");
        JButton btnCreateDB = new JButton("Create DB");
        JButton btnShowDB = new JButton("Show DB");
        JButton btnFinalize = new JButton("Finalize");
        JButton btnLaunchClient = new JButton("Launch Client");
        JButton btnEnd = new JButton("End");

        top.add(btnStart);
        top.add(btnCreateDB);
        top.add(btnShowDB);
        top.add(btnFinalize);
        top.add(btnLaunchClient);
        top.add(btnEnd);
        content.add(top, BorderLayout.CENTER);

        // Log area
        logArea = new JTextArea(10, 60);
        logArea.setEditable(false);
        JScrollPane sp = new JScrollPane(logArea);
        content.add(sp, BorderLayout.SOUTH);

        // Action listeners
        btnStart.addActionListener(e -> onStart());
        btnCreateDB.addActionListener(e -> onCreateDB());
        btnShowDB.addActionListener(e -> onShowDB());
        btnFinalize.addActionListener(e -> onFinalize());
        btnLaunchClient.addActionListener(e -> launchClientProcess());
        btnEnd.addActionListener(e -> shutdown());

        setSize(640, 420);
        setLocationRelativeTo(null);

        log("Exec button...");
        log("port=" + portField.getText());
        log("Waiting for clients to connect...");
    }

    /**
     * Loads an image from the images directory and scales it.
     *
     * @return scaled ImageIcon, or {@code null} if loading fails
     */
    private ImageIcon loadImage() {
        try {
            Image img = new ImageIcon("images/" + "QCServer.png")
                    .getImage()
                    .getScaledInstance(520, 160, Image.SCALE_SMOOTH);
            return new ImageIcon(img);
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Starts the server and begins listening for client connections.
     */
    private void onStart() {
        if (running) { log("Server already running."); return; }
        int port;
        try { port = Integer.parseInt(portField.getText().trim()); }
        catch (NumberFormatException nfe) { log("Invalid port."); return; }

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                running = true;
                log("Server started on port " + port);

                while (running) {
                    Socket client = serverSocket.accept();
                    pool.execute(new ClientHandler(client));
                }
            } catch (IOException ioe) {
                log("Server error: " + ioe.getMessage());
            } finally {
                running = false;
                closeQuietly(serverSocket);
            }
        }, "qcs-server-accept").start();
    }

    /**
     * Creates or resets the SQLite database schema.
     */
    private void onCreateDB() {
        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DROP TABLE IF EXISTS User");
                stmt.executeUpdate("DROP TABLE IF EXISTS QuantumCircuits");
                stmt.executeUpdate("CREATE TABLE User (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE NOT NULL, password TEXT NOT NULL)");
                stmt.executeUpdate("CREATE TABLE QuantumCircuits (username TEXT PRIMARY KEY, circuit TEXT)");
            }
            log("Database created/reset.");
        } catch (Exception ex) {
            log("DB error: " + ex.getMessage());
        }
    }

    /**
     * Displays the contents of the database in the log area.
     */
    private void onShowDB() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            log("-- USERS --");
            try (PreparedStatement ps = conn.prepareStatement("SELECT id, username FROM User");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) log("  " + rs.getInt(1) + " : " + rs.getString(2));
            }
            log("-- CIRCUITS --");
            try (PreparedStatement ps = conn.prepareStatement("SELECT username, SUBSTR(circuit,1,60) FROM QuantumCircuits");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) log("  " + rs.getString(1) + " : " + rs.getString(2));
            }
        } catch (SQLException ex) {
            log("DB error: " + ex.getMessage());
        }
    }

    /**
     * Placeholder for finalizing/closing client resources.
     * (Currently no persistent client list is maintained.)
     */
    private void onFinalize() {
        log("Finalize requested (no active persistent sockets to close).");
    }

    /**
     * Shuts down the server, closes resources, and exits the application.
     */
    private void shutdown() {
        running = false;
        closeQuietly(serverSocket);
        pool.shutdownNow();
        log("Server terminated.");
        dispose();
        System.exit(0);
    }

    /**
     * Appends a message to the server log area.
     *
     * @param s the message
     */
    private void log(String s) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(s + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    /**
     * Quietly closes a ServerSocket, ignoring IOExceptions.
     *
     * @param ss the ServerSocket to close
     */
    private static void closeQuietly(ServerSocket ss) {
        if (ss != null) try { ss.close(); } catch (IOException ignored) {}
    }

    /**
     * Launches QCSLoginClient in a new JVM process.
     * JavaFX JARs are filtered out of the inherited classpath and placed on
     * --module-path instead, preventing the split-module conflict that causes
     * a silent crash when the same JAR appears on both paths.
     * Child process stderr/stdout is piped to the server log.
     */
    private void launchClientProcess() {
        String java    = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String workDir = System.getProperty("user.dir");

        // Build classpath: prefer known locations, fall back to current java.class.path
        // (filtered to remove JavaFX JARs — they go on --module-path only)
        List<String> cpEntries = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (!entry.isEmpty() && !entry.toLowerCase().contains("javafx")) {
                cpEntries.add(entry);
            }
        }
        // Ensure compiled classes and SQLite are always included
        File ideaOut = new File(workDir, "out/production/QCS");
        File batJar  = new File(workDir, "bin/qcsclient.jar");
        File sqJar   = new File(workDir, "lib/sqlite-jdbc.jar");
        if (ideaOut.exists() && !cpEntries.contains(ideaOut.getAbsolutePath()))
            cpEntries.add(0, ideaOut.getAbsolutePath());
        if (batJar.exists() && !cpEntries.contains(batJar.getAbsolutePath()))
            cpEntries.add(0, batJar.getAbsolutePath());
        if (sqJar.exists() && !cpEntries.contains(sqJar.getAbsolutePath()))
            cpEntries.add(sqJar.getAbsolutePath());

        File fxLib = new File(workDir, "javafx-sdk-26.0.1/lib");

        List<String> cmd = new ArrayList<>();
        cmd.add(java);
        if (fxLib.exists()) {
            cmd.add("--module-path");
            cmd.add(fxLib.getAbsolutePath());
            cmd.add("--add-modules");
            cmd.add("javafx.controls,javafx.fxml,javafx.graphics");
            cmd.add("--enable-native-access=javafx.graphics");
        }
        cmd.add("-cp");
        cmd.add(String.join(File.pathSeparator, cpEntries));
        cmd.add("qcs.QCSLoginClient");

        log("Launching: " + String.join(" ", cmd));
        try {
            Process p = new ProcessBuilder(cmd)
                    .directory(new File(workDir))
                    .redirectErrorStream(true)
                    .start();
            // Pipe client output to server log so errors are visible
            new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) log("[Client] " + line);
                } catch (IOException ignored) {}
            }, "client-log").start();
            log("Client launched.");
        } catch (IOException e) {
            log("Failed to launch client: " + e.getMessage());
        }
    }

    /**
     * Returns the SHA-256 hex digest of {@code password}.
     * Passwords are never stored or compared in plaintext.
     */
    private static String hashPassword(String password) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    /**
     * Handles communication with a single client.
     * Implements the QCS protocol.
     */
    private class ClientHandler implements Runnable {
        private final Socket socket;
        ClientHandler(Socket socket) { this.socket = socket; }

        @Override public void run() {
            String remote = socket.getRemoteSocketAddress().toString();
            log("Client connected: " + remote);
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                String line;
                while ((line = in.readLine()) != null) {
                    String[] parts = line.split("#", 3);
                    if (parts.length < 2) continue;
                    String user = parts[0], proto = parts[1];
                    String data = parts.length == 3 ? parts[2] : "";

                    switch (proto) {
                        case "P0": // login
                            out.println(user + "#P0#" + (checkLogin(user, data) ? "OK" : "FAIL"));
                            break;
                        case "P1": // signup
                            out.println(user + "#P1#" + (createUser(user, data) ? "OK" : "FAIL"));
                            break;
                        case "P2": // save circuit
                            out.println(user + "#P2#" + (saveCircuit(user, data) ? "OK" : "FAIL"));
                            break;
                        case "P3": // load circuit
                            String circuit = loadCircuit(user);
                            out.println(circuit != null ? user + "#P3#" + circuit : user + "#P3#FAIL");
                            break;
                        default:
                            out.println(user + "#" + proto + "#FAIL#Unknown");
                    }
                }
            } catch (IOException ioe) {
                log("Client IO error: " + ioe.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
                log("Client disconnected: " + remote);
            }
        }

        private boolean checkLogin(String username, String password) {
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM User WHERE username=? AND password=?")) {
                ps.setString(1, username);
                ps.setString(2, hashPassword(password));
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            } catch (SQLException e) { log("DB login error: " + e.getMessage()); return false; }
        }

        private boolean createUser(String username, String password) {
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement ps = conn.prepareStatement("INSERT INTO User(username,password) VALUES(?,?)")) {
                ps.setString(1, username);
                ps.setString(2, hashPassword(password));
                ps.executeUpdate();
                return true;
            } catch (SQLException e) { log("DB signup error: " + e.getMessage()); return false; }
        }

        private boolean saveCircuit(String username, String circuit) {
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement ps = conn.prepareStatement(
                         "REPLACE INTO QuantumCircuits(username,circuit) VALUES(?,?)")) {
                ps.setString(1, username);
                ps.setString(2, circuit);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) { log("DB save error: " + e.getMessage()); return false; }
        }

        private String loadCircuit(String username) {
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement ps = conn.prepareStatement("SELECT circuit FROM QuantumCircuits WHERE username=?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
            } catch (SQLException e) { log("DB load error: " + e.getMessage()); return null; }
        }
    }
}
