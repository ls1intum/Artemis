package de.tum.cit.aet.artemis.atlas.service.atlasml;

/**
 * Exception thrown when there are errors communicating with the AtlasML microservice.
 */
public class AtlasMLServiceException extends RuntimeException {

    public AtlasMLServiceException(String message) {
        super(message);
    }

    public AtlasMLServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public AtlasMLServiceException(Throwable cause) {
        super(cause);
    }
}
