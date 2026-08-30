package de.tum.cit.aet.artemis.account.service;

import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.Authority;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;

/**
 * Validates the current account state for administrative operations.
 */
@Lazy
@Service("currentUserAuthorizationService")
public class CurrentUserAuthorizationService {

    private static final Set<Authority> ADMIN_AUTHORITIES = Set.of(Authority.ADMIN_AUTHORITY, Authority.SUPER_ADMIN_AUTHORITY);

    private static final Set<Authority> SUPER_ADMIN_AUTHORITIES = Set.of(Authority.SUPER_ADMIN_AUTHORITY);

    private final UserRepository userRepository;

    public CurrentUserAuthorizationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Checks whether the authenticated account is currently active and has an administrative authority.
     *
     * @param authentication the current authentication
     * @return whether the account is currently authorized as an administrator
     */
    public boolean isCurrentUserAdmin(Authentication authentication) {
        return hasCurrentAuthority(authentication, ADMIN_AUTHORITIES);
    }

    /**
     * Checks whether the authenticated account is currently active and has the super-administrator authority.
     *
     * @param authentication the current authentication
     * @return whether the account is currently authorized as a super administrator
     */
    public boolean isCurrentUserSuperAdmin(Authentication authentication) {
        return hasCurrentAuthority(authentication, SUPER_ADMIN_AUTHORITIES);
    }

    private boolean hasCurrentAuthority(Authentication authentication, Set<Authority> requiredAuthorities) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        return userRepository.findUserWithAuthoritiesByLogin(authentication.getName()).filter(User::getActivated).filter(user -> !user.isDeleted()).map(User::getAuthorities)
                .filter(authorities -> authorities.stream().anyMatch(requiredAuthorities::contains)).isPresent();
    }
}
