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
 * <h3>Protocol Commands</h3>
 * <ul>
 *     <li>{@code username#P0#password} → Login request (replies {@code username#P0#OK} or {@code FAIL})</li>
 *     <li>{@code username#P1#password} → Signup request (replies {@code username#P1#OK} or {@code FAIL})</li>
 *     <li>{@code username#P2#circuit}  → Save circuit (replies {@code username#P2#OK} or {@code FAIL})</li>
 *     <li>{@code username#P3}          → Load circuit (replies {@code username#P3#<circuit>} or {@code FAIL})</li>
 * </ul>
 *
 * <h3>GUI Controls</h3>
 * <ul>
 *     <li>Start – Start listening for clients on the specified port</li>
 *     <li>Create DB – Create/reset the SQLite database schema</li>
 *     <li>Show DB – Display all users and saved circuits in the log area</li>
 *     <li>Finalize – Placeholder for finalizing/closing client resources</li>
 *     <li>End – Shut down the server and exit the application</li>
 * </ul>
 *
 * <h3>Database Schema</h3>
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
        JButton btnEnd = new JButton("End");

        top.add(btnStart);
        top.add(btnCreateDB);
        top.add(btnShowDB);
        top.add(btnFinalize);
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
            Image img = new ImageIcon("../images/" + "QCServer.png")
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
                ps.setString(2, password);
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            } catch (SQLException e) { log("DB login error: " + e.getMessage()); return false; }
        }

        private boolean createUser(String username, String password) {
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement ps = conn.prepareStatement("INSERT INTO User(username,password) VALUES(?,?)")) {
                ps.setString(1, username);
                ps.setString(2, password);
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
