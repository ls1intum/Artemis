package de.tum.in.www1.artemis.service.authorization;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.authorization.db.AdminDBAuthorizationCheck;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

public interface AdminAuthorizationCheck extends AdminDBAuthorizationCheck {

    /**
     * NOTE: this method should only be used in a REST Call context, when the SecurityContext is correctly setup.
     * Preferably use the method isAdmin(user) below
     * <p>
     * Checks if the currently logged-in user is an admin user
     *
     * @return true, if user is admin, otherwise false
     */
    @CheckReturnValue
    default boolean isAdmin() {
        return SecurityUtils.isCurrentUserInRole(Role.ADMIN.getAuthority());
    }

    /**
     * Checks if the passed user is an admin user
     *
     * @param user the user with authorities. If the user is null, the currently logged-in user will be used.
     * @return true, if user is admin, otherwise false
     */
    @CheckReturnValue
    default boolean isAdmin(@Nullable User user) {
        if (user == null) {
            return isAdmin();
        }
        return user.getAuthorities().contains(Authority.ADMIN_AUTHORITY);
    }

    /**
     * Checks if the passed user is an admin user. Throws an AccessForbiddenException in case the user is not an admin
     *
     * @param user the user with authorities. If the user is null, the currently logged-in user will be used.
     **/
    default void checkIsAdminElseThrow(@Nullable User user) {
        if (!isAdmin(user)) {
            throw new AccessForbiddenException();
        }
    }
}
