package de.tum.in.www1.artemis.web.rest.errors;

import java.io.Serial;

@Deprecated // Moved to user management microservice. To be removed
public class LoginAlreadyUsedException extends BadRequestAlertException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LoginAlreadyUsedException() {
        super(ErrorConstants.LOGIN_ALREADY_USED_TYPE, "Login name already used!", "userManagement", "userexists", false);
    }
}
