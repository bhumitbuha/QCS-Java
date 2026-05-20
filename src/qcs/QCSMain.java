package qcs;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import java.util.Locale;

/**
 * Main entry point for the Quantum Circuit Simulator (QCS).
 * Supports passing a username from login and launching standalone.
 *
 * @author Bhumit
 * @version 2.0 (A4/A6)
 */
public class QCSMain extends Application {

    private static String passedUsername = null;

    /**
     * Launches the main app with a provided username (from login).
     * @param username logged-in user
     */
    public QCSMain(String username) {
        passedUsername = username;
    }

    /** No-arg constructor for JavaFX launcher */
    public QCSMain() {}

    /**
     * JavaFX start method called when the application launches.
     *
     * @param primaryStage The main stage provided by JavaFX.
     */
    @Override
    public void start(Stage primaryStage) {
        try {
            QCSSplash.showSplash(primaryStage, () -> {
                // Pass username to controller if set
                if (passedUsername != null && !passedUsername.isEmpty()) {
                    new QCSController(passedUsername).start(primaryStage);
                } else {
                    new QCSController().start(primaryStage);
                }
            });
        } catch (Exception ex) {
            ex.printStackTrace();
            Platform.exit();
        }
    }

    /**
     * Main method for launching the application via JVM.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        Locale.setDefault(Locale.ENGLISH);
        launch(args);
    }
}
