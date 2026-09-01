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

import de.tum.cit.aet.artemis.account.service.PasskeyAuthenticationService;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.exception.PasskeyAuthenticationException;
import de.tum.cit.aet.artemis.core.security.Role;

@ExtendWith(MockitoExtension.class)
class ElevatedAccessServiceTest {

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private PasskeyAuthenticationService passkeyAuthenticationService;

    private ElevatedAccessService elevatedAccessService;

    @BeforeEach
    void setUp() {
        elevatedAccessService = new ElevatedAccessService(userRepository, passkeyAuthenticationService);
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

        assertThat(elevatedAccessService.isAdminElevationActive()).isTrue();
    }

    @Test
    void shouldNotEnableElevationForPasswordAuthenticatedAdmin() {
        authenticate("admin", Role.ADMIN);
        when(userRepository.isAdmin("admin")).thenReturn(true);
        when(passkeyAuthenticationService.isAuthenticatedWithSuperAdminApprovedPasskey())
                .thenThrow(new PasskeyAuthenticationException(PasskeyAuthenticationException.PasskeyAuthenticationFailureReason.NOT_AUTHENTICATED_WITH_PASSKEY));

        assertThat(elevatedAccessService.isAdminElevationActive()).isFalse();
    }

    @Test
    void shouldKeepPersistedAdministratorClassificationSeparateFromRequestElevation() {
        authenticate("admin");
        when(userRepository.isAdmin("admin")).thenReturn(true);

        assertThat(elevatedAccessService.isCurrentUserAdministrator()).isTrue();
        assertThat(elevatedAccessService.isAdminElevationActive()).isFalse();
        verify(userRepository, times(1)).isAdmin("admin");
        verifyNoInteractions(passkeyAuthenticationService);
    }

    @Test
    void shouldNotCheckPasskeyForNonAdmin() {
        authenticate("instructor", Role.INSTRUCTOR);

        assertThat(elevatedAccessService.isAdminElevationActive()).isFalse();
        verifyNoInteractions(userRepository, passkeyAuthenticationService);
    }

    @Test
    void shouldNotCheckPasskeyForInactiveAdmin() {
        authenticate("admin", Role.ADMIN);
        when(userRepository.isAdmin("admin")).thenReturn(false);

        assertThat(elevatedAccessService.isAdminElevationActive()).isFalse();
        verifyNoInteractions(passkeyAuthenticationService);
    }

    @Test
    void shouldPreserveExplicitInstructorRoleWithoutAdminElevation() {
        authenticate("admin", Role.ADMIN, Role.INSTRUCTOR);

        assertThat(elevatedAccessService.hasAtLeastRoleOrAdminAccess(Role.INSTRUCTOR)).isTrue();
        verifyNoInteractions(userRepository, passkeyAuthenticationService);
    }

    @Test
    void shouldNotTreatAdminAuthorityAsInstructorWithoutElevation() {
        authenticate("admin", Role.ADMIN);
        when(userRepository.isAdmin("admin")).thenReturn(true);
        when(passkeyAuthenticationService.isAuthenticatedWithSuperAdminApprovedPasskey())
                .thenThrow(new PasskeyAuthenticationException(PasskeyAuthenticationException.PasskeyAuthenticationFailureReason.NOT_AUTHENTICATED_WITH_PASSKEY));

        assertThat(elevatedAccessService.hasAtLeastRoleOrAdminAccess(Role.INSTRUCTOR)).isFalse();
    }

    @Test
    void shouldKeepAdminAsAuthenticatedStudentWithoutElevation() {
        authenticate("admin", Role.ADMIN);

        assertThat(elevatedAccessService.hasAtLeastRoleOrAdminAccess(Role.STUDENT)).isTrue();
        verify(userRepository, never()).isAdmin("admin");
        verifyNoInteractions(passkeyAuthenticationService);
    }

    @Test
    void shouldApplyExplicitTeachingRoleHierarchyWithoutAdminElevation() {
        authenticate("editor", Role.EDITOR);

        assertThat(elevatedAccessService.hasAtLeastRoleOrAdminAccess(Role.TEACHING_ASSISTANT)).isTrue();
        assertThat(elevatedAccessService.hasAtLeastRoleOrAdminAccess(Role.EDITOR)).isTrue();
        assertThat(elevatedAccessService.hasAtLeastRoleOrAdminAccess(Role.INSTRUCTOR)).isFalse();
        verifyNoInteractions(userRepository, passkeyAuthenticationService);
    }

    @Test
    void shouldCacheElevationWithinOneRequest() {
        authenticate("admin", Role.ADMIN);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        when(userRepository.isAdmin("admin")).thenReturn(true);
        when(passkeyAuthenticationService.isAuthenticatedWithSuperAdminApprovedPasskey()).thenReturn(true);

        assertThat(elevatedAccessService.isAdminElevationActive()).isTrue();
        assertThat(elevatedAccessService.isAdminElevationActive()).isTrue();
        verify(userRepository, times(1)).isAdmin("admin");
        verify(passkeyAuthenticationService, times(1)).isAuthenticatedWithSuperAdminApprovedPasskey();
    }

    @Test
    void shouldRequirePersistedSuperAdminStatusForSuperAdminRole() {
        authenticate("superadmin", Role.SUPER_ADMIN);
        when(userRepository.isAdmin("superadmin")).thenReturn(true);
        when(passkeyAuthenticationService.isAuthenticatedWithSuperAdminApprovedPasskey()).thenReturn(true);

        assertThat(elevatedAccessService.hasAtLeastRoleOrAdminAccess(Role.SUPER_ADMIN)).isFalse();
        verify(userRepository).isSuperAdmin("superadmin");
        verify(passkeyAuthenticationService).isAuthenticatedWithSuperAdminApprovedPasskey();
    }

    private void authenticate(String login, Role... roles) {
        var authorities = List.of(roles).stream().map(Role::getAuthority).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login, "password", authorities));
    }
}
