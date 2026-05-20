package qcs;

import java.util.*;
import java.io.*;

/**
 * QCSModel - Holds the state and configuration of the quantum circuit grid.
 * Manages gate data, grid resizing, colors, and serialization for saving/loading.
 * <p>
 * All modifications and queries of the grid state are performed through this class.
 * </p>
 *
 * @author Bhumit
 * @version A4
 */
public class QCSModel {
    /** Number of rows in the circuit grid (qubits) */
    private int rows;
    /** Number of columns in the circuit grid (steps/gates) */
    private int cols;
    /** 2D grid representing gate labels at each cell */
    private String[][] grid;

    /** Default color mapping for all supported quantum gates */
    public static final Map<String, String> DEFAULT_GATE_COLORS = new HashMap<>();
    static {
        DEFAULT_GATE_COLORS.put("I", "#66FFFF");
        DEFAULT_GATE_COLORS.put("X", "#FF9999");
        DEFAULT_GATE_COLORS.put("Y", "#CCFF99");
        DEFAULT_GATE_COLORS.put("Z", "#9999FF");
        DEFAULT_GATE_COLORS.put("H", "#66FFCC");
        DEFAULT_GATE_COLORS.put("S", "#FF66FF");
        DEFAULT_GATE_COLORS.put("T", "#FFCC66");
        DEFAULT_GATE_COLORS.put("U", "#FF9900");
        DEFAULT_GATE_COLORS.put("CX", "#FFCCCC");
        DEFAULT_GATE_COLORS.put("SWAP", "#CCFFCC");
        DEFAULT_GATE_COLORS.put("CU", "#FFCCCB");
        DEFAULT_GATE_COLORS.put("CCX", "#D8BFD8");
        DEFAULT_GATE_COLORS.put("BARRIER", "#C0C0C0");
    }

    /** Customizable color map for gates (initializes to defaults) */
    private static Map<String, String> gateColors = new HashMap<>(DEFAULT_GATE_COLORS);

    /**
     * Gets the display color for a given gate label.
     *
     * @param gate Gate label (e.g. "X", "CX", etc.)
     * @return The color string for JavaFX style (e.g., "#FF9999")
     */
    public static String getGateColor(String gate) {
        return gateColors.getOrDefault(gate, "#dddddd");
    }

    /**
     * Sets or overrides the color for a particular gate.
     *
     * @param gate  Gate label (e.g., "H", "CX")
     * @param color Hex color string (e.g., "#66FFCC")
     */
    public static void setGateColor(String gate, String color) {
        gateColors.put(gate, color);
    }

    /**
     * Constructs a default model with 3 rows and 5 columns.
     */
    public QCSModel() {
        this.rows = 3;
        this.cols = 5;
        clearGrid();
    }

    /**
     * @return Number of rows (qubits)
     */
    public int getRows() {
        return rows;
    }

    /**
     * @return Number of columns (steps)
     */
    public int getCols() {
        return cols;
    }

    /**
     * Resizes the grid to the given size and clears all gates.
     *
     * @param r Number of rows
     * @param c Number of columns
     */
    public void resize(int r, int c) {
        this.rows = r;
        this.cols = c;
        clearGrid();
    }

    /**
     * Clears the circuit grid, setting all gates to empty.
     */
    public void clearGrid() {
        grid = new String[rows][cols];
        for (String[] row : grid)
            Arrays.fill(row, "");
    }

    /**
     * Gets the label of the gate at a specified position.
     *
     * @param row Row index (0-based)
     * @param col Column index (0-based)
     * @return Gate label, or "" if empty
     */
    public String getGate(int row, int col) {
        return grid[row][col] == null ? "" : grid[row][col];
    }

    /**
     * Sets the label of the gate at a specified position.
     *
     * @param row   Row index (0-based)
     * @param col   Column index (0-based)
     * @param label Gate label to set ("" to clear)
     */
    public void setGate(int row, int col, String label) {
        grid[row][col] = label;
    }

    /**
     * Removes the gate at the specified position (sets to empty).
     *
     * @param row Row index (0-based)
     * @param col Column index (0-based)
     */
    public void removeGate(int row, int col) {
        grid[row][col] = "";
    }

    /**
     * Saves the current circuit grid and step number to a file.
     *
     * @param file           The file to save to
     * @param serializedGrid The grid as a single line (from serializeGrid())
     * @param currentStep    The current simulation step (int)
     * @throws IOException If an I/O error occurs
     */
    public void saveToFile(File file, String serializedGrid, int currentStep) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("step=" + currentStep); // First line: step
            writer.newLine();
            writer.write(serializedGrid); // Second line: grid (single line!)
            writer.newLine();
        }
    }

    /**
     * Loads the circuit from a file and returns an array: [step, grid].
     *
     * @param file The file to load from
     * @return String[]: [0]=step, [1]=grid data
     * @throws IOException If an I/O error occurs
     */
    public String[] loadFromFile(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String stepLine = reader.readLine();
            String gridLine = reader.readLine();
            if (stepLine == null || gridLine == null) {
                throw new IOException("Invalid QCS file: missing data.");
            }
            int step = 0;
            if (stepLine.startsWith("step=")) {
                step = Integer.parseInt(stepLine.substring(5).trim());
            }
            return new String[] { String.valueOf(step), gridLine.trim() };
        }
    }

    /**
     * Gets the mapping of gate labels to their display colors.
     *
     * @return Map from gate label to color string
     */
    public Map<String, String> getGateColors() {
        return gateColors;
    }
}
