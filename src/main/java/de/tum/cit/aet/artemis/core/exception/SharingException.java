package de.tum.in.www1.artemis.exception;

import org.springframework.context.annotation.Profile;

@Profile("sharing")
public class SharingException extends Exception {

    public SharingException() {
        super();
    }

    public SharingException(String message) {
        super(message);
    }

    public SharingException(String message, Throwable cause) {
        super(message, cause);
    }

    public SharingException(Throwable cause) {
        super(cause);
    }

    protected SharingException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
