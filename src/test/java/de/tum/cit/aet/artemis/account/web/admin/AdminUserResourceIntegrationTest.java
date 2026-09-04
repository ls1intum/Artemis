package de.tum.cit.aet.artemis.account.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.account.domain.Authority;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.dto.UserDeletionResultStatus;
import de.tum.cit.aet.artemis.account.service.UserActivityService;
import de.tum.cit.aet.artemis.account.service.user.UserService;
import de.tum.cit.aet.artemis.account.service.user.deletion.PermanentUserDeletionService;
import de.tum.cit.aet.artemis.account.service.user.deletion.UserDeletionMode;
import de.tum.cit.aet.artemis.account.service.user.deletion.UserDeletionPlanService;
import de.tum.cit.aet.artemis.account.service.user.deletion.UserDeletionReferencePolicy;
import de.tum.cit.aet.artemis.account.service.user.deletion.UserReferenceCleanupService;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.dto.vm.ManagedUserVM;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionVersionRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.exercise.team.TeamUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class AdminUserResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "adminuserresource";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserActivityService userActivityService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private PermanentUserDeletionService permanentUserDeletionService;

    @Autowired
    private UserReferenceCleanupService userReferenceCleanupService;

    @Autowired
    private UserDeletionPlanService userDeletionPlanService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ExerciseVersionService exerciseVersionService;

    @Autowired
    private SubmissionVersionRepository submissionVersionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpAuthenticatedAdministrators() {
        // Admin endpoints validate the current account state in addition to the authorities in the mock security context.
        userUtilService.addAdmin("");
        userUtilService.addSuperAdmin("");
    }

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

            mockMvc.perform(put("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
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

            mockMvc.perform(put("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
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

            mockMvc.perform(put("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isForbidden());

            User unchangedUser = userTestRepository.findByIdWithAuthoritiesElseThrow(regularUser.getId());
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

            mockMvc.perform(post("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isForbidden());

            // Verify user was not created
            assertThat(userUtilService.userExistsWithLogin(TEST_PREFIX + "newsuperadmin")).isFalse();
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void createUser_createAdminByAdmin_forbidden() throws Exception {
            ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(TEST_PREFIX + "newadmin");
            managedUserVM.setAuthorities(Set.of(Role.ADMIN.getAuthority()));

            mockMvc.perform(post("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isForbidden());

            // Verify user was not created
            assertThat(userUtilService.userExistsWithLogin(TEST_PREFIX + "newadmin")).isFalse();
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void createUser_createRegularUserByAdmin_success() throws Exception {
            ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(TEST_PREFIX + "newstudent");
            managedUserVM.setAuthorities(Set.of(Role.STUDENT.getAuthority()));

            mockMvc.perform(post("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
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

            mockMvc.perform(post("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
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

            mockMvc.perform(post("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
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

            mockMvc.perform(put("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isOk());

            // Verify user was updated to super admin
            User updatedUser = userTestRepository.findByIdWithAuthoritiesElseThrow(regularUser.getId());
            assertThat(updatedUser.getAuthorities()).extracting(Authority::getName).contains(Authority.SUPER_ADMIN_AUTHORITY.getName());
        }

        @Test
        @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
        void updateUser_externalToInternalWithoutPassword_generatesPassword() throws Exception {
            ManagedUserVM externalUserDTO = userUtilService.createManagedUserVM(TEST_PREFIX + "extuser");
            externalUserDTO.setInternal(false);
            mockMvc.perform(post("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(externalUserDTO)))
                    .andExpect(status().isCreated());

            User externalUser = userTestRepository.findOneByLogin(TEST_PREFIX + "extuser").orElseThrow();
            assertThat(externalUser.isInternal()).isFalse();
            // set external user to internal and provide no password
            ManagedUserVM updateDTO = userUtilService.createManagedUserVM(externalUser.getLogin());
            updateDTO.setId(externalUser.getId());
            updateDTO.setInternal(true);
            updateDTO.setPassword(null);

            mockMvc.perform(put("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(updateDTO))).andExpect(status().isOk());

            User updatedUser = userTestRepository.findByIdWithAuthoritiesElseThrow(externalUser.getId());
            assertThat(updatedUser.isInternal()).isTrue();
            assertThat(updatedUser.getPassword()).isNotNull().isNotEmpty();
        }

        @Test
        @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
        void updateUser_internalToExternal_reverseTransition_success() throws Exception {
            User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "internaluser");
            assertThat(regularUser.isInternal()).isTrue();

            ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(regularUser.getLogin());
            managedUserVM.setId(regularUser.getId());
            managedUserVM.setInternal(false);

            mockMvc.perform(put("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isOk());

            User updatedUser = userTestRepository.findByIdWithAuthoritiesElseThrow(regularUser.getId());
            assertThat(updatedUser.isInternal()).isFalse();
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

            mockMvc.perform(put("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isOk());

            // Verify super admin authority was revoked
            User updatedUser = userTestRepository.findByIdWithAuthoritiesElseThrow(superUser.getId());
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

            mockMvc.perform(put("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isOk());

            // Verify user was updated while maintaining super admin authority
            User updatedUser = userTestRepository.findByIdWithAuthoritiesElseThrow(superUser.getId());
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

            mockMvc.perform(delete("/api/account/admin/users/" + superUser.getLogin()).contentType(MediaType.APPLICATION_JSON).content("{\"impactFingerprint\":\"irrelevant\"}"))
                    .andExpect(status().isForbidden());

            // Verify user was not deleted
            assertThat(userUtilService.userExistsWithLogin(superUser.getLogin())).isTrue();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "calleradmin", roles = "ADMIN")
        void deleteUser_deleteRegularUserByAdmin_success() throws Exception {
            userUtilService.addAdmin(TEST_PREFIX + "caller");

            // Create a regular user
            User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser4");

            permanentlyDeleteUser(regularUser.getLogin());

            assertThat(userTestRepository.findById(regularUser.getId())).isEmpty();
        }
    }

    @Nested
    class SuperAdminDeletingUsers {

        @Test
        @WithMockUser(username = TEST_PREFIX + "callersuperadmin", roles = "SUPER_ADMIN")
        void deleteUser_deleteSuperAdminBySuperAdmin_forbidden() throws Exception {
            userUtilService.addSuperAdmin(TEST_PREFIX + "caller");

            // Create a super admin user
            userUtilService.addSuperAdmin(TEST_PREFIX + "test5");
            User superUser = userUtilService.getUserByLogin(TEST_PREFIX + "test5superadmin");

            mockMvc.perform(delete("/api/account/admin/users/" + superUser.getLogin()).contentType(MediaType.APPLICATION_JSON).content("{\"impactFingerprint\":\"irrelevant\"}"))
                    .andExpect(status().isForbidden());

            assertThat(userTestRepository.findById(superUser.getId())).isPresent();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "callersuperadmin", roles = "SUPER_ADMIN")
        void deleteUser_deleteRegularUserBySuperAdmin_success() throws Exception {
            userUtilService.addSuperAdmin(TEST_PREFIX + "caller");

            // Create a regular user
            User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser5");

            permanentlyDeleteUser(regularUser.getLogin());

            assertThat(userTestRepository.findById(regularUser.getId())).isEmpty();
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

            mockMvc.perform(patch("/api/account/admin/users/" + superUser.getId() + "/activate")).andExpect(status().isForbidden());

            // Verify user was not activated
            User unchangedUser = userTestRepository.findByIdWithAuthoritiesElseThrow(superUser.getId());
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

            mockMvc.perform(patch("/api/account/admin/users/" + superUser.getId() + "/deactivate")).andExpect(status().isForbidden());

            // Verify user was not deactivated
            User unchangedUser = userTestRepository.findByIdWithAuthoritiesElseThrow(superUser.getId());
            assertThat(unchangedUser.getActivated()).isTrue();
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void activateUser_activateRegularUserByAdmin_success() throws Exception {
            // Create a deactivated regular user
            User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser6");
            regularUser.setActivated(false);
            userTestRepository.save(regularUser);

            mockMvc.perform(patch("/api/account/admin/users/" + regularUser.getId() + "/activate")).andExpect(status().isOk());

            // Verify user was activated
            User activatedUser = userTestRepository.findByIdWithAuthoritiesElseThrow(regularUser.getId());
            assertThat(activatedUser.getActivated()).isTrue();
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void deactivateUser_deactivateRegularUserByAdmin_success() throws Exception {
            // Create an activated regular user
            User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser7");
            regularUser.setActivated(true);
            userTestRepository.save(regularUser);

            mockMvc.perform(patch("/api/account/admin/users/" + regularUser.getId() + "/deactivate")).andExpect(status().isOk());

            // Verify user was deactivated
            User deactivatedUser = userTestRepository.findByIdWithAuthoritiesElseThrow(regularUser.getId());
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

            mockMvc.perform(patch("/api/account/admin/users/" + superUser.getId() + "/activate")).andExpect(status().isOk());

            // Verify user was activated
            User activatedUser = userTestRepository.findByIdWithAuthoritiesElseThrow(superUser.getId());
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

            mockMvc.perform(patch("/api/account/admin/users/" + superUser.getId() + "/deactivate")).andExpect(status().isOk());

            // Verify user was deactivated
            User deactivatedUser = userTestRepository.findByIdWithAuthoritiesElseThrow(superUser.getId());
            assertThat(deactivatedUser.getActivated()).isFalse();
        }

        @Test
        @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
        void activateUser_activateRegularUserBySuperAdmin_success() throws Exception {
            // Create a deactivated regular user
            User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser8");
            regularUser.setActivated(false);
            userTestRepository.save(regularUser);

            mockMvc.perform(patch("/api/account/admin/users/" + regularUser.getId() + "/activate")).andExpect(status().isOk());

            // Verify user was activated
            User activatedUser = userTestRepository.findByIdWithAuthoritiesElseThrow(regularUser.getId());
            assertThat(activatedUser.getActivated()).isTrue();
        }

        @Test
        @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
        void deactivateUser_deactivateRegularUserBySuperAdmin_success() throws Exception {
            // Create an activated regular user
            User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser9");
            regularUser.setActivated(true);
            userTestRepository.save(regularUser);

            mockMvc.perform(patch("/api/account/admin/users/" + regularUser.getId() + "/deactivate")).andExpect(status().isOk());

            // Verify user was deactivated
            User deactivatedUser = userTestRepository.findByIdWithAuthoritiesElseThrow(regularUser.getId());
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

            mockMvc.perform(put("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isForbidden());

            // Verify user was not updated
            User unchangedUser = userTestRepository.findByIdWithAuthoritiesElseThrow(adminUser.getId());
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

            mockMvc.perform(patch("/api/account/admin/users/" + adminUser.getId() + "/activate")).andExpect(status().isForbidden());

            // Verify user was not activated
            User unchangedUser = userTestRepository.findByIdWithAuthoritiesElseThrow(adminUser.getId());
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

            mockMvc.perform(patch("/api/account/admin/users/" + adminUser.getId() + "/deactivate")).andExpect(status().isForbidden());

            // Verify user was not deactivated
            User unchangedUser = userTestRepository.findByIdWithAuthoritiesElseThrow(adminUser.getId());
            assertThat(unchangedUser.getActivated()).isTrue();
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void deleteUser_deleteAdminByAdmin_forbidden() throws Exception {
            // Create an admin user
            userUtilService.addAdmin(TEST_PREFIX + "adminuser4");
            User adminUser = userUtilService.getUserByLogin(TEST_PREFIX + "adminuser4admin");

            mockMvc.perform(delete("/api/account/admin/users/" + adminUser.getLogin()).contentType(MediaType.APPLICATION_JSON).content("{\"impactFingerprint\":\"irrelevant\"}"))
                    .andExpect(status().isForbidden());

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

            mockMvc.perform(delete("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("users",
                            List.of(Map.of("login", adminUser1.getLogin(), "impactFingerprint", "irrelevant"),
                                    Map.of("login", adminUser2.getLogin(), "impactFingerprint", "irrelevant"))))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value(UserDeletionResultStatus.FORBIDDEN.name()))
                    .andExpect(jsonPath("$[1].status").value(UserDeletionResultStatus.FORBIDDEN.name()));

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

            mockMvc.perform(post("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
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

            mockMvc.perform(put("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isOk());

            // Verify user was updated
            User updatedUser = userTestRepository.findByIdWithAuthoritiesElseThrow(adminUser.getId());
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

            mockMvc.perform(patch("/api/account/admin/users/" + adminUser.getId() + "/activate")).andExpect(status().isOk());

            // Verify user was activated
            User activatedUser = userTestRepository.findByIdWithAuthoritiesElseThrow(adminUser.getId());
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

            mockMvc.perform(patch("/api/account/admin/users/" + adminUser.getId() + "/deactivate")).andExpect(status().isOk());

            // Verify user was deactivated
            User deactivatedUser = userTestRepository.findByIdWithAuthoritiesElseThrow(adminUser.getId());
            assertThat(deactivatedUser.getActivated()).isFalse();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "callersuperadmin", roles = "SUPER_ADMIN")
        void deleteUser_deleteAdminBySuperAdmin_forbidden() throws Exception {
            userUtilService.addSuperAdmin(TEST_PREFIX + "caller");

            // Create an admin user
            userUtilService.addAdmin(TEST_PREFIX + "adminuser10");
            User adminUser = userUtilService.getUserByLogin(TEST_PREFIX + "adminuser10admin");

            mockMvc.perform(delete("/api/account/admin/users/" + adminUser.getLogin()).contentType(MediaType.APPLICATION_JSON).content("{\"impactFingerprint\":\"irrelevant\"}"))
                    .andExpect(status().isForbidden());

            assertThat(userTestRepository.findById(adminUser.getId())).isPresent();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "callersuperadmin", roles = "SUPER_ADMIN")
        void deleteUsers_deleteAdminsBySuperAdmin_forbidden() throws Exception {
            // Create the calling user (super admin) in the database - required for batch delete
            userUtilService.addSuperAdmin(TEST_PREFIX + "caller");

            // Create admin users to be deleted
            userUtilService.addAdmin(TEST_PREFIX + "adminuser11");
            User adminUser1 = userUtilService.getUserByLogin(TEST_PREFIX + "adminuser11admin");

            userUtilService.addAdmin(TEST_PREFIX + "adminuser12");
            User adminUser2 = userUtilService.getUserByLogin(TEST_PREFIX + "adminuser12admin");

            mockMvc.perform(delete("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("users",
                            List.of(Map.of("login", adminUser1.getLogin(), "impactFingerprint", "irrelevant"),
                                    Map.of("login", adminUser2.getLogin(), "impactFingerprint", "irrelevant"))))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value(UserDeletionResultStatus.FORBIDDEN.name()))
                    .andExpect(jsonPath("$[1].status").value(UserDeletionResultStatus.FORBIDDEN.name()));

            assertThat(userTestRepository.findById(adminUser1.getId())).isPresent();
            assertThat(userTestRepository.findById(adminUser2.getId())).isPresent();
        }
    }

    @Nested
    class DefaultAdminProtection {

        // The default admin username is configured in application-artemis.yml as "artemis_admin"
        private static final String DEFAULT_ADMIN_USERNAME = "artemis_admin";

        @BeforeEach
        void ensureDefaultAdminExists() {
            // This uses the same logic as the server startup to maintain consistency
            userService.ensureInternalAdminExists(DEFAULT_ADMIN_USERNAME, DEFAULT_ADMIN_USERNAME);
        }

        @Test
        @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
        void updateUser_removeSuperAdminFromDefaultAdmin_badRequest() throws Exception {
            // Get the default admin user (created by UserService.applicationReady())
            User defaultAdmin = userTestRepository.findOneWithAuthoritiesByLogin(DEFAULT_ADMIN_USERNAME).orElseThrow();

            // Verify the default admin has super admin authority
            assertThat(defaultAdmin.getAuthorities()).extracting(Authority::getName).contains(Authority.SUPER_ADMIN_AUTHORITY.getName());

            // Try to remove super admin rights from the default admin
            ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(defaultAdmin.getLogin());
            managedUserVM.setId(defaultAdmin.getId());
            managedUserVM.setAuthorities(Set.of(Role.STUDENT.getAuthority())); // Remove super admin, keep only student

            mockMvc.perform(put("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isBadRequest());

            // Verify the default admin still has super admin authority
            User unchangedAdmin = userTestRepository.findByIdWithAuthoritiesElseThrow(defaultAdmin.getId());
            assertThat(unchangedAdmin.getAuthorities()).extracting(Authority::getName).contains(Authority.SUPER_ADMIN_AUTHORITY.getName());
        }

        @Test
        @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
        void updateUser_updateDefaultAdminKeepingSuperAdmin_success() throws Exception {
            // Get the default admin user
            User defaultAdmin = userTestRepository.findOneWithAuthoritiesByLogin(DEFAULT_ADMIN_USERNAME).orElseThrow();

            // Update the default admin while keeping super admin rights
            ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(defaultAdmin.getLogin());
            managedUserVM.setId(defaultAdmin.getId());
            managedUserVM.setFirstName("UpdatedDefaultAdmin");
            managedUserVM.setAuthorities(Set.of(Authority.SUPER_ADMIN_AUTHORITY.getName(), Role.STUDENT.getAuthority()));

            mockMvc.perform(put("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isOk());

            // Verify the update was applied and super admin authority is retained
            User updatedAdmin = userTestRepository.findByIdWithAuthoritiesElseThrow(defaultAdmin.getId());
            assertThat(updatedAdmin.getFirstName()).isEqualTo("UpdatedDefaultAdmin");
            assertThat(updatedAdmin.getAuthorities()).extracting(Authority::getName).contains(Authority.SUPER_ADMIN_AUTHORITY.getName());
        }
    }

    @Nested
    class TestUserFlag {

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void updateUser_togglesTestUserFlagAndExposesItOnTheDTO() throws Exception {
            User user = userUtilService.createAndSaveUser(TEST_PREFIX + "flaguser");
            assertThat(user.isTestUser()).as("a freshly created user is not a test user").isFalse();

            // set the flag via the admin user-management form
            ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(user.getLogin());
            managedUserVM.setId(user.getId());
            managedUserVM.setTestUser(true);
            mockMvc.perform(put("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isOk());
            assertThat(userTestRepository.findOneByLogin(user.getLogin()).orElseThrow().isTestUser()).as("the flag is persisted").isTrue();

            // the flag is readable again, under the wire name the client uses
            String body = mockMvc.perform(get("/api/account/admin/users/" + user.getLogin())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(objectMapper.readTree(body).path("isTestUser").asBoolean()).as("serialized as isTestUser, matching StudentDTO and the client model").isTrue();

            // and it can be cleared again
            managedUserVM.setTestUser(false);
            mockMvc.perform(put("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isOk());
            assertThat(userTestRepository.findOneByLogin(user.getLogin()).orElseThrow().isTestUser()).as("the flag is cleared").isFalse();
        }
    }

    /**
     * The admin edit form reaches the same transitions as the dedicated deactivate endpoint and a password reset do, but
     * it writes the fields itself. Without the timestamp, a session established earlier keeps passing the credential-change
     * checkpoint and is extended for the rest of its lifetime, so an account that the admin sees as deactivated stays
     * usable.
     */
    @Nested
    class CredentialsChangedDateOnAdminUpdate {

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void updateUser_recordsTheCredentialChangeWhenDeactivating() throws Exception {
            User user = userUtilService.createAndSaveUser(TEST_PREFIX + "deactivated");
            assertThat(userActivityService.findCredentialsChangedDate(user.getId())).isNull();

            ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(user.getLogin());
            managedUserVM.setId(user.getId());
            managedUserVM.setActivated(false);
            managedUserVM.setPassword(null);
            mockMvc.perform(put("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isOk());

            User updated = userTestRepository.findOneByLogin(user.getLogin()).orElseThrow();
            assertThat(updated.getActivated()).isFalse();
            assertThat(userActivityService.findCredentialsChangedDate(updated.getId())).as("deactivating through the admin form has to end existing sessions too").isNotNull();
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void updateUser_recordsTheCredentialChangeWhenResettingThePassword() throws Exception {
            User user = userUtilService.createAndSaveUser(TEST_PREFIX + "newpassword");
            assertThat(userActivityService.findCredentialsChangedDate(user.getId())).isNull();

            ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(user.getLogin());
            managedUserVM.setId(user.getId());
            // #13492 made the internal flag caller-controlled, and only an internal account receives the password: an
            // update that leaves it external ignores the password, so this has to say what it means.
            managedUserVM.setInternal(true);
            managedUserVM.setPassword("a-new-admin-set-password");
            mockMvc.perform(put("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isOk());

            assertThat(userActivityService.findCredentialsChangedDate(user.getId())).as("a password set by an admin has to end sessions established before it").isNotNull();
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void updateUser_leavesTheCredentialChangeAloneForAnUnrelatedEdit() throws Exception {
            User user = userUtilService.createAndSaveUser(TEST_PREFIX + "renamed");

            ManagedUserVM managedUserVM = userUtilService.createManagedUserVM(user.getLogin());
            managedUserVM.setId(user.getId());
            managedUserVM.setPassword(null);
            managedUserVM.setFirstName("Renamed");
            mockMvc.perform(put("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVM)))
                    .andExpect(status().isOk());

            assertThat(userActivityService.findCredentialsChangedDate(user.getId())).as("editing a name is not a credential change and must not log the user out").isNull();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "staleactoradmin", roles = "ADMIN")
    void permanentDeletionRejectsAnUnconfirmedImpact() throws Exception {
        userUtilService.addAdmin(TEST_PREFIX + "staleactor");
        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "staleimpact");
        String initialImpactResponse = mockMvc.perform(get("/api/account/admin/users/" + user.getLogin() + "/deletion-impact")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        var initialImpact = objectMapper.readTree(initialImpactResponse);
        String initialFingerprint = initialImpact.path("impactFingerprint").asText();
        assertThat(initialFingerprint).isNotBlank();
        assertThat(initialImpact.path("automaticEligible").asBoolean()).isTrue();
        assertThat(initialImpact.path("retentionOverrideRequired").asBoolean()).isFalse();

        var course = courseUtilService.addEmptyCourse();
        userUtilService.enrollUserInCourse(user, course, CourseRole.STUDENT);

        String changedImpactResponse = mockMvc.perform(get("/api/account/admin/users/" + user.getLogin() + "/deletion-impact")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        var changedImpact = objectMapper.readTree(changedImpactResponse);
        assertThat(changedImpact.path("impactFingerprint").asText()).isNotEqualTo(initialFingerprint);
        assertThat(changedImpact.path("automaticEligible").asBoolean()).isFalse();
        assertThat(changedImpact.path("retentionOverrideRequired").asBoolean()).isTrue();
        assertThat(changedImpact.path("categories")).anySatisfy(category -> {
            assertThat(category.path("category").asText()).isEqualTo("COURSE_MEMBERSHIP");
            assertThat(category.path("action").asText()).isEqualTo("REMOVE_MEMBERSHIP");
            assertThat(category.path("count").asLong()).isOne();
        });

        String deletionResponse = mockMvc
                .perform(delete("/api/account/admin/users/" + user.getLogin()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("impactFingerprint", initialFingerprint))))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        var deletionResult = objectMapper.readTree(deletionResponse);

        assertThat(deletionResult.path("status").asText()).isEqualTo("PLAN_CHANGED");
        assertThat(deletionResult.path("reason").asText()).isEqualTo("impactChanged");
        assertThat(userTestRepository.findById(user.getId())).isPresent();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_course_role WHERE user_id = ? AND course_id = ?", Long.class, user.getId(), course.getId())).isOne();
    }

    @Test
    void deletionFingerprintChangesWhenTargetAuthoritiesChange() {
        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "authorityfingerprint");
        String originalFingerprint = userDeletionPlanService.createImpact(user, UserDeletionMode.ADMIN_FORCED).impactFingerprint();

        user.setAuthorities(Set.of(Authority.USER_AUTHORITY, Authority.ADMIN_AUTHORITY));
        String promotedFingerprint = userDeletionPlanService.createImpact(user, UserDeletionMode.ADMIN_FORCED).impactFingerprint();

        assertThat(promotedFingerprint).isNotEqualTo(originalFingerprint);
    }

    @Test
    void automaticDeletionRevalidatesCourseEnrollment() {
        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "retainedcourse");
        userActivityService.recordDeletionWarning(user.getLogin(), Instant.now().minusSeconds(60));
        var course = courseUtilService.addEmptyCourse();
        userUtilService.enrollUserInCourse(user, course, CourseRole.STUDENT);

        var result = permanentUserDeletionService.deleteAutomatically(user.getId(), Instant.now());

        assertThat(result.status()).isEqualTo(UserDeletionResultStatus.BLOCKED);
        assertThat(result.reason()).isEqualTo("noLongerEligible");
        assertThat(userTestRepository.findById(user.getId())).isPresent();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_course_role WHERE user_id = ? AND course_id = ?", Long.class, user.getId(), course.getId())).isOne();
    }

    @Test
    void automaticDeletionLeavesWarnedUsersWithDomainReferencesUnchanged() {
        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "automaticblocked");
        User teammate = userUtilService.createAndSaveUser(TEST_PREFIX + "automaticteammate");
        User owner = userUtilService.createAndSaveUser(TEST_PREFIX + "automaticowner");
        var course = courseUtilService.addEmptyCourse();
        var exercise = textExerciseUtilService.createTeamTextExercise(course, null, null, null);
        var team = teamUtilService.createTeam(Set.of(user, teammate), owner, exercise, TEST_PREFIX + "automaticteam");
        userActivityService.recordDeletionWarning(user.getLogin(), Instant.now().minusSeconds(60));

        var result = permanentUserDeletionService.deleteAutomatically(user.getId(), Instant.now());

        assertThat(result.status()).isEqualTo(UserDeletionResultStatus.BLOCKED);
        assertThat(result.reason()).isEqualTo("remainingReferences");
        assertThat(userTestRepository.findById(user.getId())).isPresent();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM team_student WHERE team_id = ? AND student_id = ?", Long.class, team.getId(), user.getId())).isOne();
    }

    /**
     * A deletion closes the account before it removes anything, with these two statements: a deactivated account is
     * refused by every authentication provider, including git over HTTPS and SSH, and its course memberships are what
     * its access inside Artemis consists of.
     */
    @Test
    void closingAnAccountDeactivatesItAndRemovesItsCourseMemberships() {
        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "closedaccount");
        var course = courseUtilService.addEmptyCourse();
        userUtilService.enrollUserInCourse(user, course, CourseRole.STUDENT);
        assertThat(user.getActivated()).as("the fixture only tests anything if the account starts out usable").isTrue();

        assertThat(userTestRepository.deactivateForDeletion(user.getId())).isOne();
        assertThat(userReferenceCleanupService.resolve(UserDeletionReferencePolicy.COURSE_ROLE, user.getId())).as("the enrollment is the membership that is dropped").isOne();

        assertThat(userTestRepository.findById(user.getId()).orElseThrow().getActivated()).isFalse();
        assertThat(userReferenceCleanupService.count(UserDeletionReferencePolicy.COURSE_ROLE, List.of(user.getId()))).isEmpty();
    }

    @Test
    void legacyTombstoneWithoutDomainReferencesIsPhysicallyPurged() {
        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "legacytombstone");
        user.setDeleted(true);
        userTestRepository.saveAndFlush(user);

        var result = permanentUserDeletionService.deleteLegacyTombstone(user.getId());

        assertThat(result.status()).isEqualTo(UserDeletionResultStatus.DELETED);
        assertThat(userTestRepository.findById(user.getId())).isEmpty();
    }

    @Test
    void permanentDeletionServiceNeverDeletesAdministrators() {
        userUtilService.addAdmin(TEST_PREFIX + "protected");
        User administrator = userUtilService.getUserByLogin(TEST_PREFIX + "protectedadmin");

        assertThat(permanentUserDeletionService.deleteByAdmin(administrator.getId(), "irrelevant", "another-admin").status()).isEqualTo(UserDeletionResultStatus.FORBIDDEN);
        assertThat(permanentUserDeletionService.deleteAutomatically(administrator.getId(), Instant.now()).status()).isEqualTo(UserDeletionResultStatus.FORBIDDEN);

        administrator.setActivated(false);
        userTestRepository.saveAndFlush(administrator);
        assertThat(permanentUserDeletionService.deleteProvisional(administrator.getId()).status()).isEqualTo(UserDeletionResultStatus.FORBIDDEN);

        administrator.setDeleted(true);
        userTestRepository.saveAndFlush(administrator);
        assertThat(permanentUserDeletionService.deleteLegacyTombstone(administrator.getId()).status()).isEqualTo(UserDeletionResultStatus.FORBIDDEN);
        assertThat(userTestRepository.findById(administrator.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "bulkactoradmin", roles = "ADMIN")
    void bulkDeletionCommitsEligibleUsersAndReportsAChangedPlanIndependently() throws Exception {
        userUtilService.addAdmin(TEST_PREFIX + "bulkactor");
        User unchangedUser = userUtilService.createAndSaveUser(TEST_PREFIX + "bulkunchanged");
        User changedUser = userUtilService.createAndSaveUser(TEST_PREFIX + "bulkchanged");
        List<String> logins = List.of(unchangedUser.getLogin(), changedUser.getLogin());
        String impactResponse = mockMvc
                .perform(
                        post("/api/account/admin/users/deletion-impact").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("logins", logins))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var impacts = objectMapper.readTree(impactResponse).path("users");
        assertThat(impacts).hasSize(2);

        var course = courseUtilService.addEmptyCourse();
        userUtilService.enrollUserInCourse(changedUser, course, CourseRole.STUDENT);

        List<Map<String, String>> confirmations = new ArrayList<>();
        for (var impact : impacts) {
            confirmations.add(Map.of("login", impact.path("login").asText(), "impactFingerprint", impact.path("impactFingerprint").asText()));
        }
        String deletionResponse = mockMvc
                .perform(delete("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("users", confirmations))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var results = objectMapper.readTree(deletionResponse);

        assertThat(results).anySatisfy(result -> {
            assertThat(result.path("login").asText()).isEqualTo(unchangedUser.getLogin());
            assertThat(result.path("status").asText()).isEqualTo("DELETED");
        });
        assertThat(results).anySatisfy(result -> {
            assertThat(result.path("login").asText()).isEqualTo(changedUser.getLogin());
            assertThat(result.path("status").asText()).isEqualTo("PLAN_CHANGED");
            assertThat(result.path("reason").asText()).isEqualTo("impactChanged");
        });
        assertThat(userTestRepository.findById(unchangedUser.getId())).isEmpty();
        assertThat(userTestRepository.findById(changedUser.getId())).isPresent();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_course_role WHERE user_id = ? AND course_id = ?", Long.class, changedUser.getId(), course.getId()))
                .isOne();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "teamactoradmin", roles = "ADMIN")
    void forcedDeletionRemovesAStudentFromTheirTeamWithoutDeletingTheSharedTeam() throws Exception {
        userUtilService.addAdmin(TEST_PREFIX + "teamactor");
        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "teamstudent");
        User teammate = userUtilService.createAndSaveUser(TEST_PREFIX + "teammate");
        User owner = userUtilService.createAndSaveUser(TEST_PREFIX + "teamowner");
        var course = courseUtilService.addEmptyCourse();
        var exercise = textExerciseUtilService.createTeamTextExercise(course, null, null, null);
        var team = teamUtilService.createTeam(Set.of(user, teammate), owner, exercise, TEST_PREFIX + "retainedteam");

        permanentlyDeleteUser(user.getLogin());

        assertThat(userTestRepository.findById(user.getId())).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM team WHERE id = ?", Long.class, team.getId())).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM team_student WHERE team_id = ? AND student_id = ?", Long.class, team.getId(), user.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM team_student WHERE team_id = ? AND student_id = ?", Long.class, team.getId(), teammate.getId())).isOne();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "teamowneractoradmin", roles = "ADMIN")
    void forcedDeletionDoesNotDeleteAnotherUsersTeamWhenTheTargetIsItsOnlyStudent() throws Exception {
        userUtilService.addAdmin(TEST_PREFIX + "teamowneractor");
        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "onlyteamstudent");
        User owner = userUtilService.createAndSaveUser(TEST_PREFIX + "otherteamowner");
        var course = courseUtilService.addEmptyCourse();
        var exercise = textExerciseUtilService.createTeamTextExercise(course, null, null, null);
        var team = teamUtilService.createTeam(Set.of(user), owner, exercise, TEST_PREFIX + "otherownedteam");

        permanentlyDeleteUser(user.getLogin());

        assertThat(userTestRepository.findById(user.getId())).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM team WHERE id = ? AND owner_id = ?", Long.class, team.getId(), owner.getId())).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM team_student WHERE team_id = ?", Long.class, team.getId())).isZero();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "versionactoradmin", roles = "ADMIN")
    void forcedDeletionDeletesExerciseAndSubmissionVersionsAuthoredByTheUser() throws Exception {
        userUtilService.addAdmin(TEST_PREFIX + "versionactor");
        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "versionauthor");
        User submissionOwner = userUtilService.createAndSaveUser(TEST_PREFIX + "submissionowner");
        var course = courseUtilService.addEmptyCourse();
        var exercise = textExerciseUtilService.createIndividualTextExercise(course, null, null, null);

        exerciseVersionService.createExerciseVersion(exercise, user);
        Long exerciseVersionId = jdbcTemplate.queryForObject("SELECT id FROM exercise_version WHERE exercise_id = ? AND author_id = ?", Long.class, exercise.getId(), user.getId());
        var submission = textExerciseUtilService.createSubmissionForTextExercise(exercise, submissionOwner, "submission");
        var submissionVersion = submissionVersionRepository.save(ParticipationFactory.generateSubmissionVersion("version", submission, user));

        permanentlyDeleteUser(user.getLogin());

        assertThat(userTestRepository.findById(user.getId())).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM exercise_version WHERE id = ?", Long.class, exerciseVersionId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM submission_version WHERE id = ?", Long.class, submissionVersion.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM exercise WHERE id = ?", Long.class, exercise.getId())).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM submission WHERE id = ?", Long.class, submission.getId())).isOne();
    }

    private void permanentlyDeleteUser(String login) throws Exception {
        String impactResponse = mockMvc.perform(get("/api/account/admin/users/" + login + "/deletion-impact")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        String fingerprint = objectMapper.readTree(impactResponse).path("impactFingerprint").asText();
        mockMvc.perform(delete("/api/account/admin/users/" + login).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("impactFingerprint", fingerprint)))).andExpect(status().isOk());
    }

    @Nested
    class EmailUpdates {

        /**
         * Re-sending the address the account already has, in a different case, is not a change: {@code canonicalEmail}
         * folds it to the stored value. The uniqueness check must not read that as taking an address from someone else.
         */
        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void updateUserAllowsItsOwnEmailInADifferentCase() throws Exception {
            String email = TEST_PREFIX + "own-address@test.de";
            User user = userUtilService.createAndSaveUser(TEST_PREFIX + "own-address");
            user.setEmail(email);
            user = userTestRepository.save(user);

            ManagedUserVM update = userUtilService.createManagedUserVM(user.getLogin());
            update.setId(user.getId());
            update.setEmail(email.toUpperCase(Locale.ROOT));
            update.setFirstName("Updated");

            mockMvc.perform(put("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(update))).andExpect(status().isOk());

            User updated = userTestRepository.findById(user.getId()).orElseThrow();
            assertThat(updated.getFirstName()).isEqualTo("Updated");
            assertThat(updated.getEmail()).isEqualTo(email);
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void updateUserRejectsAnEmailHeldByAnotherAccount() throws Exception {
            User holder = userUtilService.createAndSaveUser(TEST_PREFIX + "address-holder");
            holder.setEmail(TEST_PREFIX + "taken-address@test.de");
            userTestRepository.save(holder);
            User user = userUtilService.createAndSaveUser(TEST_PREFIX + "address-taker");

            ManagedUserVM update = userUtilService.createManagedUserVM(user.getLogin());
            update.setId(user.getId());
            update.setEmail(holder.getEmail().toUpperCase(Locale.ROOT));

            mockMvc.perform(put("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(update)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void updateUserAllowsRemovingAnEmail() throws Exception {
            User user = userUtilService.createAndSaveUser(TEST_PREFIX + "remove-email");
            ManagedUserVM update = userUtilService.createManagedUserVM(user.getLogin());
            update.setId(user.getId());
            update.setEmail(null);

            mockMvc.perform(put("/api/account/admin/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(update))).andExpect(status().isOk());

            assertThat(userTestRepository.findById(user.getId()).orElseThrow().getEmail()).isNull();
        }
    }
}
