package de.tum.cit.aet.artemis.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import de.tum.cit.aet.artemis.account.domain.Authority;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CurrentUserAuthorizationServiceTest {

    private static final String LOGIN = "admin";

    @Mock
    private UserRepository userRepository;

    private CurrentUserAuthorizationService currentUserAuthorizationService;

    private Authentication authentication;

    @BeforeEach
    void setUp() {
        currentUserAuthorizationService = new CurrentUserAuthorizationService(userRepository);
        authentication = UsernamePasswordAuthenticationToken.authenticated(LOGIN, null, List.of());
    }

    @Test
    void shouldAuthorizeActiveAdmin() {
        mockUser(true, false, Set.of(Authority.ADMIN_AUTHORITY));

        assertThat(currentUserAuthorizationService.isCurrentUserAdmin(authentication)).isTrue();
        assertThat(currentUserAuthorizationService.isCurrentUserSuperAdmin(authentication)).isFalse();
    }

    @Test
    void shouldAuthorizeActiveSuperAdmin() {
        mockUser(true, false, Set.of(Authority.SUPER_ADMIN_AUTHORITY));

        assertThat(currentUserAuthorizationService.isCurrentUserAdmin(authentication)).isTrue();
        assertThat(currentUserAuthorizationService.isCurrentUserSuperAdmin(authentication)).isTrue();
    }

    @Test
    void shouldRejectAccountWithoutAdministrativeAuthority() {
        mockUser(true, false, Set.of(Authority.USER_AUTHORITY));

        assertThat(currentUserAuthorizationService.isCurrentUserAdmin(authentication)).isFalse();
        assertThat(currentUserAuthorizationService.isCurrentUserSuperAdmin(authentication)).isFalse();
    }

    @Test
    void shouldRejectInactiveAdmin() {
        mockUser(false, false, Set.of(Authority.ADMIN_AUTHORITY));

        assertThat(currentUserAuthorizationService.isCurrentUserAdmin(authentication)).isFalse();
    }

    @Test
    void shouldRejectDeletedAdmin() {
        mockUser(true, true, Set.of(Authority.ADMIN_AUTHORITY));

        assertThat(currentUserAuthorizationService.isCurrentUserAdmin(authentication)).isFalse();
    }

    @Test
    void shouldRejectMissingAccount() {
        when(userRepository.findUserWithAuthoritiesByLogin(LOGIN)).thenReturn(Optional.empty());

        assertThat(currentUserAuthorizationService.isCurrentUserAdmin(authentication)).isFalse();
    }

    @Test
    void shouldRejectUnauthenticatedUserWithoutAccountLookup() {
        Authentication unauthenticated = UsernamePasswordAuthenticationToken.unauthenticated(LOGIN, null);

        assertThat(currentUserAuthorizationService.isCurrentUserAdmin(unauthenticated)).isFalse();
        verifyNoInteractions(userRepository);
    }

    private void mockUser(boolean activated, boolean deleted, Set<Authority> authorities) {
        User user = new User();
        user.setLogin(LOGIN);
        user.setActivated(activated);
        user.setDeleted(deleted);
        user.setAuthorities(authorities);
        when(userRepository.findUserWithAuthoritiesByLogin(LOGIN)).thenReturn(Optional.of(user));
    }
}
