package de.tum.cit.aet.artemis.globalsearch.exception;

/**
 * An embedding request for global search failed or returned something unusable. An index write retries through the
 * outbox; a search falls back to keyword search.
 */
public class EmbeddingFailedException extends WeaviateException {

    public EmbeddingFailedException(String message) {
        super(message);
    }

    public EmbeddingFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
