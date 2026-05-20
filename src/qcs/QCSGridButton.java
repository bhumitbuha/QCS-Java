package qcs;

import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

/**
 * QCSGridButton represents a single cell in the quantum circuit grid.
 * <p>
 * Each button knows its row/column position and can display a quantum gate label,
 * highlight state, and fill status. Handles its own appearance based on state.
 * </p>
 *
 * @author Bhumit
 * @version 2.0 (A4)
 */
public class QCSGridButton extends Button {

    private final int row;
    private final int col;
    private String gate = "";
    private boolean filled = false;
    private boolean highlighted = false;

    /**
     * Constructs a grid button for the given row and column.
     *
     * @param row Row index of this button in the grid.
     * @param col Column index of this button in the grid.
     */
    public QCSGridButton(int row, int col) {
        this.row = row;
        this.col = col;
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        GridPane.setHgrow(this, Priority.ALWAYS);
        GridPane.setVgrow(this, Priority.ALWAYS);
        updateAppearance();
    }

    /**
     * Sets the label (quantum gate) for this button and updates appearance.
     *
     * @param gateLabel The label or gate name to set (e.g., "H", "CX(1)").
     */
    public void setGate(String gateLabel) {
        this.gate = gateLabel;
        this.filled = !gateLabel.isEmpty();
        updateAppearance();
    }

    /**
     * Resets the button to its default (empty) state.
     */
    public void reset() {
        this.gate = "";
        this.filled = false;
        this.highlighted = false;
        updateAppearance();
    }

    /**
     * Sets the highlighted status of this button and updates appearance.
     *
     * @param highlight True to highlight, false otherwise.
     */
    public void setHighlighted(boolean highlight) {
        this.highlighted = highlight;
        updateAppearance();
    }

    /**
     * Returns true if this cell is filled with a gate.
     *
     * @return true if gate is present, false otherwise.
     */
    public boolean isFilled() {
        return filled;
    }

    /**
     * Returns the row index of this button.
     *
     * @return row index
     */
    public int getRow() { return row; }

    /**
     * Returns the column index of this button.
     *
     * @return column index
     */
    public int getCol() { return col; }

    /**
     * Gets the gate label currently set for this cell.
     *
     * @return gate label or "" if empty.
     */
    public String getGate() { return gate; }

    /**
     * Updates the visual appearance of the button based on its state.
     * <ul>
     *   <li>Highlighted: yellow background, black border.</li>
     *   <li>Filled: uses gate color from QCSController, gray border.</li>
     *   <li>Empty: light background, light border.</li>
     * </ul>
     */
    public void updateAppearance() {
        if (highlighted) {
            setStyle("-fx-background-color: yellow; -fx-border-color: black; -fx-border-width: 2px;");
            setText(gate);
        } else if (!gate.isEmpty()) {
            setText(gate);
            setStyle("-fx-background-color: " + QCSController.getGateColor(gate) + "; -fx-border-color: gray;");
        } else {
            setText("");
            setStyle("-fx-background-color: #f5f5f5; -fx-border-color: lightgray;");
        }
    }
}
