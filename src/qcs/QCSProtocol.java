package qcs;

/**
 * Utility class containing constants and helper methods
 * for the QCS (Quantum Circuit Simulator) client-server protocol.
 * <p>
 * The protocol is based on sending a single text line with fields
 * separated by {@link #SEP}, where the first field is the username,
 * the second is a protocol code (P0, P1, etc.), and optionally the
 * third contains additional data such as a password or circuit data.
 * </p>
 *
 * <h3>Protocol Codes</h3>
 * <ul>
 *   <li>{@link #P0_LOGIN}  - Login request: {@code username#P0#password}</li>
 *   <li>{@link #P1_SIGNUP} - Signup request: {@code username#P1#password}</li>
 *   <li>{@link #P2_SAVE}   - Save circuit: {@code username#P2#<circuit>}</li>
 *   <li>{@link #P3_LOAD}   - Load circuit: {@code username#P3}</li>
 *   <li>{@link #P9_END}    - Optional end/terminate message</li>
 * </ul>
 */
public final class QCSProtocol {

    /**
     * Private constructor to prevent instantiation.
     */
    private QCSProtocol() {}

    /** Field separator for all protocol messages. */
    public static final String SEP = "#";

    /** Login request: {@code username#P0#password} */
    public static final String P0_LOGIN   = "P0";

    /** Signup request: {@code username#P1#password} */
    public static final String P1_SIGNUP  = "P1";

    /** Save circuit: {@code username#P2#<circuit>} */
    public static final String P2_SAVE    = "P2";

    /** Load circuit: {@code username#P3} */
    public static final String P3_LOAD    = "P3";

    /** Optional client termination message: {@code username#P9} */
    public static final String P9_END     = "P9";

    /**
     * Builds a protocol-compliant message.
     *
     * @param user  the username
     * @param proto the protocol code (e.g., {@link #P0_LOGIN})
     * @param data  optional payload (e.g., password or circuit string), may be {@code null}
     * @return the complete protocol message
     */
    public static String msg(String user, String proto, String data) {
        return user + SEP + proto + (data == null ? "" : SEP + data);
    }
}
