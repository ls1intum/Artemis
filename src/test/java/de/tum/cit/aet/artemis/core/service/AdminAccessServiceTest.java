package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.PasskeyAuthenticationService;
import de.tum.cit.aet.artemis.core.exception.PasskeyAuthenticationException;
import de.tum.cit.aet.artemis.core.security.Role;

@ExtendWith(MockitoExtension.class)
class AdminAccessServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasskeyAuthenticationService passkeyAuthenticationService;

    private AdminAccessService adminAccessService;

    @BeforeEach
    void setUp() {
        adminAccessService = new AdminAccessService(userRepository, passkeyAuthenticationService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldEnableElevationForActiveAdminWithApprovedPasskey() {
        authenticate("admin", Role.ADMIN);
        when(userRepository.isAdmin("admin")).thenReturn(true);
        when(passkeyAuthenticationService.isAuthenticatedWithSuperAdminApprovedPasskey()).thenReturn(true);

        assertThat(adminAccessService.isAdminElevationActive()).isTrue();
    }

    @Test
    void shouldNotEnableElevationForPasswordAuthenticatedAdmin() {
        authenticate("admin", Role.ADMIN);
        when(userRepository.isAdmin("admin")).thenReturn(true);
        when(passkeyAuthenticationService.isAuthenticatedWithSuperAdminApprovedPasskey())
                .thenThrow(new PasskeyAuthenticationException(PasskeyAuthenticationException.PasskeyAuthenticationFailureReason.NOT_AUTHENTICATED_WITH_PASSKEY));

        assertThat(adminAccessService.isAdminElevationActive()).isFalse();
    }

    @Test
    void shouldNotCheckPasskeyForNonAdmin() {
        authenticate("instructor", Role.INSTRUCTOR);

        assertThat(adminAccessService.isAdminElevationActive()).isFalse();
        verifyNoInteractions(userRepository, passkeyAuthenticationService);
    }

    @Test
    void shouldNotCheckPasskeyForInactiveAdmin() {
        authenticate("admin", Role.ADMIN);
        when(userRepository.isAdmin("admin")).thenReturn(false);

        assertThat(adminAccessService.isAdminElevationActive()).isFalse();
        verifyNoInteractions(passkeyAuthenticationService);
    }

    @Test
    void shouldPreserveExplicitInstructorRoleWithoutAdminElevation() {
        authenticate("admin", Role.ADMIN, Role.INSTRUCTOR);

        assertThat(adminAccessService.hasAtLeastRoleOrAdminAccess(Role.INSTRUCTOR)).isTrue();
        verifyNoInteractions(userRepository, passkeyAuthenticationService);
    }

    @Test
    void shouldNotTreatAdminAuthorityAsInstructorWithoutElevation() {
        authenticate("admin", Role.ADMIN);
        when(userRepository.isAdmin("admin")).thenReturn(true);
        when(passkeyAuthenticationService.isAuthenticatedWithSuperAdminApprovedPasskey())
                .thenThrow(new PasskeyAuthenticationException(PasskeyAuthenticationException.PasskeyAuthenticationFailureReason.NOT_AUTHENTICATED_WITH_PASSKEY));

        assertThat(adminAccessService.hasAtLeastRoleOrAdminAccess(Role.INSTRUCTOR)).isFalse();
    }

    @Test
    void shouldKeepAdminAsAuthenticatedStudentWithoutElevation() {
        authenticate("admin", Role.ADMIN);

        assertThat(adminAccessService.hasAtLeastRoleOrAdminAccess(Role.STUDENT)).isTrue();
        verify(userRepository, never()).isAdmin("admin");
        verifyNoInteractions(passkeyAuthenticationService);
    }

    @Test
    void shouldApplyExplicitTeachingRoleHierarchyWithoutAdminElevation() {
        authenticate("editor", Role.EDITOR);

        assertThat(adminAccessService.hasAtLeastRoleOrAdminAccess(Role.TEACHING_ASSISTANT)).isTrue();
        assertThat(adminAccessService.hasAtLeastRoleOrAdminAccess(Role.EDITOR)).isTrue();
        assertThat(adminAccessService.hasAtLeastRoleOrAdminAccess(Role.INSTRUCTOR)).isFalse();
        verifyNoInteractions(userRepository, passkeyAuthenticationService);
    }

    @Test
    void shouldCacheElevationWithinOneRequest() {
        authenticate("admin", Role.ADMIN);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        when(userRepository.isAdmin("admin")).thenReturn(true);
        when(passkeyAuthenticationService.isAuthenticatedWithSuperAdminApprovedPasskey()).thenReturn(true);

        assertThat(adminAccessService.isAdminElevationActive()).isTrue();
        assertThat(adminAccessService.isAdminElevationActive()).isTrue();
        verify(userRepository, times(1)).isAdmin("admin");
        verify(passkeyAuthenticationService, times(1)).isAuthenticatedWithSuperAdminApprovedPasskey();
    }

    @Test
    void shouldRequirePersistedSuperAdminStatusForSuperAdminRole() {
        authenticate("superadmin", Role.SUPER_ADMIN);
        when(userRepository.isSuperAdmin("superadmin")).thenReturn(false);

        assertThat(adminAccessService.hasAtLeastRoleOrAdminAccess(Role.SUPER_ADMIN)).isFalse();
        verifyNoInteractions(passkeyAuthenticationService);
    }

    private void authenticate(String login, Role... roles) {
        var authorities = List.of(roles).stream().map(Role::getAuthority).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login, "password", authorities));
    }
}
