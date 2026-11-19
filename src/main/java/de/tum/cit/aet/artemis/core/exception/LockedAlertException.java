package de.tum.cit.aet.artemis.core.exception;

import org.springframework.http.HttpStatus;

/**
 * Unchecked exception for a resource that another user holds at the moment (i.e. 423 Locked), for example a submission another tutor is assessing.
 */
public class LockedAlertException extends HttpStatusException {

    public LockedAlertException(String defaultMessage, String entityName, String errorKey) {
        super(ErrorConstants.DEFAULT_TYPE, defaultMessage, HttpStatus.LOCKED, entityName, errorKey, getAlertParameters(entityName, errorKey, false));
    }
}
