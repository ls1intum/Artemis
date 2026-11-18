package de.tum.cit.aet.artemis.localvc.exception;

/**
 * Exception thrown when the user is not authenticated or authorized to fetch or push to a local VC repository.
 * Corresponds to HTTP status code 401.
 */
public class LocalVCAuthException extends LocalVCOperationException {

    /**
     * Whether this rejection is part of the normal git authentication handshake rather than a genuine problem.
     * <p>
     * A git client first sends a request without credentials and waits for the 401 challenge, and depending on the
     * client and the remote URL the follow-up request may still carry an empty password. Both are expected, happen on
     * every single clone, and must therefore not be reported as a warning. Everything else is a real rejection that has
     * to stay visible, because the client only sees a bare 401.
     */
    private final boolean expectedDuringHandshake;

    public LocalVCAuthException() {
        this.expectedDuringHandshake = false;
    }

    public LocalVCAuthException(Throwable cause) {
        super(cause);
        this.expectedDuringHandshake = false;
    }

    public LocalVCAuthException(String message) {
        this(message, false);
    }

    public LocalVCAuthException(String message, boolean expectedDuringHandshake) {
        super(message);
        this.expectedDuringHandshake = expectedDuringHandshake;
    }

    public boolean isExpectedDuringHandshake() {
        return expectedDuringHandshake;
    }
}
