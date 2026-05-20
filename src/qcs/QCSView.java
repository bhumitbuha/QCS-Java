package qcs;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.ResourceBundle;

/**
 * QCSView - Handles all visual components of the Quantum Circuit Simulator.
 * <p>
 * Purely UI (no logic, no event handling here). All wiring is done in the Controller.
 * Provides the layout for menu, banner, grid, gate selection, message panel, and controls.
 * </p>
 *
 * @author Bhumit
 * @version A4
 */
public class QCSView {
    /** Root BorderPane for the application's scene graph */
    private final BorderPane root;
    /** Quantum circuit grid panel */
    public final QCSPanel gridPanel;
    /** Message area for system/user messages */
    public final TextArea messageArea;
    /** Label for current step in simulation */
    public final Label stepLabel;
    /** Scroll pane for quantum circuit grid */
    public final ScrollPane gridScrollPane;

    /**
     * Constructs the main view with all UI elements for QCS.
     *
     * @param stage    The main application window
     * @param model    The QCS model (for initial grid size)
     * @param messages Localized message bundle
     */
    public QCSView(Stage stage, QCSModel model, ResourceBundle messages) {
        root = new BorderPane();

        // Top area: Menu and Banner (controller will add actions to menu)
        VBox top = new VBox(createMenuBar(messages), createBanner());
        root.setTop(top);

        // Left area: Gate selection
        setupLeft(messages);

        // Center: GridPanel and MessagePanel
        gridPanel = new QCSPanel(model.getRows(), model.getCols());
        gridScrollPane = new ScrollPane(gridPanel.getGrid());
        gridScrollPane.setFitToWidth(true);
        gridScrollPane.setFitToHeight(true);
        gridScrollPane.setPadding(new Insets(10));

        messageArea = new TextArea();
        messageArea.setEditable(false);
        messageArea.setPrefHeight(70);
        messageArea.setWrapText(true);
        stepLabel = new Label();

        VBox messagePanel = new VBox(5,
                createControlsBox(messages),
                new Label(messages.getString("label.messages")),
                messageArea
        );
        messagePanel.setPadding(new Insets(10));

        VBox centerBox = new VBox(5, gridScrollPane, messagePanel);
        centerBox.setPadding(new Insets(10));
        VBox.setVgrow(gridScrollPane, Priority.ALWAYS);
        VBox.setVgrow(messagePanel, Priority.NEVER);
        root.setCenter(centerBox);

        // Finalize stage
        Scene scene = new Scene(root, 1000, 700);
        stage.setTitle(messages.getString("app.title"));
        stage.setScene(scene);
        stage.setResizable(true);
        stage.show();
    }

    /**
     * Creates the menu bar with all top-level menus and menu items.
     * Note: Event handlers should be attached by the controller.
     *
     * @param messages Localized messages
     * @return the MenuBar
     */
    private MenuBar createMenuBar(ResourceBundle messages) {
        MenuBar menuBar = new MenuBar();

        Menu fileMenu = new Menu(messages.getString("menu.file"));
        fileMenu.getItems().addAll(
                new MenuItem(messages.getString("menu.save")),
                new MenuItem(messages.getString("menu.load"))
        );

        Menu langMenu = new Menu(messages.getString("menu.language"));
        langMenu.getItems().addAll(
                new MenuItem(messages.getString("menu.english")),
                new MenuItem(messages.getString("menu.french"))
        );

        Menu themeMenu = new Menu(messages.getString("menu.theme"));
        themeMenu.getItems().addAll(
                new MenuItem(messages.getString("theme.light")),
                new MenuItem(messages.getString("theme.dark")),
                new MenuItem(messages.getString("theme.sky")),
                new MenuItem(messages.getString("theme.gray"))
        );

        Menu colorMenu = new Menu(messages.getString("menu.colors"));
        colorMenu.getItems().add(
                new MenuItem(messages.getString("menu.colors.gates"))
        );

        Menu gridMenu = new Menu(messages.getString("menu.grid"));
        gridMenu.getItems().add(new MenuItem(messages.getString("menu.grid.resize")));

        Menu modeMenu = new Menu(messages.getString("menu.mode"));
        modeMenu.getItems().addAll(
                new MenuItem(messages.getString("menu.mode.design")),
                new MenuItem(messages.getString("menu.mode.execution"))
        );

        Menu helpMenu = new Menu(messages.getString("menu.help"));
        helpMenu.getItems().addAll(
                new MenuItem(messages.getString("menu.about")),
                new MenuItem(messages.getString("menu.readme"))
        );

        menuBar.getMenus().addAll(fileMenu, langMenu, themeMenu, colorMenu, gridMenu, modeMenu, helpMenu);
        return menuBar;
    }

    /**
     * Creates the banner image displayed at the top center of the window.
     *
     * @return HBox containing the banner image
     */
    private HBox createBanner() {
        ImageView banner = new ImageView(new Image("file:images/qcs.png"));
        banner.setPreserveRatio(true);
        banner.setFitHeight(90);
        HBox box = new HBox(banner);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(10));
        return box;
    }

    /**
     * Sets up the gate selection panel on the left side of the window.
     * Only displays UI; controller is responsible for event wiring.
     *
     * @param messages Localized messages
     */
    private void setupLeft(ResourceBundle messages) {
        VBox left = new VBox(10);
        left.setPadding(new Insets(10));
        left.setPrefWidth(160);
        left.setStyle("-fx-background-color: #f0f0f0;");
        Label title = new Label(messages.getString("label.title"));
        Label singleLabel = new Label(messages.getString("label.single"));
        GridPane singleGrid = new GridPane();
        singleGrid.setHgap(5); singleGrid.setVgap(5);
        String[] single = {"I", "X", "Y", "Z", "H", "S", "T", "U"};
        for (int i = 0; i < single.length; i++) {
            Button b = new Button(single[i]);
            b.setPrefWidth(60);
            singleGrid.add(b, i % 2, i / 2);
        }
        Label multiLabel = new Label(messages.getString("label.multi"));
        VBox multiBox = new VBox(5);
        for (String gate : new String[]{"CX", "SWAP", "CU", "CCX"}) {
            Button btn = new Button(gate);
            btn.setMaxWidth(Double.MAX_VALUE);
            multiBox.getChildren().add(btn);
        }
        Label opLabel = new Label(messages.getString("label.operations"));
        Button barrier = new Button("BARRIER");
        barrier.setMaxWidth(Double.MAX_VALUE);

        left.getChildren().addAll(title, singleLabel, singleGrid, multiLabel, multiBox, opLabel, barrier);
        root.setLeft(left);
    }

    /**
     * Creates the control button bar with New, Step, Reset, and Step label.
     * The controller is responsible for attaching event handlers.
     *
     * @param messages Localized messages
     * @return HBox containing controls
     */
    private HBox createControlsBox(ResourceBundle messages) {
        Button newCircuit = new Button(messages.getString("button.new"));
        Button step = new Button(messages.getString("button.step"));
        Button reset = new Button(messages.getString("button.reset"));
        HBox controls = new HBox(10, newCircuit, step, reset, stepLabel);
        controls.setAlignment(Pos.CENTER_LEFT);
        return controls;
    }

    /**
     * Appends a message to the message area, adding a newline.
     *
     * @param msg Message to append
     */
    public void appendMessage(String msg) {
        messageArea.appendText(msg + "\n");
    }

    /**
     * Gets the root BorderPane (main application layout node).
     *
     * @return BorderPane for the window's Scene
     */
    public BorderPane getRoot() { return root; }
}
