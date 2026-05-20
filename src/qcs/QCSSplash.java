package qcs;

import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Splash screen for Quantum Circuit Simulator.
 * <p>
 * Shows logo, app title, author, and loading indicator.
 * Used at application startup to provide a polished initial user experience.
 * </p>
 *
 * @author Bhumit Buha
 * @version 2.0 (A4)
 */
public class QCSSplash {

    /**
     * Displays the splash screen, then launches the main app after a delay.
     *
     * @param mainStage   The stage for the main window (will be passed to controller)
     * @param afterSplash Runnable to launch main app after splash
     */
    public static void showSplash(Stage mainStage, Runnable afterSplash) {
        Stage splashStage = new Stage();
        VBox splash = new VBox(12);
        splash.setAlignment(Pos.CENTER);
        splash.setStyle("-fx-background-color: black;");

        ImageView icon = new ImageView(new Image("file:../images/qcsicon.jpg"));
        icon.setFitHeight(100);
        icon.setPreserveRatio(true);

        Label title = new Label("Quantum Circuit Simulator");
        title.setStyle("-fx-font-size: 24px; -fx-text-fill: gold; -fx-font-weight: bold;");

        Label author = new Label("By Bhumit Buha - 04109070");
        author.setStyle("-fx-font-size: 14px; -fx-text-fill: white;");

        Label loading = new Label("Loading...");
        loading.setStyle("-fx-font-size: 16px; -fx-text-fill: #BBBBBB;");

        splash.getChildren().addAll(icon, title, author, loading);

        Scene scene = new Scene(splash, 400, 270);
        splashStage.setScene(scene);
        splashStage.setTitle("Quantum Circuit Simulator – Loading...");
        splashStage.show();

        // Pause for 3 seconds, then close splash and run main app
        PauseTransition pause = new PauseTransition(Duration.seconds(3));
        pause.setOnFinished(e -> {
            splashStage.close();
            afterSplash.run();
        });
        pause.play();
    }
}
