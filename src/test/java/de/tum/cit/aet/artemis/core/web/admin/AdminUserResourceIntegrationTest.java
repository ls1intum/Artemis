package de.tum.cit.aet.artemis.core.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.vm.ManagedUserVM;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.PasskeyAuthenticationService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class AdminUserResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PasskeyAuthenticationService passkeyAuthenticationService;

    @BeforeEach
    void initTestCase() {
        when(passkeyAuthenticationService.isAuthenticatedWithSuperAdminApprovedPasskey()).thenReturn(true);
    }

    // ==================== Admin trying to escalate privileges - Update User ====================

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateUser_setSuperAdminByNonSuperAdmin_forbidden() throws Exception {
        // Create a regular user first
        User regularUser = userUtilService.createAndSaveUser("regularuser");

        ManagedUserVM managedUserVM = userUtilService.createManagedUserVM("regularuser");
        managedUserVM.setId(regularUser.getId());
        managedUserVM.setAuthorities(Set.of(Authority.SUPER_ADMIN_AUTHORITY.getName()));

        mockMvc.perform(put("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateUser_revokeSuperAdminByNonSuperAdmin_forbidden() throws Exception {
        // Create and persist an existing super-admin user
        userUtilService.addSuperAdmin("test");
        User superUser = userUtilService.getUserByLogin("testsuperadmin");

        ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(superUser.getLogin());
        managedUserVM.setId(superUser.getId());
        managedUserVM.setAuthorities(Set.of(Role.STUDENT.getAuthority())); // removed super-admin role

        mockMvc.perform(put("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateUser_addAdminRoleByAdmin_success() throws Exception {
        // Create a regular user
        User regularUser = userUtilService.createAndSaveUser("regularuser2");

        ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(regularUser.getLogin());
        managedUserVM.setId(regularUser.getId());
        managedUserVM.setAuthorities(Set.of(Role.ADMIN.getAuthority()));

        mockMvc.perform(put("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM))).andExpect(status().isOk());

        User updatedUser = userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(regularUser.getId());
        assertThat(updatedUser.getAuthorities()).extracting(Authority::getName).contains(Role.ADMIN.getAuthority());
    }

    // ==================== Admin trying to escalate privileges - Create User ====================

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createUser_createSuperAdminByNonSuperAdmin_forbidden() throws Exception {
        ManagedUserVM managedUserVM = userUtilService.createManagedUserVM("newsuperadmin");
        managedUserVM.setAuthorities(Set.of(Authority.SUPER_ADMIN_AUTHORITY.getName()));

        mockMvc.perform(post("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                .andExpect(status().isForbidden());

        // Verify user was not created
        assertThat(userUtilService.userExistsWithLogin("newsuperadmin")).isFalse();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createUser_createAdminByAdmin_success() throws Exception {
        ManagedUserVM managedUserVM = userUtilService.createManagedUserVM("newadmin");
        managedUserVM.setAuthorities(Set.of(Role.ADMIN.getAuthority()));

        mockMvc.perform(post("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                .andExpect(status().isCreated());

        // Verify user was created with correct authorities
        assertThat(userUtilService.userExistsWithLogin("newadmin")).isTrue();
        User createdUser = userUtilService.getUserByLogin("newadmin");
        assertThat(createdUser.getAuthorities()).extracting(Authority::getName).contains(Role.ADMIN.getAuthority());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createUser_createRegularUserByAdmin_success() throws Exception {
        ManagedUserVM managedUserVM = userUtilService.createManagedUserVM("newstudent");
        managedUserVM.setAuthorities(Set.of(Role.STUDENT.getAuthority()));

        mockMvc.perform(post("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                .andExpect(status().isCreated());

        // Verify user was created with correct authorities
        assertThat(userUtilService.userExistsWithLogin("newstudent")).isTrue();
        User createdUser = userUtilService.getUserByLogin("newstudent");
        assertThat(createdUser.getAuthorities()).extracting(Authority::getName).contains(Role.STUDENT.getAuthority()).doesNotContain(Role.SUPER_ADMIN.getAuthority());
    }

    // ==================== Super Admin creating users ====================

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void createUser_createSuperAdminBySuperAdmin_success() throws Exception {
        ManagedUserVM managedUserVM = userUtilService.createManagedUserVM("newsuperadmin2");
        managedUserVM.setAuthorities(Set.of(Authority.SUPER_ADMIN_AUTHORITY.getName()));

        mockMvc.perform(post("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                .andExpect(status().isCreated());

        // Verify user was created with super admin authority
        assertThat(userUtilService.userExistsWithLogin("newsuperadmin2")).isTrue();
        User createdUser = userUtilService.getUserByLogin("newsuperadmin2");
        assertThat(createdUser.getAuthorities()).extracting(Authority::getName).contains(Authority.SUPER_ADMIN_AUTHORITY.getName());
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void createUser_createRegularUserBySuperAdmin_success() throws Exception {
        ManagedUserVM managedUserVM = userUtilService.createManagedUserVM("newregularuser");
        managedUserVM.setAuthorities(Set.of(Role.STUDENT.getAuthority()));

        mockMvc.perform(post("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                .andExpect(status().isCreated());

        // Verify user was created
        assertThat(userUtilService.userExistsWithLogin("newregularuser")).isTrue();
        User createdUser = userUtilService.getUserByLogin("newregularuser");
        assertThat(createdUser.getAuthorities()).extracting(Authority::getName).contains(Role.STUDENT.getAuthority()).doesNotContain(Authority.SUPER_ADMIN_AUTHORITY.getName());
    }

    // ==================== Super Admin updating users ====================

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void updateUser_setSuperAdminBySuperAdmin_success() throws Exception {
        // Create a regular user
        User regularUser = userUtilService.createAndSaveUser("regularuser3");

        ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(regularUser.getLogin());
        managedUserVM.setId(regularUser.getId());
        managedUserVM.setAuthorities(Set.of(Authority.SUPER_ADMIN_AUTHORITY.getName()));

        mockMvc.perform(put("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM))).andExpect(status().isOk());

        // Verify user was updated to super admin
        User updatedUser = userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(regularUser.getId());
        assertThat(updatedUser.getAuthorities()).extracting(Authority::getName).contains(Authority.SUPER_ADMIN_AUTHORITY.getName());
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void updateUser_revokeSuperAdminBySuperAdmin_success() throws Exception {
        // Create a super admin user
        userUtilService.addSuperAdmin("test2");
        User superUser = userUtilService.getUserByLogin("test2superadmin");

        ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(superUser.getLogin());
        managedUserVM.setId(superUser.getId());
        managedUserVM.setAuthorities(Set.of(Role.STUDENT.getAuthority()));

        mockMvc.perform(put("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM))).andExpect(status().isOk());

        // Verify super admin authority was revoked
        User updatedUser = userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(superUser.getId());
        assertThat(updatedUser.getAuthorities()).extracting(Authority::getName).doesNotContain(Authority.SUPER_ADMIN_AUTHORITY.getName()).contains(Role.STUDENT.getAuthority());
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void updateUser_updateAnotherSuperAdminBySuperAdmin_success() throws Exception {
        // Create a super admin user
        userUtilService.addSuperAdmin("test3");
        User superUser = userUtilService.getUserByLogin("test3superadmin");

        ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(superUser.getLogin());
        managedUserVM.setId(superUser.getId());
        managedUserVM.setFirstName("UpdatedFirstName");
        managedUserVM.setAuthorities(Set.of(Authority.SUPER_ADMIN_AUTHORITY.getName()));

        mockMvc.perform(put("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM))).andExpect(status().isOk());

        // Verify user was updated while maintaining super admin authority
        User updatedUser = userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(superUser.getId());
        assertThat(updatedUser.getFirstName()).isEqualTo("UpdatedFirstName");
        assertThat(updatedUser.getAuthorities()).extracting(Authority::getName).contains(Authority.SUPER_ADMIN_AUTHORITY.getName());
    }

    // ==================== Admin trying to delete super admin users ====================

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deleteUser_deleteSuperAdminByNonSuperAdmin_forbidden() throws Exception {
        // Create a super admin user
        userUtilService.addSuperAdmin("test4");
        User superUser = userUtilService.getUserByLogin("test4superadmin");

        mockMvc.perform(delete("/api/core/admin/users/" + superUser.getLogin())).andExpect(status().isForbidden());

        // Verify user was not deleted
        assertThat(userUtilService.userExistsWithLogin(superUser.getLogin())).isTrue();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deleteUser_deleteRegularUserByAdmin_success() throws Exception {
        // Create a regular user
        User regularUser = userUtilService.createAndSaveUser("regularuser4");

        mockMvc.perform(delete("/api/core/admin/users/" + regularUser.getLogin())).andExpect(status().isOk());

        // Verify user was soft deleted
        User deletedUser = userTestRepository.findById(regularUser.getId()).orElseThrow();
        assertThat(deletedUser.isDeleted()).isTrue();
    }

    // ==================== Super Admin deleting users ====================

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void deleteUser_deleteSuperAdminBySuperAdmin_success() throws Exception {
        // Create a super admin user
        userUtilService.addSuperAdmin("test5");
        User superUser = userUtilService.getUserByLogin("test5superadmin");

        mockMvc.perform(delete("/api/core/admin/users/" + superUser.getLogin())).andExpect(status().isOk());

        // Verify user was soft deleted
        User deletedUser = userTestRepository.findById(superUser.getId()).orElseThrow();
        assertThat(deletedUser.isDeleted()).isTrue();
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void deleteUser_deleteRegularUserBySuperAdmin_success() throws Exception {
        // Create a regular user
        User regularUser = userUtilService.createAndSaveUser("regularuser5");

        mockMvc.perform(delete("/api/core/admin/users/" + regularUser.getLogin())).andExpect(status().isOk());

        // Verify user was soft deleted
        User deletedUser = userTestRepository.findById(regularUser.getId()).orElseThrow();
        assertThat(deletedUser.isDeleted()).isTrue();
    }

    // ==================== Admin trying to manage super admin activation state ====================

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void activateUser_activateSuperAdminByNonSuperAdmin_forbidden() throws Exception {
        // Create a deactivated super admin user
        userUtilService.addSuperAdmin("test6");
        User superUser = userUtilService.getUserByLogin("test6superadmin");
        superUser.setActivated(false);
        userTestRepository.save(superUser);

        mockMvc.perform(patch("/api/core/admin/users/" + superUser.getId() + "/activate")).andExpect(status().isForbidden());

        // Verify user was not activated
        User unchangedUser = userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(superUser.getId());
        assertThat(unchangedUser.getActivated()).isFalse();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deactivateUser_deactivateSuperAdminByNonSuperAdmin_forbidden() throws Exception {
        // Create an activated super admin user
        userUtilService.addSuperAdmin("test7");
        User superUser = userUtilService.getUserByLogin("test7superadmin");
        superUser.setActivated(true);
        userTestRepository.save(superUser);

        mockMvc.perform(patch("/api/core/admin/users/" + superUser.getId() + "/deactivate")).andExpect(status().isForbidden());

        // Verify user was not deactivated
        User unchangedUser = userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(superUser.getId());
        assertThat(unchangedUser.getActivated()).isTrue();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void activateUser_activateRegularUserByAdmin_success() throws Exception {
        // Create a deactivated regular user
        User regularUser = userUtilService.createAndSaveUser("regularuser6");
        regularUser.setActivated(false);
        userTestRepository.save(regularUser);

        mockMvc.perform(patch("/api/core/admin/users/" + regularUser.getId() + "/activate")).andExpect(status().isOk());

        // Verify user was activated
        User activatedUser = userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(regularUser.getId());
        assertThat(activatedUser.getActivated()).isTrue();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deactivateUser_deactivateRegularUserByAdmin_success() throws Exception {
        // Create an activated regular user
        User regularUser = userUtilService.createAndSaveUser("regularuser7");
        regularUser.setActivated(true);
        userTestRepository.save(regularUser);

        mockMvc.perform(patch("/api/core/admin/users/" + regularUser.getId() + "/deactivate")).andExpect(status().isOk());

        // Verify user was deactivated
        User deactivatedUser = userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(regularUser.getId());
        assertThat(deactivatedUser.getActivated()).isFalse();
    }

    // ==================== Super Admin managing activation state ====================

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void activateUser_activateSuperAdminBySuperAdmin_success() throws Exception {
        // Create a deactivated super admin user
        userUtilService.addSuperAdmin("test8");
        User superUser = userUtilService.getUserByLogin("test8superadmin");
        superUser.setActivated(false);
        userTestRepository.save(superUser);

        mockMvc.perform(patch("/api/core/admin/users/" + superUser.getId() + "/activate")).andExpect(status().isOk());

        // Verify user was activated
        User activatedUser = userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(superUser.getId());
        assertThat(activatedUser.getActivated()).isTrue();
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void deactivateUser_deactivateSuperAdminBySuperAdmin_success() throws Exception {
        // Create an activated super admin user
        userUtilService.addSuperAdmin("test9");
        User superUser = userUtilService.getUserByLogin("test9superadmin");
        superUser.setActivated(true);
        userTestRepository.save(superUser);

        mockMvc.perform(patch("/api/core/admin/users/" + superUser.getId() + "/deactivate")).andExpect(status().isOk());

        // Verify user was deactivated
        User deactivatedUser = userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(superUser.getId());
        assertThat(deactivatedUser.getActivated()).isFalse();
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void activateUser_activateRegularUserBySuperAdmin_success() throws Exception {
        // Create a deactivated regular user
        User regularUser = userUtilService.createAndSaveUser("regularuser8");
        regularUser.setActivated(false);
        userTestRepository.save(regularUser);

        mockMvc.perform(patch("/api/core/admin/users/" + regularUser.getId() + "/activate")).andExpect(status().isOk());

        // Verify user was activated
        User activatedUser = userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(regularUser.getId());
        assertThat(activatedUser.getActivated()).isTrue();
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void deactivateUser_deactivateRegularUserBySuperAdmin_success() throws Exception {
        // Create an activated regular user
        User regularUser = userUtilService.createAndSaveUser("regularuser9");
        regularUser.setActivated(true);
        userTestRepository.save(regularUser);

        mockMvc.perform(patch("/api/core/admin/users/" + regularUser.getId() + "/deactivate")).andExpect(status().isOk());

        // Verify user was deactivated
        User deactivatedUser = userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(regularUser.getId());
        assertThat(deactivatedUser.getActivated()).isFalse();
    }
}
