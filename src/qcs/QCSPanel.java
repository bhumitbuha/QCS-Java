package qcs;

import javafx.geometry.Insets;
import javafx.scene.layout.GridPane;

/**
 * QCSPanel manages the 2D quantum circuit grid, which is made up of QCSGridButton objects.
 * Handles the creation, resizing, serialization, deserialization, and appearance updates for the circuit grid.
 * Acts as the "panel" where the quantum gates are visually placed.
 *
 * @author Bhumit
 * @version A4
 */
public class QCSPanel {
    /** The JavaFX grid container holding all QCSGridButtons. */
    private GridPane grid;
    /** Number of rows (qubits) in the grid. */
    private int rows;
    /** Number of columns (steps/time slots) in the grid. */
    private int cols;
    /** 2D array of QCSGridButton, representing the circuit. */
    private QCSGridButton[][] buttons;

    /**
     * Constructs a new QCSPanel with the specified number of rows and columns.
     * @param rows Number of qubit rows.
     * @param cols Number of circuit steps (columns).
     */
    public QCSPanel(int rows, int cols) {
        buildGrid(rows, cols);
    }

    /** @return The JavaFX GridPane containing all QCSGridButtons. */
    public GridPane getGrid() { return grid; }

    /** @return The number of rows (qubits). */
    public int getRows() { return rows; }

    /** @return The number of columns (circuit steps). */
    public int getCols() { return cols; }

    /**
     * Returns the button at the specified row and column, or null if out of bounds.
     * @param row Row index (0-based).
     * @param col Column index (0-based).
     * @return The QCSGridButton at (row, col), or null if invalid indices.
     */
    public QCSGridButton getButton(int row, int col) {
        if (row >= 0 && row < rows && col >= 0 && col < cols) {
            return buttons[row][col];
        }
        return null;
    }

    /**
     * Builds or rebuilds the grid with the given dimensions.
     * Existing grid/buttons are replaced.
     * @param newRows Number of rows.
     * @param newCols Number of columns.
     */
    public void buildGrid(int newRows, int newCols) {
        this.rows = newRows;
        this.cols = newCols;

        grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(5);
        grid.setVgap(5);

        buttons = new QCSGridButton[rows][cols];
        grid.getChildren().clear();

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                QCSGridButton btn = new QCSGridButton(r, c);
                btn.setOnAction(e -> QCSController.getInstance().handleGridButtonClick(btn));
                buttons[r][c] = btn;
                grid.add(btn, c, r);
            }
        }
    }

    /**
     * Resizes the grid, rebuilding it with the new row and column counts.
     * @param newRows New number of rows.
     * @param newCols New number of columns.
     */
    public void resizeGrid(int newRows, int newCols) {
        buildGrid(newRows, newCols);
    }

    /**
     * Resets all grid buttons to their default (empty) state.
     */
    public void resetGrid() {
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                buttons[r][c].reset();
    }

    /**
     * Serializes the grid to a single String, row-by-row, with rows separated by ';' and columns by ','.
     * Empty cells are encoded as '_'.
     * @return String encoding of the entire grid's state.
     */
    public String serializeGrid() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < rows; r++) {
            if (r > 0) sb.append(";");
            for (int c = 0; c < cols; c++) {
                if (c > 0) sb.append(",");
                String g = buttons[r][c].getGate();
                sb.append(g.isEmpty() ? "_" : g);
            }
        }
        return sb.toString();
    }

    /**
     * Deserializes a grid state from a String (produced by serializeGrid) and rebuilds grid accordingly.
     * @param data The serialized grid data.
     */
    public void deserializeGrid(String data) {
        if (data == null || data.trim().isEmpty()) return; // Safety
        String[] rowParts = data.split(";");
        int nRows = rowParts.length;
        int nCols = rowParts[0].split(",").length;
        resizeGrid(nRows, nCols);  // THIS REBUILDS THE BUTTONS ARRAY
        for (int r = 0; r < nRows; r++) {
            String[] colParts = rowParts[r].split(",");
            for (int c = 0; c < nCols; c++) {
                buttons[r][c].setGate(colParts[c].equals("_") ? "" : colParts[c]);
            }
        }
        updateAllAppearances();
    }

    /**
     * Calls updateAppearance() on all grid buttons, refreshing their color/label.
     */
    public void updateAllAppearances() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                buttons[r][c].updateAppearance();
            }
        }
    }

    /**
     * Removes all highlights from all grid buttons (used for simulation step display).
     */
    public void clearHighlights() {
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                buttons[r][c].setHighlighted(false);
    }
}
