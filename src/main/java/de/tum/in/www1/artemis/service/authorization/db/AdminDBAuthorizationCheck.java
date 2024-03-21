package de.tum.in.www1.artemis.service.authorization.db;

import javax.annotation.CheckReturnValue;
import javax.validation.constraints.NotNull;

import de.tum.in.www1.artemis.service.authorization.AuthorizationCheck;

public interface AdminDBAuthorizationCheck extends AuthorizationCheck {

    /**
     * Checks if the passed user is an admin user
     *
     * @param login the login of the user that needs to be checked
     * @return true, if user is admin, otherwise false
     */
    @CheckReturnValue
    default boolean isAdmin(@NotNull String login) {
        return getAuthorizationRepository().isAdmin(login);
    }
}
