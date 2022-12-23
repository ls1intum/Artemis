package de.tum.in.www1.artemis.security.localVC;

import de.tum.in.www1.artemis.exception.LocalVCException;

public class LocalVCNotFoundException extends LocalVCException {

    public LocalVCNotFoundException(String message) {
        super(message);
    }

    public LocalVCNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
