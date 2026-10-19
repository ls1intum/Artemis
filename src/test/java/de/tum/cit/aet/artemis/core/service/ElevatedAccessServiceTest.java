package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;

@ExtendWith(MockitoExtension.class)
class ElevatedAccessServiceTest {

    @Mock
    private UserTestRepository userRepository;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private ElevatedAccessService serviceRequiringPasskey() {
        return new ElevatedAccessService(userRepository, true);
    }

    @Test
    void shouldEnableElevationForActiveAdminWithApprovedPasskey() {
        authenticateWithApprovedPasskey("admin", Role.ADMIN);
        when(userRepository.isAdmin("admin")).thenReturn(true);

        assertThat(serviceRequiringPasskey().isAdminElevationActive()).isTrue();
    }

    @Test
    void shouldNotEnableElevationForPasswordAuthenticatedAdmin() {
        authenticate("admin", Role.ADMIN);

        assertThat(serviceRequiringPasskey().isAdminElevationActive()).isFalse();
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldNotEnableElevationForPasskeyWithoutSuperAdminApproval() {
        authenticateWithPasskey("admin", false, Role.ADMIN);

        assertThat(serviceRequiringPasskey().isAdminElevationActive()).isFalse();
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldKeepPersistedAdministratorClassificationSeparateFromRequestElevation() {
        authenticate("admin");
        when(userRepository.isAdmin("admin")).thenReturn(true);

        var elevatedAccessService = serviceRequiringPasskey();
        assertThat(elevatedAccessService.isCurrentUserAdministrator()).isTrue();
        assertThat(elevatedAccessService.isAdminElevationActive()).isFalse();
        verify(userRepository, times(1)).isAdmin("admin");
    }

    @Test
    void shouldNotQueryTheAccountForANonAdmin() {
        authenticateWithApprovedPasskey("instructor", Role.INSTRUCTOR);

        assertThat(serviceRequiringPasskey().isAdminElevationActive()).isFalse();
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldNotEnableElevationForInactiveAdmin() {
        authenticateWithApprovedPasskey("admin", Role.ADMIN);
        when(userRepository.isAdmin("admin")).thenReturn(false);

        assertThat(serviceRequiringPasskey().isAdminElevationActive()).isFalse();
    }

    /**
     * With the requirement disabled the administrator override is the pre-existing behaviour: the persisted role alone
     * decides it, and no session is asked to prove a passkey.
     */
    @Test
    void shouldEnableElevationForPasswordAuthenticatedAdminWhenTheRequirementIsDisabled() {
        authenticate("admin", Role.ADMIN);
        when(userRepository.isAdmin("admin")).thenReturn(true);

        assertThat(new ElevatedAccessService(userRepository, false).isAdminElevationActive()).isTrue();
    }

    @Test
    void shouldPreserveExplicitInstructorRoleWithoutAdminElevation() {
        authenticate("admin", Role.ADMIN, Role.INSTRUCTOR);

        assertThat(serviceRequiringPasskey().hasAtLeastRoleOrAdminAccess(Role.INSTRUCTOR)).isTrue();
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldNotTreatAdminAuthorityAsInstructorWithoutElevation() {
        authenticate("admin", Role.ADMIN);

        assertThat(serviceRequiringPasskey().hasAtLeastRoleOrAdminAccess(Role.INSTRUCTOR)).isFalse();
    }

    @Test
    void shouldKeepAdminAsAuthenticatedStudentWithoutElevation() {
        authenticate("admin", Role.ADMIN);

        assertThat(serviceRequiringPasskey().hasAtLeastRoleOrAdminAccess(Role.STUDENT)).isTrue();
        verify(userRepository, never()).isAdmin("admin");
    }

    @Test
    void shouldApplyExplicitTeachingRoleHierarchyWithoutAdminElevation() {
        authenticate("editor", Role.EDITOR);

        var elevatedAccessService = serviceRequiringPasskey();
        assertThat(elevatedAccessService.hasAtLeastRoleOrAdminAccess(Role.TEACHING_ASSISTANT)).isTrue();
        assertThat(elevatedAccessService.hasAtLeastRoleOrAdminAccess(Role.EDITOR)).isTrue();
        assertThat(elevatedAccessService.hasAtLeastRoleOrAdminAccess(Role.INSTRUCTOR)).isFalse();
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRequirePersistedSuperAdminStatusForSuperAdminRole() {
        authenticateWithApprovedPasskey("superadmin", Role.SUPER_ADMIN);
        when(userRepository.isAdmin("superadmin")).thenReturn(true);

        assertThat(serviceRequiringPasskey().hasAtLeastRoleOrAdminAccess(Role.SUPER_ADMIN)).isFalse();
        verify(userRepository).isSuperAdmin("superadmin");
    }

    /**
     * A background thread standing in as the system carries no token, so it carries no passkey claims either.
     */
    @Test
    void shouldNotEnableElevationForAnAuthenticationWithoutClaims() {
        assertThat(serviceRequiringPasskey().isAdminElevationActive(tokenFor("admin", Role.ADMIN))).isFalse();
        verifyNoInteractions(userRepository);
    }

    /**
     * A background thread standing in as the system carries the administrator authority but no identity, and the
     * requirement being disabled is the case where nothing else would stop it. There is no account to confirm, so the
     * answer has to be false without reaching the database.
     */
    @Test
    void shouldNotEnableElevationForTheSystemStandIn() {
        SecurityContextHolder.getContext().setAuthentication(SecurityUtils.makeAuthorizationObject(null));

        assertThat(new ElevatedAccessService(userRepository, false).isAdminElevationActive()).isFalse();
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldNotEnableElevationWithoutAnAuthentication() {
        assertThat(serviceRequiringPasskey().isAdminElevationActive(null)).isFalse();
        verifyNoInteractions(userRepository);
    }

    private void authenticate(String login, Role... roles) {
        SecurityContextHolder.getContext().setAuthentication(tokenFor(login, roles));
    }

    private void authenticateWithApprovedPasskey(String login, Role... roles) {
        authenticateWithPasskey(login, true, roles);
    }

    private void authenticateWithPasskey(String login, boolean superAdminApproved, Role... roles) {
        var authentication = tokenFor(login, roles);
        authentication.setDetails(Map.of(TokenProvider.IS_AUTHENTICATED_WITH_PASSKEY, true, TokenProvider.IS_PASSKEY_SUPER_ADMIN_APPROVED, superAdminApproved));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static UsernamePasswordAuthenticationToken tokenFor(String login, Role... roles) {
        List<SimpleGrantedAuthority> authorities = List.of(roles).stream().map(Role::getAuthority).map(SimpleGrantedAuthority::new).toList();
        return new UsernamePasswordAuthenticationToken(login, "password", authorities);
    }
}
