package qcs;

/**
 * Custom exception for Quantum Circuit Simulator (QCS) model errors.
 * Used for signaling invalid actions or state within the QCSModel or controller.
 *
 * @author Bhumit
 * @version A4
 */
public class QCSModelException extends Exception {
    /**
     * Constructs a new QCSModelException with the specified detail message.
     *
     * @param message The detail message describing the error.
     */
    public QCSModelException(String message) {
        super(message);
    }
}
