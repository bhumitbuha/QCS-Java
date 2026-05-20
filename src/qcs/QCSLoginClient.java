package qcs;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * JavaFX client app that provides a simple login &amp; connectivity screen for the QCS system.
 * <p>
 * UI sections:
 * <ul>
 *   <li>Banner image</li>
 *   <li>Connection strip (User, Server, Port, Connect, End)</li>
 *   <li>Actions (Password, Login, Sign Up)</li>
 *   <li>Log area (status messages)</li>
 * </ul>
 * Behavior:
 * <ul>
 *   <li>Connect: probes the server (host/port) and enables Login/Sign Up on success.</li>
 *   <li>Login/Sign Up: exchanges a single line over TCP using the protocol
 *       <code>username#P0#password</code> (login) or <code>username#P1#password</code> (signup).</li>
 *   <li>On successful response containing <code>OK</code>, launches the main MVC UI and passes the username.</li>
 * </ul>
 */
public class QCSLoginClient extends Application {
    private TextField userField, hostField, portField;
    private PasswordField passField;
    private TextArea logArea;
    private Button connectBtn, endBtn, loginBtn, signupBtn;

    /**
     * Standard JavaFX entry point.
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) { launch(args); }

    /**
     * Builds and shows the login client UI.
     *
     * @param stage primary JavaFX stage
     */
    @Override public void start(Stage stage) {
        stage.setTitle("Game Client - JAP (Summer 2025)");

        // banner
        ImageView banner = new ImageView();
        try { banner.setImage(new Image("file:images/QCClient.png"));
        } catch (Exception ignored) {}
        banner.setPreserveRatio(true);
        banner.setFitHeight(140);

        // top strip (User/Server/Port/Connect/End)
        userField = new TextField();
        userField.setPrefColumnCount(12);
        hostField = new TextField("localhost");
        hostField.setPrefColumnCount(12);
        portField = new TextField("12345");
        portField.setPrefColumnCount(6);

        connectBtn = new Button("Connect");
        endBtn = new Button("End");

        HBox strip = new HBox(8,
                new Label("User:"), userField,
                new Label("Server:"), hostField,
                new Label("Port:"), portField,
                connectBtn, endBtn
        );
        strip.setAlignment(Pos.CENTER_LEFT);

        // action buttons
        loginBtn = new Button("Login");
        signupBtn = new Button("Sign Up");
        loginBtn.setDisable(true);
        signupBtn.setDisable(true);

        HBox actions = new HBox(10, new Label("Password:"), (passField = new PasswordField()), loginBtn, signupBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        // log
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(8);

        VBox root = new VBox(10, banner, strip, actions, logArea);
        root.setPadding(new Insets(12));
        Scene scene = new Scene(root, 680, 420);
        stage.setScene(scene);
        stage.show();

        // Handlers
        connectBtn.setOnAction(e -> tryConnect());
        endBtn.setOnAction(e -> log("Client ended."));
        loginBtn.setOnAction(e -> doLogin(false));
        signupBtn.setOnAction(e -> doLogin(true));
    }

    /**
     * Attempts to connect to the server using the provided host/port.
     * Runs in a background thread and enables Login/Sign Up on success.
     * Logs any connection issues.
     */
    private void tryConnect() {
        connectBtn.setDisable(true);
        new Thread(() -> {
            String host = hostField.getText().trim();
            int port;
            try { port = Integer.parseInt(portField.getText().trim()); }
            catch (NumberFormatException nfe) { logLater("Invalid port."); Platform.runLater(() -> connectBtn.setDisable(false)); return; }
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, port), 2000);
                logLater("Connected to " + host + ":" + port);
                Platform.runLater(() -> { loginBtn.setDisable(false); signupBtn.setDisable(false); });
            } catch (IOException ioe) {
                logLater("Cannot reach server: " + ioe.getMessage());
            } finally {
                Platform.runLater(() -> connectBtn.setDisable(false));
            }
        }, "client-connect").start();
    }

    /**
     * Performs either login or sign up against the server, depending on the {@code signup} flag.
     * Uses protocol:
     * <ul>
     *   <li>Login: {@code username#P0#password}</li>
     *   <li>Sign up: {@code username#P1#password}</li>
     * </ul>
     * On success, launches the main MVC UI and passes the logged-in username to {@link QCSController}.
     *
     * @param signup {@code true} for sign up; {@code false} for login
     */
    private void doLogin(boolean signup) {
        String user = userField.getText().trim();
        String pass = passField.getText();
        if (user.isEmpty() || pass.isEmpty()) { log("Please fill user and password."); return; }

        String host = hostField.getText().trim();
        int port;
        try { port = Integer.parseInt(portField.getText().trim()); }
        catch (NumberFormatException nfe) { log("Invalid port."); return; }

        new Thread(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 3000);
                socket.setSoTimeout(5000);
                try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                    String proto = signup ? "P1" : "P0";
                    out.println(user + "#" + proto + "#" + pass);
                    String reply = in.readLine();
                    if (reply != null && reply.contains("OK")) {
                        logLater((signup ? "Signup" : "Login") + " OK. Launching MVC...");
                        // pass username to controller and open main UI
                        Platform.runLater(() -> {
                            QCSController.setLoggedInUser(user);
                            try { new QCSMain().start(new Stage()); }
                            catch (Exception ex) { log("Launch error: " + ex.getMessage()); }
                        });
                    } else {
                        logLater((signup ? "Signup" : "Login") + " failed.");
                    }
                }
            } catch (IOException ex) {
                logLater("Network error: " + ex.getMessage());
            }
        }, "client-login").start();
    }

    /**
     * Appends a line to the on-screen log immediately.
     *
     * @param s text to add
     */
    private void log(String s) { logArea.appendText(s + "\n"); }

    /**
     * Appends a line to the on-screen log on the JavaFX application thread.
     *
     * @param s text to add
     */
    private void logLater(String s) { Platform.runLater(() -> log(s)); }
}
