package de.tum.in.www1.artemis.web.rest.errors;

import java.io.Serial;

@Deprecated // Moved to user management microservice. To be removed
public class EmailAlreadyUsedException extends BadRequestAlertException {

    @Serial
    private static final long serialVersionUID = 1L;

    public EmailAlreadyUsedException() {
        super(ErrorConstants.EMAIL_ALREADY_USED_TYPE, "Email is already in use!", "userManagement", "emailexists", false);
    }
}
