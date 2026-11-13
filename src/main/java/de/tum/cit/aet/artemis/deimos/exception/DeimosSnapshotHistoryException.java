package de.tum.cit.aet.artemis.deimos.exception;

import java.io.Serial;

/**
 * Thrown when the observed submission snapshot history of a participation could not be reconstructed, for example
 * because the repository could not be opened, a commit could not be resolved, or the diff could not be built.
 * <p>
 * Deliberately distinct from the "there is nothing to analyse" case, which is represented by an empty history. Without
 * that distinction a repository failure is indistinguishable from a participation that simply never changed anything,
 * and a broken run looks like a clean one.
 */
public class DeimosSnapshotHistoryException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DeimosSnapshotHistoryException(String message, Throwable cause) {
        super(message, cause);
    }

    public DeimosSnapshotHistoryException(String message) {
        super(message);
    }
}
