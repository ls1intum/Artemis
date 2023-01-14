package de.tum.in.www1.artemis.security.localVC;

import de.tum.in.www1.artemis.exception.LocalVCException;

public class LocalVCBadRequestException extends LocalVCException {

    public LocalVCBadRequestException() {
        super();
    }

    public LocalVCBadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
