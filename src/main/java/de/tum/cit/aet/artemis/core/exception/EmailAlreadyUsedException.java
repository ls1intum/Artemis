package de.tum.cit.aet.artemis.core.exception;

import java.io.Serial;

public class EmailAlreadyUsedException extends BadRequestAlertException {

    @Serial
    private static final long serialVersionUID = 1L;

    public EmailAlreadyUsedException() {
        super(ErrorConstants.EMAIL_ALREADY_USED_TYPE, "Email is already in use!", "userManagement", "emailExists", false);
    }
}
