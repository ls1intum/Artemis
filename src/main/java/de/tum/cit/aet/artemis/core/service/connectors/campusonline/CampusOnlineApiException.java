package de.tum.cit.aet.artemis.core.service.connectors.campusonline;

public class CampusOnlineApiException extends RuntimeException {

    public CampusOnlineApiException(String message) {
        super(message);
    }

    public CampusOnlineApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
