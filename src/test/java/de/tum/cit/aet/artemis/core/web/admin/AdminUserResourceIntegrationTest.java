package de.tum.cit.aet.artemis.core.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.vm.ManagedUserVM;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class AdminUserResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "adminuserresource";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    class AdminTryingToEscalatePrivilegesUpdateUser {

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void updateUser_setSuperAdminByNonSuperAdmin_forbidden() throws Exception {
            // Create a regular user first
            User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser");

            ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(TEST_PREFIX + "regularuser");
            managedUserVM.setId(regularUser.getId());
            managedUserVM.setAuthorities(Set.of(Authority.SUPER_ADMIN_AUTHORITY.getName()));

            mockMvc.perform(put("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void updateUser_revokeSuperAdminByNonSuperAdmin_forbidden() throws Exception {
            // Create and persist an existing super-admin user
            userUtilService.addSuperAdmin(TEST_PREFIX);
            User superUser = userUtilService.getUserByLogin(TEST_PREFIX + "superadmin");

            ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(superUser.getLogin());
            managedUserVM.setId(superUser.getId());
            managedUserVM.setAuthorities(Set.of(Role.STUDENT.getAuthority())); // removed super-admin role

            mockMvc.perform(put("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void updateUser_addAdminRoleByAdmin_forbidden() throws Exception {
            // Create a regular user
            User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser2");

            ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(regularUser.getLogin());
            managedUserVM.setId(regularUser.getId());
            managedUserVM.setAuthorities(Set.of(Role.ADMIN.getAuthority()));

            mockMvc.perform(put("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isForbidden());

            User unchangedUser = userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(regularUser.getId());
            assertThat(unchangedUser.getAuthorities()).extracting(Authority::getName).doesNotContain(Role.ADMIN.getAuthority());
        }
    }

    @Nested
    class AdminTryingToEscalatePrivilegesCreateUser {

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void createUser_createSuperAdminByNonSuperAdmin_forbidden() throws Exception {
            ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(TEST_PREFIX + "newsuperadmin");
            managedUserVM.setAuthorities(Set.of(Authority.SUPER_ADMIN_AUTHORITY.getName()));

            mockMvc.perform(post("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isForbidden());

            // Verify user was not created
            assertThat(userUtilService.userExistsWithLogin(TEST_PREFIX + "newsuperadmin")).isFalse();
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void createUser_createAdminByAdmin_forbidden() throws Exception {
            ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(TEST_PREFIX + "newadmin");
            managedUserVM.setAuthorities(Set.of(Role.ADMIN.getAuthority()));

            mockMvc.perform(post("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isForbidden());

            // Verify user was not created
            assertThat(userUtilService.userExistsWithLogin(TEST_PREFIX + "newadmin")).isFalse();
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void createUser_createRegularUserByAdmin_success() throws Exception {
            ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(TEST_PREFIX + "newstudent");
            managedUserVM.setAuthorities(Set.of(Role.STUDENT.getAuthority()));

            mockMvc.perform(post("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isCreated());

            // Verify user was created with correct authorities
            assertThat(userUtilService.userExistsWithLogin(TEST_PREFIX + "newstudent")).isTrue();
            User createdUser = userUtilService.getUserByLogin(TEST_PREFIX + "newstudent");
            assertThat(createdUser.getAuthorities()).extracting(Authority::getName).contains(Role.STUDENT.getAuthority()).doesNotContain(Role.SUPER_ADMIN.getAuthority());
        }
    }

    @Nested
    class SuperAdminCreatingUsers {

        @Test
        @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
        void createUser_createSuperAdminBySuperAdmin_success() throws Exception {
            ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(TEST_PREFIX + "newsuperadmin2");
            managedUserVM.setAuthorities(Set.of(Authority.SUPER_ADMIN_AUTHORITY.getName()));

            mockMvc.perform(post("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isCreated());

            // Verify user was created with super admin authority
            assertThat(userUtilService.userExistsWithLogin(TEST_PREFIX + "newsuperadmin2")).isTrue();
            User createdUser = userUtilService.getUserByLogin(TEST_PREFIX + "newsuperadmin2");
            assertThat(createdUser.getAuthorities()).extracting(Authority::getName).contains(Authority.SUPER_ADMIN_AUTHORITY.getName());
        }

        @Test
        @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
        void createUser_createRegularUserBySuperAdmin_success() throws Exception {
            ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(TEST_PREFIX + "newregularuser");
            managedUserVM.setAuthorities(Set.of(Role.STUDENT.getAuthority()));

            mockMvc.perform(post("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isCreated());

            // Verify user was created
            assertThat(userUtilService.userExistsWithLogin(TEST_PREFIX + "newregularuser")).isTrue();
            User createdUser = userUtilService.getUserByLogin(TEST_PREFIX + "newregularuser");
            assertThat(createdUser.getAuthorities()).extracting(Authority::getName).contains(Role.STUDENT.getAuthority()).doesNotContain(Authority.SUPER_ADMIN_AUTHORITY.getName());
        }
    }

    @Nested
    class SuperAdminUpdatingUsers {

        @Test
        @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
        void updateUser_setSuperAdminBySuperAdmin_success() throws Exception {
            // Create a regular user
            User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser3");

            ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(regularUser.getLogin());
            managedUserVM.setId(regularUser.getId());
            managedUserVM.setAuthorities(Set.of(Authority.SUPER_ADMIN_AUTHORITY.getName()));

            mockMvc.perform(put("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isOk());

            // Verify user was updated to super admin
            User updatedUser = userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(regularUser.getId());
            assertThat(updatedUser.getAuthorities()).extracting(Authority::getName).contains(Authority.SUPER_ADMIN_AUTHORITY.getName());
        }

        @Test
        @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
        void updateUser_revokeSuperAdminBySuperAdmin_success() throws Exception {
            // Create a super admin user
            userUtilService.addSuperAdmin(TEST_PREFIX + "test2");
            User superUser = userUtilService.getUserByLogin(TEST_PREFIX + "test2superadmin");

            ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(superUser.getLogin());
            managedUserVM.setId(superUser.getId());
            managedUserVM.setAuthorities(Set.of(Role.STUDENT.getAuthority()));

            mockMvc.perform(put("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isOk());

            // Verify super admin authority was revoked
            User updatedUser = userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(superUser.getId());
            assertThat(updatedUser.getAuthorities()).extracting(Authority::getName).doesNotContain(Authority.SUPER_ADMIN_AUTHORITY.getName()).contains(Role.STUDENT.getAuthority());
        }

        @Test
        @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
        void updateUser_updateAnotherSuperAdminBySuperAdmin_success() throws Exception {
            // Create a super admin user
            userUtilService.addSuperAdmin(TEST_PREFIX + "test3");
            User superUser = userUtilService.getUserByLogin(TEST_PREFIX + "test3superadmin");

            ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(superUser.getLogin());
            managedUserVM.setId(superUser.getId());
            managedUserVM.setFirstName("UpdatedFirstName");
            managedUserVM.setAuthorities(Set.of(Authority.SUPER_ADMIN_AUTHORITY.getName()));

            mockMvc.perform(put("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isOk());

            // Verify user was updated while maintaining super admin authority
            User updatedUser = userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(superUser.getId());
            assertThat(updatedUser.getFirstName()).isEqualTo("UpdatedFirstName");
            assertThat(updatedUser.getAuthorities()).extracting(Authority::getName).contains(Authority.SUPER_ADMIN_AUTHORITY.getName());
        }
    }

    @Nested
    class AdminTryingToDeleteSuperAdminUsers {

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void deleteUser_deleteSuperAdminByNonSuperAdmin_forbidden() throws Exception {
            // Create a super admin user
            userUtilService.addSuperAdmin(TEST_PREFIX + "test4");
            User superUser = userUtilService.getUserByLogin(TEST_PREFIX + "test4superadmin");

            mockMvc.perform(delete("/api/core/admin/users/" + superUser.getLogin())).andExpect(status().isForbidden());

            // Verify user was not deleted
            assertThat(userUtilService.userExistsWithLogin(superUser.getLogin())).isTrue();
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void deleteUser_deleteRegularUserByAdmin_success() throws Exception {
            // Create a regular user
            User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser4");

            mockMvc.perform(delete("/api/core/admin/users/" + regularUser.getLogin())).andExpect(status().isOk());

            // Verify user was soft deleted
            User deletedUser = userTestRepository.findById(regularUser.getId()).orElseThrow();
            assertThat(deletedUser.isDeleted()).isTrue();
        }
    }

    @Nested
    class SuperAdminDeletingUsers {

        @Test
        @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
        void deleteUser_deleteSuperAdminBySuperAdmin_success() throws Exception {
            // Create a super admin user
            userUtilService.addSuperAdmin(TEST_PREFIX + "test5");
            User superUser = userUtilService.getUserByLogin(TEST_PREFIX + "test5superadmin");

            mockMvc.perform(delete("/api/core/admin/users/" + superUser.getLogin())).andExpect(status().isOk());

            // Verify user was soft deleted
            User deletedUser = userTestRepository.findById(superUser.getId()).orElseThrow();
            assertThat(deletedUser.isDeleted()).isTrue();
        }

        @Test
        @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
        void deleteUser_deleteRegularUserBySuperAdmin_success() throws Exception {
            // Create a regular user
            User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser5");

            mockMvc.perform(delete("/api/core/admin/users/" + regularUser.getLogin())).andExpect(status().isOk());

            // Verify user was soft deleted
            User deletedUser = userTestRepository.findById(regularUser.getId()).orElseThrow();
            assertThat(deletedUser.isDeleted()).isTrue();
        }
    }

    @Nested
    class AdminTryingToManageSuperAdminActivationState {

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void activateUser_activateSuperAdminByNonSuperAdmin_forbidden() throws Exception {
            // Create a deactivated super admin user
            userUtilService.addSuperAdmin(TEST_PREFIX + "test6");
            User superUser = userUtilService.getUserByLogin(TEST_PREFIX + "test6superadmin");
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
            userUtilService.addSuperAdmin(TEST_PREFIX + "test7");
            User superUser = userUtilService.getUserByLogin(TEST_PREFIX + "test7superadmin");
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
            User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser6");
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
            User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser7");
            regularUser.setActivated(true);
            userTestRepository.save(regularUser);

            mockMvc.perform(patch("/api/core/admin/users/" + regularUser.getId() + "/deactivate")).andExpect(status().isOk());

            // Verify user was deactivated
            User deactivatedUser = userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(regularUser.getId());
            assertThat(deactivatedUser.getActivated()).isFalse();
        }
    }

    @Nested
    class SuperAdminManagingActivationState {

        @Test
        @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
        void activateUser_activateSuperAdminBySuperAdmin_success() throws Exception {
            // Create a deactivated super admin user
            userUtilService.addSuperAdmin(TEST_PREFIX + "test8");
            User superUser = userUtilService.getUserByLogin(TEST_PREFIX + "test8superadmin");
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
            userUtilService.addSuperAdmin(TEST_PREFIX + "test9");
            User superUser = userUtilService.getUserByLogin(TEST_PREFIX + "test9superadmin");
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
            User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser8");
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
            User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser9");
            regularUser.setActivated(true);
            userTestRepository.save(regularUser);

            mockMvc.perform(patch("/api/core/admin/users/" + regularUser.getId() + "/deactivate")).andExpect(status().isOk());

            // Verify user was deactivated
            User deactivatedUser = userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(regularUser.getId());
            assertThat(deactivatedUser.getActivated()).isFalse();
        }
    }

    @Nested
    class AdminTryingToManageAdminUsers {

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void updateUser_updateAdminByAdmin_forbidden() throws Exception {
            // Create an admin user using the utility method
            userUtilService.addAdmin(TEST_PREFIX + "adminuser1");
            User adminUser = userUtilService.getUserByLogin(TEST_PREFIX + "adminuser1admin");

            ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(adminUser.getLogin());
            managedUserVM.setId(adminUser.getId());
            managedUserVM.setFirstName("UpdatedFirstName");
            managedUserVM.setAuthorities(Set.of(Role.ADMIN.getAuthority()));

            mockMvc.perform(put("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isForbidden());

            // Verify user was not updated
            User unchangedUser = userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(adminUser.getId());
            assertThat(unchangedUser.getFirstName()).isNotEqualTo("UpdatedFirstName");
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void activateUser_activateAdminByAdmin_forbidden() throws Exception {
            // Create a deactivated admin user
            userUtilService.addAdmin(TEST_PREFIX + "adminuser2");
            User adminUser = userUtilService.getUserByLogin(TEST_PREFIX + "adminuser2admin");
            adminUser.setActivated(false);
            userTestRepository.save(adminUser);

            mockMvc.perform(patch("/api/core/admin/users/" + adminUser.getId() + "/activate")).andExpect(status().isForbidden());

            // Verify user was not activated
            User unchangedUser = userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(adminUser.getId());
            assertThat(unchangedUser.getActivated()).isFalse();
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void deactivateUser_deactivateAdminByAdmin_forbidden() throws Exception {
            // Create an activated admin user
            userUtilService.addAdmin(TEST_PREFIX + "adminuser3");
            User adminUser = userUtilService.getUserByLogin(TEST_PREFIX + "adminuser3admin");
            adminUser.setActivated(true);
            userTestRepository.save(adminUser);

            mockMvc.perform(patch("/api/core/admin/users/" + adminUser.getId() + "/deactivate")).andExpect(status().isForbidden());

            // Verify user was not deactivated
            User unchangedUser = userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(adminUser.getId());
            assertThat(unchangedUser.getActivated()).isTrue();
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void deleteUser_deleteAdminByAdmin_forbidden() throws Exception {
            // Create an admin user
            userUtilService.addAdmin(TEST_PREFIX + "adminuser4");
            User adminUser = userUtilService.getUserByLogin(TEST_PREFIX + "adminuser4admin");

            mockMvc.perform(delete("/api/core/admin/users/" + adminUser.getLogin())).andExpect(status().isForbidden());

            // Verify user was not deleted
            User unchangedUser = userTestRepository.findById(adminUser.getId()).orElseThrow();
            assertThat(unchangedUser.isDeleted()).isFalse();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "calleradmin", roles = "ADMIN")
        void deleteUsers_deleteAdminsByAdmin_forbidden() throws Exception {
            // Create the calling user (admin) in the database - required for batch delete
            userUtilService.addAdmin(TEST_PREFIX + "caller");

            // Create admin users to be deleted
            userUtilService.addAdmin(TEST_PREFIX + "adminuser5");
            User adminUser1 = userUtilService.getUserByLogin(TEST_PREFIX + "adminuser5admin");

            userUtilService.addAdmin(TEST_PREFIX + "adminuser6");
            User adminUser2 = userUtilService.getUserByLogin(TEST_PREFIX + "adminuser6admin");

            mockMvc.perform(delete("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.List.of(adminUser1.getLogin(), adminUser2.getLogin())))).andExpect(status().isForbidden());

            // Verify users were not deleted
            User unchangedUser1 = userTestRepository.findById(adminUser1.getId()).orElseThrow();
            User unchangedUser2 = userTestRepository.findById(adminUser2.getId()).orElseThrow();
            assertThat(unchangedUser1.isDeleted()).isFalse();
            assertThat(unchangedUser2.isDeleted()).isFalse();
        }
    }

    @Nested
    class SuperAdminManagingAdminUsers {

        @Test
        @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
        void createUser_createAdminBySuperAdmin_success() throws Exception {
            ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(TEST_PREFIX + "newadminsuperadmin");
            managedUserVM.setAuthorities(Set.of(Role.ADMIN.getAuthority()));

            mockMvc.perform(post("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isCreated());

            // Verify user was created with correct authorities
            assertThat(userUtilService.userExistsWithLogin(TEST_PREFIX + "newadminsuperadmin")).isTrue();
            User createdUser = userUtilService.getUserByLogin(TEST_PREFIX + "newadminsuperadmin");
            assertThat(createdUser.getAuthorities()).extracting(Authority::getName).contains(Role.ADMIN.getAuthority());
        }

        @Test
        @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
        void updateUser_updateAdminBySuperAdmin_success() throws Exception {
            // Create an admin user
            userUtilService.addAdmin(TEST_PREFIX + "adminuser7");
            User adminUser = userUtilService.getUserByLogin(TEST_PREFIX + "adminuser7admin");

            ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(adminUser.getLogin());
            managedUserVM.setId(adminUser.getId());
            managedUserVM.setFirstName("UpdatedFirstName");
            managedUserVM.setAuthorities(Set.of(Role.ADMIN.getAuthority()));

            mockMvc.perform(put("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isOk());

            // Verify user was updated
            User updatedUser = userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(adminUser.getId());
            assertThat(updatedUser.getFirstName()).isEqualTo("UpdatedFirstName");
        }

        @Test
        @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
        void activateUser_activateAdminBySuperAdmin_success() throws Exception {
            // Create a deactivated admin user
            userUtilService.addAdmin(TEST_PREFIX + "adminuser8");
            User adminUser = userUtilService.getUserByLogin(TEST_PREFIX + "adminuser8admin");
            adminUser.setActivated(false);
            userTestRepository.save(adminUser);

            mockMvc.perform(patch("/api/core/admin/users/" + adminUser.getId() + "/activate")).andExpect(status().isOk());

            // Verify user was activated
            User activatedUser = userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(adminUser.getId());
            assertThat(activatedUser.getActivated()).isTrue();
        }

        @Test
        @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
        void deactivateUser_deactivateAdminBySuperAdmin_success() throws Exception {
            // Create an activated admin user
            userUtilService.addAdmin(TEST_PREFIX + "adminuser9");
            User adminUser = userUtilService.getUserByLogin(TEST_PREFIX + "adminuser9admin");
            adminUser.setActivated(true);
            userTestRepository.save(adminUser);

            mockMvc.perform(patch("/api/core/admin/users/" + adminUser.getId() + "/deactivate")).andExpect(status().isOk());

            // Verify user was deactivated
            User deactivatedUser = userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(adminUser.getId());
            assertThat(deactivatedUser.getActivated()).isFalse();
        }

        @Test
        @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
        void deleteUser_deleteAdminBySuperAdmin_success() throws Exception {
            // Create an admin user
            userUtilService.addAdmin(TEST_PREFIX + "adminuser10");
            User adminUser = userUtilService.getUserByLogin(TEST_PREFIX + "adminuser10admin");

            mockMvc.perform(delete("/api/core/admin/users/" + adminUser.getLogin())).andExpect(status().isOk());

            // Verify user was soft deleted
            User deletedUser = userTestRepository.findById(adminUser.getId()).orElseThrow();
            assertThat(deletedUser.isDeleted()).isTrue();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "callersuperadmin", roles = "SUPER_ADMIN")
        void deleteUsers_deleteAdminsBySuperAdmin_success() throws Exception {
            // Create the calling user (super admin) in the database - required for batch delete
            userUtilService.addSuperAdmin(TEST_PREFIX + "caller");

            // Create admin users to be deleted
            userUtilService.addAdmin(TEST_PREFIX + "adminuser11");
            User adminUser1 = userUtilService.getUserByLogin(TEST_PREFIX + "adminuser11admin");

            userUtilService.addAdmin(TEST_PREFIX + "adminuser12");
            User adminUser2 = userUtilService.getUserByLogin(TEST_PREFIX + "adminuser12admin");

            mockMvc.perform(delete("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.List.of(adminUser1.getLogin(), adminUser2.getLogin())))).andExpect(status().isOk());

            // Verify users were soft deleted
            User deletedUser1 = userTestRepository.findById(adminUser1.getId()).orElseThrow();
            User deletedUser2 = userTestRepository.findById(adminUser2.getId()).orElseThrow();
            assertThat(deletedUser1.isDeleted()).isTrue();
            assertThat(deletedUser2.isDeleted()).isTrue();
        }
    }

    @Nested
    class DefaultAdminProtection {

        // The default admin username is configured in application-artemis.yml as "artemis_admin"
        private static final String DEFAULT_ADMIN_USERNAME = "artemis_admin";

        @BeforeEach
        void ensureDefaultAdminExists() {
            // Ensure the default admin user exists with super admin authority for parallel test execution
            User existingAdmin = userTestRepository.findOneWithGroupsAndAuthoritiesByLogin(DEFAULT_ADMIN_USERNAME).orElse(null);
            if (existingAdmin == null) {
                // Create the default admin if it doesn't exist
                User defaultAdmin = userUtilService.createAndSaveUser(DEFAULT_ADMIN_USERNAME);
                defaultAdmin.setActivated(true);
                defaultAdmin.setFirstName("Administrator");
                defaultAdmin.setLastName("Administrator");
                defaultAdmin.setEmail("admin@localhost");
                defaultAdmin.setAuthorities(Set.of(Authority.SUPER_ADMIN_AUTHORITY, new Authority(Role.STUDENT.getAuthority())));
                userTestRepository.save(defaultAdmin);
            }
            else if (!existingAdmin.getAuthorities().contains(Authority.SUPER_ADMIN_AUTHORITY)) {
                // Ensure the existing admin has super admin authority (might be modified by other tests)
                existingAdmin.getAuthorities().add(Authority.SUPER_ADMIN_AUTHORITY);
                userTestRepository.save(existingAdmin);
            }
        }

        @Test
        @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
        void updateUser_removeSuperAdminFromDefaultAdmin_badRequest() throws Exception {
            // Get the default admin user (created by UserService.applicationReady())
            User defaultAdmin = userTestRepository.findOneWithGroupsAndAuthoritiesByLogin(DEFAULT_ADMIN_USERNAME).orElseThrow();

            // Verify the default admin has super admin authority
            assertThat(defaultAdmin.getAuthorities()).extracting(Authority::getName).contains(Authority.SUPER_ADMIN_AUTHORITY.getName());

            // Try to remove super admin rights from the default admin
            ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(defaultAdmin.getLogin());
            managedUserVM.setId(defaultAdmin.getId());
            managedUserVM.setAuthorities(Set.of(Role.STUDENT.getAuthority())); // Remove super admin, keep only student

            mockMvc.perform(put("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isBadRequest());

            // Verify the default admin still has super admin authority
            User unchangedAdmin = userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(defaultAdmin.getId());
            assertThat(unchangedAdmin.getAuthorities()).extracting(Authority::getName).contains(Authority.SUPER_ADMIN_AUTHORITY.getName());
        }

        @Test
        @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
        void updateUser_updateDefaultAdminKeepingSuperAdmin_success() throws Exception {
            // Get the default admin user
            User defaultAdmin = userTestRepository.findOneWithGroupsAndAuthoritiesByLogin(DEFAULT_ADMIN_USERNAME).orElseThrow();

            // Update the default admin while keeping super admin rights
            ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(defaultAdmin.getLogin());
            managedUserVM.setId(defaultAdmin.getId());
            managedUserVM.setFirstName("UpdatedDefaultAdmin");
            managedUserVM.setAuthorities(Set.of(Authority.SUPER_ADMIN_AUTHORITY.getName(), Role.STUDENT.getAuthority()));

            mockMvc.perform(put("/api/core/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isOk());

            // Verify the update was applied and super admin authority is retained
            User updatedAdmin = userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(defaultAdmin.getId());
            assertThat(updatedAdmin.getFirstName()).isEqualTo("UpdatedDefaultAdmin");
            assertThat(updatedAdmin.getAuthorities()).extracting(Authority::getName).contains(Authority.SUPER_ADMIN_AUTHORITY.getName());
        }
    }
}
