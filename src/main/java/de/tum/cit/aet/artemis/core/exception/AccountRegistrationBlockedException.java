package de.tum.cit.aet.artemis.core.exception;

import java.io.Serial;

public class AccountRegistrationBlockedException extends BadRequestAlertException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AccountRegistrationBlockedException(String email) {
        super(ErrorConstants.ACCOUNT_REGISTRATION_BLOCKED, "Account registration has been blocked for email: " + email, "userManagement", "accountRegistrationBlocked", false);
    }
}
