package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.CheckReturnValue;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.PasskeyAuthenticationService;
import de.tum.cit.aet.artemis.core.exception.PasskeyAuthenticationException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;

/**
 * Separates administrator account classification from the request-bound capability to exercise administrator access.
 * <p>
 * An administrator without an approved passkey keeps explicitly assigned normal roles, but must not receive the global administrator override. Expected passkey authentication
 * failures are converted to {@code false} for normal endpoints so that list endpoints can fall back to their role-filtered queries. Explicit administrator endpoints continue to
 * use {@link PasskeyAuthenticationService} directly and therefore retain the detailed passkey error response.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service("adminAccessService")
public class AdminAccessService {

    private static final String ADMIN_ELEVATION_REQUEST_ATTRIBUTE = AdminAccessService.class.getName() + ".adminElevationActive";

    private final UserRepository userRepository;

    private final PasskeyAuthenticationService passkeyAuthenticationService;

    public AdminAccessService(UserRepository userRepository, PasskeyAuthenticationService passkeyAuthenticationService) {
        this.userRepository = userRepository;
        this.passkeyAuthenticationService = passkeyAuthenticationService;
    }

    /**
     * @return whether the persisted, active current account is an administrator
     */
    @CheckReturnValue
    public boolean isCurrentUserAdministrator() {
        if (!SecurityUtils.hasCurrentUserAnyOfAuthorities(Role.ADMIN.getAuthority(), Role.SUPER_ADMIN.getAuthority())) {
            return false;
        }
        return SecurityUtils.getCurrentUserLogin().filter(userRepository::isAdmin).isPresent();
    }

    /**
     * @return whether the persisted, active current account is a super administrator
     */
    @CheckReturnValue
    public boolean isCurrentUserSuperAdministrator() {
        if (!SecurityUtils.hasCurrentUserAnyOfAuthorities(Role.SUPER_ADMIN.getAuthority())) {
            return false;
        }
        return SecurityUtils.getCurrentUserLogin().filter(userRepository::isSuperAdmin).isPresent();
    }

    /**
     * Returns whether the current request may use the global administrator override. This method is intentionally non-throwing: normal endpoints use it to select either an
     * unrestricted administrator query or the normal role-filtered query.
     *
     * @return true only for an active administrator using a super-admin-approved passkey, or while administrator passkey enforcement is disabled
     */
    @CheckReturnValue
    public boolean isAdminElevationActive() {
        HttpServletRequest request = getCurrentRequest();
        if (request != null && request.getAttribute(ADMIN_ELEVATION_REQUEST_ATTRIBUTE) instanceof Boolean cachedResult) {
            return cachedResult;
        }

        boolean isElevationActive = computeAdminElevation();
        if (request != null) {
            request.setAttribute(ADMIN_ELEVATION_REQUEST_ATTRIBUTE, isElevationActive);
        }
        return isElevationActive;
    }

    private boolean computeAdminElevation() {
        if (!isCurrentUserAdministrator()) {
            return false;
        }
        try {
            return passkeyAuthenticationService.isAuthenticatedWithSuperAdminApprovedPasskey();
        }
        catch (PasskeyAuthenticationException ignored) {
            return false;
        }
    }

    private HttpServletRequest getCurrentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    /**
     * Method-security gate for endpoints whose required role is not tied to a resource identifier. The explicit authorities are inspected without Spring's role hierarchy so an
     * administrator authority alone cannot satisfy a teaching role. Resource-specific annotations use their authorization aspects instead.
     *
     * @param requiredRole required global role
     * @return whether the caller has an explicitly assigned sufficient normal role or active administrator elevation
     */
    @CheckReturnValue
    public boolean hasAtLeastRoleOrAdminAccess(Role requiredRole) {
        return switch (requiredRole) {
            case SUPER_ADMIN -> isCurrentUserSuperAdministrator() && isAdminElevationActive();
            case ADMIN -> isAdminElevationActive();
            case INSTRUCTOR -> SecurityUtils.hasCurrentUserAnyOfAuthorities(Role.INSTRUCTOR.getAuthority()) || isAdminElevationActive();
            case EDITOR -> SecurityUtils.hasCurrentUserAnyOfAuthorities(Role.INSTRUCTOR.getAuthority(), Role.EDITOR.getAuthority()) || isAdminElevationActive();
            case TEACHING_ASSISTANT ->
                SecurityUtils.hasCurrentUserAnyOfAuthorities(Role.INSTRUCTOR.getAuthority(), Role.EDITOR.getAuthority(), Role.TEACHING_ASSISTANT.getAuthority())
                        || isAdminElevationActive();
            case STUDENT -> SecurityUtils.isAuthenticated();
            case ANONYMOUS -> true;
        };
    }
}
