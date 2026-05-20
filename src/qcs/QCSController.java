package qcs;

import java.net.*;
import java.text.MessageFormat;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.scene.paint.Color;
import java.io.*;
import java.util.*;

/**
 * Controller for the Quantum Circuit Simulator (QCS) client UI.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Builds and manages the JavaFX UI (menu bar, banner, gate palette, grid, messages).</li>
 *   <li>Handles gate placement logic (single- and multi-qubit gates, barriers) and step simulation.</li>
 *   <li>Loads/saves circuits to local files.</li>
 *   <li>Provides a simple client connection strip (Host/Port/Connect/End) per assignment spec.</li>
 *   <li>Performs client/server communication for saving/loading circuits via a simple line protocol:
 *       <ul>
 *         <li><code>username#P2#&lt;circuit&gt;</code> — save circuit</li>
 *         <li><code>username#P3</code> — load circuit</li>
 *         <li><code>username#P0</code> — polite end/close notice</li>
 *       </ul>
 *   </li>
 * </ul>
 * <p>
 * Notes:
 * <ul>
 *   <li>Networking send/load operations run on background threads to avoid freezing the FX thread.</li>
 *   <li>Images are loaded using file URLs relative to <code>../images</code> to match your project layout.</li>
 *   <li>Text resources are resolved from <code>qcs.QCSMessages_*.properties</code>.</li>
 * </ul>
 */
public class QCSController {
    private static QCSController instance;
    private static String selectedGate = "H";
    private static final Set<String> MULTI_QUBIT_GATES = Set.of("CX", "SWAP", "CU", "CCX");

    private BorderPane root;
    private QCSPanel gridPanel;
    private TextArea messageArea;
    private Label stepLabel;
    private int currentStep = 0;
    private Locale currentLocale = Locale.ENGLISH;
    private ResourceBundle messages;
    private ScrollPane gridScrollPane;
    private VBox messagePanel;
    private Stage mainStage;
    private boolean inDesignMode = true;
    private final QCSModel model;
    private QCSGridButton firstMultiQubitButton = null;

    // NEW: Store username if provided by login
    private String username = null;

    // --- client "Connect / End" strip (host, port) ---
    private TextField hostField;
    private TextField portField;
    private volatile boolean connected = false;

    /**
     * Constructs the main QCSController and initializes the model.
     * Sets itself as the singleton instance.
     */
    public QCSController() {
        instance = this;
        this.model = new QCSModel();
        if (pendingUsername != null) this.username = pendingUsername;
    }

    /**
     * Constructs the controller with a known logged-in username.
     *
     * @param username the username obtained from a login flow (may be null)
     */
    public QCSController(String username) {
        instance = this;
        this.model = new QCSModel();
        this.username = username;
    }


    /**
     * @return the singleton controller instance (set during construction)
     */
    public static QCSController getInstance() { return instance; }

    /**
     * @return the gate currently selected in the palette (e.g., "H", "CX", "BARRIER")
     */
    public static String getSelectedGate() { return selectedGate; }

    /**
     * @param gate gate symbol
     * @return the configured color (hex) for the given gate symbol
     */
    public static String getGateColor(String gate) { return QCSModel.getGateColor(gate); }

    /**
     * Initializes and shows the main stage and all UI sections.
     *
     * @param stage primary JavaFX stage supplied by the application
     */
    public void start(Stage stage) {
        this.mainStage = stage;
        messages = ResourceBundle.getBundle("qcs.QCSMessages", currentLocale);
        root = new BorderPane();
        VBox top = new VBox(createMenuBar(), createBanner());
        root.setTop(top);
        setupLeft();
        setupCenter();
        setupBottom();

        // add the Connect/End strip above grid+messages
        VBox centerBox = new VBox(8, buildClientConnectStrip(), gridScrollPane, messagePanel);
        centerBox.setPadding(new Insets(10));
        VBox.setVgrow(gridScrollPane, Priority.ALWAYS);
        VBox.setVgrow(messagePanel, Priority.NEVER);
        root.setCenter(centerBox);

        Scene scene = new Scene(root, 1000, 700);
        stage.setTitle(messages.getString("app.title"));
        stage.setScene(scene);
        stage.setResizable(true);
        stage.show();
    }

    /**
     * @return effective host from the Host field, or "localhost" if blank
     */
    private String currentHost() {
        return (hostField != null && !hostField.getText().isBlank()) ? hostField.getText().trim() : "localhost";
    }

    /**
     * @return effective port parsed from the Port field, or 12345 on error
     */
    private int currentPort() {
        try { return (portField != null) ? Integer.parseInt(portField.getText().trim()) : 12345; }
        catch (Exception e) { return 12345; }
    }

    /**
     * Builds the small client connection strip with Host/Port/Connect/End controls.
     *
     * @return container node to be placed above the grid/messages
     */
    private HBox buildClientConnectStrip() {
        String serverHost = "localhost";
        hostField = new TextField(serverHost);
        hostField.setPrefColumnCount(14);
        int serverPort = 12345;
        portField = new TextField(String.valueOf(serverPort));
        portField.setPrefColumnCount(6);

        Button connectBtn = new Button("Connect");
        Button endBtn = new Button("End");

        connectBtn.setOnAction(e -> {
            // simple “is server reachable?” probe
            try (Socket s = new Socket(currentHost(), currentPort())) {
                connected = true;
                logMessage("Connected to " + currentHost() + ":" + currentPort());
            } catch (IOException ex) {
                connected = false;
                showError("Could not connect: " + ex.getMessage());
            }
        });
        endBtn.setOnAction(e -> {
            if (!connected) { logMessage("Already disconnected."); return; }
            // politely notify the server (optional)
            try (Socket s = new Socket(currentHost(), currentPort());
                 PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
                String u = (username != null && !username.isEmpty()) ? username : "anon";
                out.println(u + "#P0"); // “end” protocol
            } catch (IOException ignored) {}
            connected = false;
            logMessage("Disconnected.");
        });

        HBox strip = new HBox(8,
                new Label("Host:"), hostField,
                new Label("Port:"), portField,
                connectBtn, endBtn
        );
        strip.setAlignment(Pos.CENTER_LEFT);
        return strip;
    }

    /**
     * Builds the application menu bar, including File, Language, Theme, Grid, Mode, and Help.
     *
     * @return the menu bar node
     */
    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu(messages.getString("menu.file"));
        MenuItem save = new MenuItem(messages.getString("menu.save"));
        save.setOnAction(e -> { try { saveCircuitToFile(); } catch (Exception ex) { showError(ex.getMessage()); }});
        MenuItem load = new MenuItem(messages.getString("menu.load"));
        load.setOnAction(e -> { try { loadCircuitFromFile(); } catch (Exception ex) { showError(ex.getMessage()); }});
        fileMenu.getItems().addAll(save, load);

        Menu langMenu = new Menu(messages.getString("menu.language"));
        MenuItem en = new MenuItem(messages.getString("menu.english"));
        MenuItem fr = new MenuItem(messages.getString("menu.french"));
        en.setOnAction(e -> switchLanguage(Locale.ENGLISH));
        fr.setOnAction(e -> switchLanguage(Locale.FRENCH));
        langMenu.getItems().addAll(en, fr);

        Menu themeMenu = new Menu(messages.getString("menu.theme"));
        MenuItem colorChooser = new MenuItem(messages.getString("menu.theme.colorchooser"));
        colorChooser.setOnAction(e -> showColorChooser());
        MenuItem appTheme = new MenuItem(messages.getString("menu.theme.appTheme"));
        appTheme.setOnAction(e -> showAppThemeChooser());
        themeMenu.getItems().addAll(colorChooser, appTheme);

        Menu gridMenu = new Menu(messages.getString("menu.grid"));
        MenuItem resize = new MenuItem(messages.getString("menu.grid.resize"));
        resize.setOnAction(e -> showGridResizeDialog());
        gridMenu.getItems().add(resize);

        Menu modeMenu = new Menu(messages.getString("menu.mode"));
        MenuItem designMode = new MenuItem(messages.getString("menu.mode.design"));
        MenuItem execMode = new MenuItem(messages.getString("menu.mode.execution"));
        designMode.setOnAction(e -> switchMode(true));
        execMode.setOnAction(e -> switchMode(false));
        modeMenu.getItems().addAll(designMode, execMode);

        Menu helpMenu = new Menu(messages.getString("menu.help"));
        MenuItem about = new MenuItem(messages.getString("menu.about"));
        about.setOnAction(e -> showAboutDialog());
        helpMenu.getItems().add(about);

        menuBar.getMenus().addAll(fileMenu, langMenu, themeMenu, gridMenu, modeMenu, helpMenu);
        return menuBar;
    }

    /**
     * Shows a dialog allowing the user to select an application theme and applies it.
     */
    private void showAppThemeChooser() {
        List<String> choices = List.of("Light", "Dark", "Sky", "Grayscale");
        ChoiceDialog<String> dialog = new ChoiceDialog<>(choices.get(0), choices);
        dialog.setTitle(messages.getString("menu.theme.appTheme"));
        dialog.setHeaderText(messages.getString("menu.theme.appTheme"));
        dialog.setContentText("Choose a theme:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(this::applyAppTheme);
    }

    /**
     * Applies the selected theme by setting inline styles on main containers.
     *
     * @param theme one of "Light", "Dark", "Sky", or "Grayscale"
     */
    private void applyAppTheme(String theme) {
        String rootStyle;
        String panelStyle;
        String textStyle;

        switch (theme) {
            case "Dark":
                rootStyle = "-fx-background-color: #232323;";
                panelStyle = "-fx-background-color: #333333;";
                textStyle = "-fx-text-fill: #FFFFFF;";
                break;
            case "Sky":
                rootStyle = "-fx-background-color: linear-gradient(to bottom, #8fd3f4, #ffffff);";
                panelStyle = "-fx-background-color: #e3f0fc;";
                textStyle = "-fx-text-fill: #1b3448;";
                break;
            case "Grayscale":
                rootStyle = "-fx-background-color: #eeeeee;";
                panelStyle = "-fx-background-color: #cccccc;";
                textStyle = "-fx-text-fill: #222222;";
                break;
            default: // Light
                rootStyle = "-fx-background-color: #f6f6f6;";
                panelStyle = "-fx-background-color: #f0f0f0;";
                textStyle = "-fx-text-fill: #000000;";
                break;
        }

        if (root != null) root.setStyle(rootStyle);
        if (gridPanel != null && gridPanel.getGrid() != null) gridPanel.getGrid().setStyle(panelStyle);
        if (messageArea != null) messageArea.setStyle(panelStyle + textStyle);
        if (stepLabel != null) stepLabel.setStyle(textStyle);
        if (messagePanel != null) messagePanel.setStyle(panelStyle);
        if (gridScrollPane != null) gridScrollPane.setStyle(panelStyle);
    }

    /**
     * Displays an "About" dialog with an app icon and localized text.
     * <p>Icon path uses a file URL pointing to <code>../images/qcsicon.jpg</code>.</p>
     */
    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(messages.getString("alert.about.title"));
        alert.setHeaderText(messages.getString("alert.about.header"));
        ImageView imageView = new ImageView(new Image("file:../images/qcsicon.jpg"));
        imageView.setFitHeight(100); imageView.setPreserveRatio(true);
        alert.setGraphic(imageView);
        alert.setContentText(messages.getString("alert.about.content"));
        alert.showAndWait();
    }

    /**
     * Shows an error alert dialog with a single OK button.
     *
     * @param msg error message to display
     */
    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.showAndWait();
    }

    /**
     * Creates and returns the top banner node with the application image.
     *
     * @return a horizontal box containing the banner image
     */
    private HBox createBanner() {
        ImageView banner = new ImageView(new Image("file:../images/qcs.png"));
        banner.setPreserveRatio(true);
        banner.setFitHeight(90);
        HBox box = new HBox(banner);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(10));
        return box;
    }

    /**
     * Builds the left-hand gate selection panel (single- and multi-qubit gates, barrier).
     */
    private void setupLeft() {
        VBox left = new VBox(10);
        left.setPadding(new Insets(10));
        left.setPrefWidth(160);
        left.setStyle("-fx-background-color: #f0f0f0;");
        Label title = new Label(messages.getString("label.title"));
        Label singleLabel = new Label(messages.getString("label.single"));
        GridPane singleGrid = new GridPane(); singleGrid.setHgap(5); singleGrid.setVgap(5);
        String[] single = {"I", "X", "Y", "Z", "H", "S", "T", "U"};
        for (int i = 0; i < single.length; i++) {
            Button b = createGateButton(single[i]);
            b.setPrefWidth(60);
            singleGrid.add(b, i % 2, i / 2);
        }

        Label multiLabel = new Label(messages.getString("label.multi"));
        VBox multiBox = new VBox(5);
        for (String gate : new String[]{"CX", "SWAP", "CU", "CCX"}) {
            Button btn = createGateButton(gate);
            btn.setMaxWidth(Double.MAX_VALUE);
            multiBox.getChildren().add(btn);
        }
        Label opLabel = new Label(messages.getString("label.operations"));
        Button barrier = createGateButton("BARRIER");
        barrier.setMaxWidth(Double.MAX_VALUE);

        left.getChildren().addAll(title, singleLabel, singleGrid, multiLabel, multiBox, opLabel, barrier);
        root.setLeft(left);
    }

    /**
     * Builds the grid center area and attaches gate placement handlers to each cell.
     */
    private void setupCenter() {
        gridPanel = new QCSPanel(model.getRows(), model.getCols());
        gridScrollPane = new ScrollPane(gridPanel.getGrid());
        gridScrollPane.setFitToWidth(true);
        gridScrollPane.setFitToHeight(true);
        gridScrollPane.setPadding(new Insets(10));
        for (int r = 0; r < gridPanel.getRows(); r++) {
            for (int c = 0; c < gridPanel.getCols(); c++) {
                QCSGridButton btn = gridPanel.getButton(r, c);
                btn.setOnAction(e -> handleGridButtonClick(btn));
            }
        }
    }

    /**
     * Builds the controls below the grid (New, Step, Reset, messages area) and
     * adds Send/Load buttons for server interaction.
     */
    private void setupBottom() {
        VBox bottom = new VBox(5);
        bottom.setPadding(new Insets(10));
        bottom.setMinHeight(140);
        bottom.setPrefHeight(140);
        bottom.setMaxHeight(140);

        HBox controls = new HBox(10);
        controls.setAlignment(Pos.CENTER_LEFT);

        Button newCircuit = new Button(messages.getString("button.new"));
        newCircuit.setOnAction(e -> {
            model.clearGrid();
            gridPanel.resetGrid();
            currentStep = 0;
            updateStepLabel();
            messageArea.clear();
        });

        Button step = new Button(messages.getString("button.step"));
        step.setOnAction(e -> {
            if (!inDesignMode) stepSimulation();
            else logMessage(messages.getString("log.step.disabled"));
        });

        Button reset = new Button(messages.getString("button.reset"));
        reset.setOnAction(e -> {
            if (!inDesignMode) {
                currentStep = 0; gridPanel.clearHighlights(); updateStepLabel();
                logMessage(messages.getString("log.reset"));
            } else logMessage(messages.getString("log.reset.disabled"));
        });

        stepLabel = new Label();
        updateStepLabel();

        controls.getChildren().addAll(newCircuit, step, reset, stepLabel);

        messageArea = new TextArea();
        messageArea.setEditable(false); messageArea.setPrefHeight(70); messageArea.setWrapText(true);

        bottom.getChildren().addAll(controls, new Label(messages.getString("label.messages")), messageArea);
        messagePanel = bottom;

        Button sendCircuit = new Button("Send Circuit");
        sendCircuit.setOnAction(e -> sendCircuitToServer());

        Button loadCircuit = new Button("Load Circuit");
        loadCircuit.setOnAction(e -> loadCircuitFromServer());

        controls.getChildren().addAll(sendCircuit, loadCircuit);
    }

    /**
     * Updates the step label (current simulation column).
     */
    private void updateStepLabel() {
        if (stepLabel != null)
            stepLabel.setText(messages.getString("label.step") + " " + currentStep);
    }

    /**
     * Shows a dialog allowing the user to resize the grid (rows,cols).
     * Validates input and rebuilds the center view.
     */
    private void showGridResizeDialog() {
        TextInputDialog dialog = new TextInputDialog(model.getRows() + "," + model.getCols());
        dialog.setHeaderText(messages.getString("menu.grid.resize"));
        dialog.setContentText(messages.getString("label.grid.input"));
        dialog.showAndWait().ifPresent(input -> {
            try {
                String[] parts = input.split(",");
                int newRows = Integer.parseInt(parts[0].trim());
                int newCols = Integer.parseInt(parts[1].trim());
                model.resize(newRows, newCols);
                setupCenter();
                VBox centerBox = new VBox(5, gridScrollPane, messagePanel);
                centerBox.setPadding(new Insets(10));
                VBox.setVgrow(gridScrollPane, Priority.ALWAYS);
                VBox.setVgrow(messagePanel, Priority.NEVER);
                root.setCenter(centerBox);
                logMessage(messages.getString("log.grid.resized") + newRows + " x " + newCols);
            } catch (Exception e) {
                logMessage(messages.getString("log.grid.invalid"));
            }
        });
    }

    /**
     * Switches the application language and reloads the UI.
     *
     * @param locale new locale (e.g., {@link Locale#ENGLISH} or {@link Locale#FRENCH})
     */
    private void switchLanguage(Locale locale) {
        currentLocale = locale;
        start(mainStage);
    }

    /**
     * Creates a gate selection button with style and click behavior.
     *
     * @param label gate label text
     * @return configured JavaFX {@link Button}
     */
    private Button createGateButton(String label) {
        Button btn = new Button(label);
        btn.setPrefWidth(60);
        btn.setStyle("-fx-background-color: " + QCSModel.getGateColor(label));
        btn.setOnAction(e -> {
            selectedGate = label;
            logMessage(messages.getString("log.gate.selected") + label);
        });
        return btn;
    }

    /**
     * Opens a dialog to choose a gate, then a color picker to change its color mapping.
     * Updates all gate cell appearances afterward.
     */
    private void showColorChooser() {
        List<String> choices = new ArrayList<>(model.getGateColors().keySet());
        ChoiceDialog<String> gateDialog = new ChoiceDialog<>(choices.getFirst(), choices);
        gateDialog.setTitle(messages.getString("menu.theme.colorchooser"));
        gateDialog.setHeaderText(messages.getString("menu.theme.colorchooser"));
        gateDialog.setContentText(messages.getString("label.gate.select"));
        Optional<String> gateOpt = gateDialog.showAndWait();
        gateOpt.ifPresent(gate -> {
            ColorPicker colorPicker = new ColorPicker(Color.web(QCSModel.getGateColor(gate)));
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle(messages.getString("menu.theme.colorchooser"));
            alert.setHeaderText(messages.getString("label.gate.color.change"));
            alert.getDialogPane().setContent(colorPicker);
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                String hex = String.format("#%02X%02X%02X",
                        (int)(colorPicker.getValue().getRed() * 255),
                        (int)(colorPicker.getValue().getGreen() * 255),
                        (int)(colorPicker.getValue().getBlue() * 255));
                QCSModel.setGateColor(gate, hex);
                logMessage(messages.getString("log.gate.color.changed") + gate + ": " + hex);
                gridPanel.updateAllAppearances();
            }
        });
    }

    /**
     * Toggles between Design and Execution modes and logs the change.
     *
     * @param designMode true to enter Design mode; false for Execution mode
     */
    private void switchMode(boolean designMode) {
        inDesignMode = designMode;
        String mode = inDesignMode ? messages.getString("log.mode.design") : messages.getString("log.mode.exec");
        logMessage(messages.getString("log.mode.switch") + mode);
    }

    // --- login/session bridge ---
    private static String pendingUsername;   // set before the controller exists

    /**
     * Sets the logged-in user. If an instance exists, updates its user; otherwise caches it.
     *
     * @param u username to store
     */
    public static void setLoggedInUser(String u) {
        pendingUsername = u;
        if (instance != null) instance.username = u;
    }

    /**
     * @return the active instance username or the pending username if instance not yet built
     */
    public static String getLoggedInUser() {
        return (instance != null) ? instance.username : pendingUsername;
    }

    /**
     * Handles placement of a gate into the grid when a button is clicked.
     * Includes special handling for multi-qubit and barrier gates.
     *
     * @param btn grid cell button that was clicked
     */
    public void handleGridButtonClick(QCSGridButton btn) {
        if (!inDesignMode) return;
        String gate = getSelectedGate();
        int row = btn.getRow(), col = btn.getCol();
        try {
            if (MULTI_QUBIT_GATES.contains(gate)) {
                if (firstMultiQubitButton == null) {
                    if (btn.isFilled()) throw new QCSModelException(messages.getString("log.cell.filled"));
                    firstMultiQubitButton = btn; btn.setHighlighted(true);
                    logMessage(messages.getString("log.multiq.prompt"));
                } else {
                    if (btn == firstMultiQubitButton) return;
                    if (btn.getCol() != firstMultiQubitButton.getCol()) {
                        firstMultiQubitButton.setHighlighted(false); firstMultiQubitButton = null;
                        throw new QCSModelException(messages.getString("log.multiq.error"));
                    }
                    if (btn.isFilled()) {
                        firstMultiQubitButton.setHighlighted(false); firstMultiQubitButton = null;
                        throw new QCSModelException(messages.getString("log.cell.filled"));
                    }
                    int row1 = firstMultiQubitButton.getRow();
                    int col1 = firstMultiQubitButton.getCol();
                    firstMultiQubitButton.setGate(gate + "(1)");
                    btn.setGate(gate + "(2)");
                    firstMultiQubitButton.setHighlighted(false);
                    firstMultiQubitButton = null;
                    logMessage(MessageFormat.format(
                            messages.getString("log.gate.multiq.placed"), gate, row1, col1, row, col));
                }
            } else if (gate.equals("BARRIER")) {
                for (int r = 0; r < gridPanel.getRows(); r++) {
                    gridPanel.getButton(r, col).setGate("BARRIER");
                }
                logMessage(messages.getString("log.barrier.placed").replace("{0}", String.valueOf(col)));
            } else {
                if (!btn.isFilled()) {
                    btn.setGate(gate);
                    logMessage(MessageFormat.format(
                            messages.getString("log.gate.placed"), gate, row, col));
                } else {
                    throw new QCSModelException(messages.getString("log.cell.filled"));
                }
            }
        } catch (Exception e) {
            showError(e.getMessage());
        }
        gridPanel.updateAllAppearances();
    }

    /**
     * Steps the circuit simulation forward by one column and logs a tensor summary.
     * Highlights the active column in the grid.
     */
    private void stepSimulation() {
        if (currentStep >= gridPanel.getCols()) {
            logMessage(messages.getString("log.step.end"));
            return;
        }
        gridPanel.clearHighlights();
        StringBuilder tensor = new StringBuilder();
        tensor.append("Tensor Product: ");
        for (int r = 0; r < gridPanel.getRows(); r++) {
            gridPanel.getButton(r, currentStep).setHighlighted(true);
            String gate = gridPanel.getButton(r, currentStep).getGate();

            String label;
            if (gate.isEmpty()) {
                label = "_";
            } else if (gate.equals("I")) {
                label = "I";
            } else if (gate.startsWith("CX")) {
                int control = -1, target = -1;
                for (int rr = 0; rr < gridPanel.getRows(); rr++) {
                    String g = gridPanel.getButton(rr, currentStep).getGate();
                    if (g.startsWith("CX(1)")) control = rr;
                    if (g.startsWith("CX(2)")) target = rr;
                }
                if (control != -1 && target != -1 && (r == control || r == target)) {
                    label = "CX(" + control + "," + target + ")";
                } else {
                    label = "CX";
                }
            } else if (gate.startsWith("SWAP")) {
                int a = -1, b = -1;
                for (int rr = 0; rr < gridPanel.getRows(); rr++) {
                    String g = gridPanel.getButton(rr, currentStep).getGate();
                    if (g.startsWith("SWAP(1)")) a = rr;
                    if (g.startsWith("SWAP(2)")) b = rr;
                }
                if (a != -1 && b != -1 && (r == a || r == b)) {
                    label = "SWAP(" + a + "," + b + ")";
                } else {
                    label = "SWAP";
                }
            } else if (gate.startsWith("CU")) {
                int a = -1, b = -1;
                for (int rr = 0; rr < gridPanel.getRows(); rr++) {
                    String g = gridPanel.getButton(rr, currentStep).getGate();
                    if (g.startsWith("CU(1)")) a = rr;
                    if (g.startsWith("CU(2)")) b = rr;
                }
                if (a != -1 && b != -1 && (r == a || r == b)) {
                    label = "CU(" + a + "," + b + ")";
                } else {
                    label = "CU";
                }
            } else if (gate.startsWith("CCX")) {
                int a = -1, b = -1, cIdx = -1;
                for (int rr = 0; rr < gridPanel.getRows(); rr++) {
                    String g = gridPanel.getButton(rr, currentStep).getGate();
                    if (g.startsWith("CCX(1)")) a = rr;
                    if (g.startsWith("CCX(2)")) b = rr;
                    if (g.startsWith("CCX(3)")) cIdx = rr;
                }
                if (a != -1 && b != -1 && cIdx != -1 && (r == a || r == b || r == cIdx)) {
                    label = "CCX(" + a + "," + b + "," + cIdx + ")";
                } else {
                    label = "CCX";
                }
            } else if (gate.equals("BARRIER")) {
                label = "BARRIER";
            } else {
                label = gate;
            }

            tensor.append("[");
            tensor.append(label);
            if (!label.equals("_")) {
                tensor.append("(q").append(r).append(")");
            }
            tensor.append("]");
        }
        tensor.append("⟩");
        logMessage(tensor.toString());
        currentStep++;
        updateStepLabel();
    }

    /**
     * Saves the circuit grid and the current step to a <code>.qcs</code> file.
     *
     * @throws Exception if the save routine fails for any reason
     */
    private void saveCircuitToFile() throws Exception {
        if (gridPanel == null) {
            showError("Nothing to save (grid not ready).");
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(messages.getString("menu.save"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("QCS Files", "*.qcs"));
        File file = fileChooser.showSaveDialog(mainStage);
        if (file != null) {
            String serialized = gridPanel.serializeGrid();
            if (serialized == null) serialized = "";
            model.saveToFile(file, serialized, currentStep);
            logMessage(messages.getString("log.save.success"));
        }
    }

    /**
     * Highlights the current step column if within bounds; otherwise clears highlights.
     */
    private void highlightCurrentStepColumn() {
        gridPanel.clearHighlights();
        if (currentStep < gridPanel.getCols()) {
            for (int r = 0; r < gridPanel.getRows(); r++) {
                gridPanel.getButton(r, currentStep).setHighlighted(true);
            }
        }
    }

    /**
     * Loads a circuit and step from a <code>.qcs</code> file and updates the grid/UI.
     *
     * @throws Exception if the file chooser or I/O produces an error
     */
    private void loadCircuitFromFile() throws Exception {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(messages.getString("menu.load"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("QCS Files", "*.qcs"));
        File file = fileChooser.showOpenDialog(mainStage);
        if (file != null) {
            String[] loaded = model.loadFromFile(file);
            if (loaded == null || loaded.length < 2) {
                showError("Invalid circuit file.");
                return;
            }
            int loadedStep;
            try { loadedStep = Integer.parseInt(loaded[0]); }
            catch (NumberFormatException nfe) { loadedStep = 0; }
            String gridData = loaded[1];
            if (gridData == null || gridData.isEmpty()) {
                showError("Circuit file is empty.");
                return;
            }

            String[] rowParts = gridData.split(";");
            if (rowParts.length == 0 || rowParts[0].isEmpty()) {
                showError("Circuit data malformed.");
                return;
            }
            int nRows = rowParts.length;
            int nCols = rowParts[0].split(",").length;

            gridPanel = new QCSPanel(nRows, nCols);
            for (int r = 0; r < nRows; r++) {
                for (int c = 0; c < nCols; c++) {
                    QCSGridButton btn = gridPanel.getButton(r, c);
                    btn.setOnAction(e -> handleGridButtonClick(btn));
                }
            }
            gridPanel.deserializeGrid(gridData);
            if (gridScrollPane != null) gridScrollPane.setContent(gridPanel.getGrid());

            currentStep = Math.max(0, Math.min(loadedStep, nCols));
            updateStepLabel();
            highlightCurrentStepColumn();
            gridPanel.updateAllAppearances();

            logMessage(messages.getString("log.load.success"));
        }
    }

    /**
     * Maps a single grid gate placement to an equivalent Qiskit API call string (if applicable).
     *
     * @param gate gate label stored in the cell
     * @param row  qubit index (row)
     * @param col  time/step index (column)
     * @return a Qiskit call string (e.g., <code>qc.h(q0)</code>) or empty string if none
     */
    private String gridGateToQiskit(String gate, int row, int col) {
        if (gate.startsWith("H")) return "qc.h(q" + row + ")";
        if (gate.startsWith("X")) return "qc.x(q" + row + ")";
        if (gate.startsWith("Y")) return "qc.y(q" + row + ")";
        if (gate.startsWith("Z")) return "qc.z(q" + row + ")";
        if (gate.startsWith("S")) return "qc.s(q" + row + ")";
        if (gate.startsWith("T")) return "qc.t(q" + row + ")";
        if (gate.startsWith("U")) return "qc.u(q" + row + ")";
        if (gate.startsWith("CX(1)")) return "";
        if (gate.startsWith("CX(2)")) {
            int control = -1;
            for (int r = 0; r < gridPanel.getRows(); r++) {
                if (gridPanel.getButton(r, col).getGate().startsWith("CX(1)")) {
                    control = r; break;
                }
            }
            if (control != -1) return "qc.cx(q" + control + ",q" + row + ")";
        }
        if (gate.startsWith("SWAP(1)")) return "";
        if (gate.startsWith("SWAP(2)")) {
            int other = -1;
            for (int r = 0; r < gridPanel.getRows(); r++) {
                if (gridPanel.getButton(r, col).getGate().startsWith("SWAP(1)")) {
                    other = r; break;
                }
            }
            if (other != -1) return "qc.swap(q" + other + ",q" + row + ")";
        }
        if (gate.startsWith("BARRIER")) return "qc.barrier(q" + row + ")";
        return "";
    }

    /**
     * Emits Qiskit code lines for all gates in a given column (step).
     *
     * @param col step index/column
     * @return multiline string with Qiskit calls, or a "No operation" message
     */
    private String generateQiskitCodeForStep(int col) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < gridPanel.getRows(); r++) {
            String gate = gridPanel.getButton(r, col).getGate();
            if (!gate.isEmpty() && !"I".equals(gate)) {
                String code = gridGateToQiskit(gate, r, col);
                if (!code.isEmpty()) sb.append(code).append("\n");
            }
        }
        if (sb.isEmpty()) sb.append("No operation (all identities)");
        return sb.toString().trim();
    }

    /**
     * Appends a message to the main message area.
     *
     * @param msg text to append (newline will be added)
     */
    public static void logMessage(String msg) {
        if (instance != null && instance.messageArea != null) {
            instance.messageArea.appendText(msg + "\n");
        }
    }

    // --- Circuit Networking Features (now async & with timeouts) ---

    /**
     * Sends the current circuit to the server (async).
     * <p>Validates username and grid, then posts a line using the P2 protocol.</p>
     * <p>Uses timeouts to avoid UI freeze; updates UI via {@link Platform#runLater(Runnable)}.</p>
     */
    private void sendCircuitToServer() {
        if (username == null || username.isEmpty()) {
            showError("You must be logged in to send circuit to server.");
            return;
        }
        if (gridPanel == null) {
            showError("No circuit to send.");
            return;
        }

        final String host = "localhost";   // keep your values if you already have host/port fields
        final int port = 12345;

        new Thread(() -> {
            // ensure we send a single line (some servers block on '\n')
            String gridString = gridPanel.serializeGrid();
            if (gridString == null) gridString = "";
            gridString = gridString.replace("\r", " ").replace("\n", " ");

            try (Socket socket = new Socket()) {
                socket.connect(new java.net.InetSocketAddress(host, port), 4000);
                socket.setSoTimeout(8000);

                try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                    out.println(username + "#P2#" + gridString);  // save
                    String reply = in.readLine();

                    if (reply != null && reply.contains("#P2#OK")) {
                        javafx.application.Platform.runLater(() -> logMessage("Circuit saved to server!"));
                    } else if (reply != null && reply.contains("#FAIL")) {
                        javafx.application.Platform.runLater(() -> showError("Server reported FAIL saving circuit."));
                    } else {
                        // fallback for servers that don't echo OK
                        out.println(username + "#P1#" + gridString);
                        javafx.application.Platform.runLater(() ->
                                logMessage("Circuit sent using fallback (P1). Server may not reply by design."));
                    }
                }
            } catch (java.net.SocketTimeoutException te) {
                javafx.application.Platform.runLater(() ->
                        showError("Error connecting to server: Read timed out (no reply)."));
            } catch (IOException ex) {
                javafx.application.Platform.runLater(() ->
                        showError("Error connecting to server: " + ex.getMessage()));
            }
        }, "qcs-send").start();
    }

    /**
     * Loads a circuit for the current user from the server (async).
     * <p>Requires an active "connected" state and username.</p>
     * <p>Rebuilds the grid to the received size and updates the UI on the FX thread.</p>
     */
    private void loadCircuitFromServer() {
        if (!connected) { showError("Not connected. Click Connect first."); return; }
        if (username == null || username.isEmpty()) { showError("You must be logged in to load circuit from server."); return; }

        new Thread(() -> {
            try (Socket socket = new Socket()) {
                // connect timeout + read timeout to avoid freezing
                socket.connect(new java.net.InetSocketAddress(currentHost(), currentPort()), 3000);
                socket.setSoTimeout(4000);

                try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                    out.println(username + "#P3");
                    String reply = in.readLine();

                    if (reply != null && reply.startsWith(username + "#P3#")) {
                        String circuit = reply.substring((username + "#P3#").length());

                        // sanity checks + UI update on FX thread
                        if (circuit.isBlank()) {
                            javafx.application.Platform.runLater(() -> showError("No circuit saved for this user!"));
                            return;
                        }
                        String[] rowParts = circuit.split(";");
                        if (rowParts.length == 0 || rowParts[0].isEmpty()) {
                            javafx.application.Platform.runLater(() -> showError("Corrupted circuit on server."));
                            return;
                        }
                        int nRows = rowParts.length;
                        int nCols = rowParts[0].split(",").length;

                        javafx.application.Platform.runLater(() -> {
                            // rebuild grid to the received size
                            gridPanel = new QCSPanel(nRows, nCols);
                            for (int r = 0; r < nRows; r++) {
                                for (int c = 0; c < nCols; c++) {
                                    QCSGridButton btn = gridPanel.getButton(r, c);
                                    btn.setOnAction(e -> handleGridButtonClick(btn));
                                }
                            }
                            gridPanel.deserializeGrid(circuit);
                            gridScrollPane.setContent(gridPanel.getGrid());
                            gridPanel.updateAllAppearances();
                            logMessage("Circuit loaded from server.");
                        });
                    } else {
                        javafx.application.Platform.runLater(() -> showError("No circuit saved for this user!"));
                    }
                }
            } catch (java.net.SocketTimeoutException te) {
                javafx.application.Platform.runLater(() ->
                        showError("Error connecting to server: Read timed out (no reply)."));
            } catch (IOException ex) {
                javafx.application.Platform.runLater(() ->
                        showError("Error connecting to server: " + ex.getMessage()));
            }
        }, "qcs-load").start();
    }
}
