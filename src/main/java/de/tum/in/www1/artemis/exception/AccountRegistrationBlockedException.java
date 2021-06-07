package de.tum.in.www1.artemis.exception;

import java.io.Serial;

import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.ErrorConstants;

public class AccountRegistrationBlockedException extends BadRequestAlertException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AccountRegistrationBlockedException(String email) {
        super(ErrorConstants.ACCOUNT_REGISTRATION_BLOCKED, "Account registration has been blocked for email: " + email, "userManagement", "accountRegistrationBlocked", false);
    }
}
