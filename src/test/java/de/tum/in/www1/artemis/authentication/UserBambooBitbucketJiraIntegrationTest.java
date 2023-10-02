package de.tum.in.www1.artemis.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.user.UserTestService;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;

class UserBambooBitbucketJiraIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "userbamb"; // shorter prefix as user's name is limited to 50 chars

    @Autowired
    private UserTestService userTestService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordService passwordService;

    @BeforeEach
    void setUp() throws Exception {
        userTestService.setup(TEST_PREFIX, this);
        jiraRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void teardown() throws IOException {
        userRepository.deleteAll(userRepository.searchAllByLoginOrName(Pageable.unpaged(), TEST_PREFIX));
        bitbucketRequestMockProvider.reset();
        userTestService.tearDown();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateUser_asAdmin_isSuccessful() throws Exception {
        userTestService.updateUser_asAdmin_isSuccessful();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateUserEmptyRoles() throws Exception {
        userTestService.updateUserWithEmptyRoles();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateUserInvalidId() throws Exception {
        userTestService.updateUserInvalidId();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateUserExistingEmail() throws Exception {
        userTestService.updateUserExistingEmail();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateUser_withNullPassword_oldPasswordNotChanged() throws Exception {
        bitbucketRequestMockProvider.mockUpdateUserDetails(userTestService.student.getLogin(), userTestService.student.getEmail(), userTestService.student.getName());
        userTestService.updateUser_withNullPassword_oldPasswordNotChanged();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateUser_asInstructor_forbidden() throws Exception {
        request.put("/api/admin/users", new ManagedUserVM(userTestService.getStudent()), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void updateUser_asTutor_forbidden() throws Exception {
        request.put("/api/admin/users", new ManagedUserVM(userTestService.getStudent()), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateUser_withExternalUserManagement_vcsManagementHasNotBeenCalled() throws Exception {
        bitbucketRequestMockProvider.mockUpdateUserDetails(userTestService.student.getLogin(), userTestService.student.getEmail(),
                "changed " + userTestService.student.getLastName());
        userTestService.updateUser_withExternalUserManagement();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateUser_withVcsUserNotExisting_isSuccessful() throws Exception {
        var student = userTestService.student;
        student.setInternal(true);
        student = userRepository.save(student);
        student.setFirstName("changed");
        student.getGroups().forEach(groupName -> jiraRequestMockProvider.mockIsGroupAvailable(groupName));
        bitbucketRequestMockProvider.mockUpdateUserDetails(student.getLogin(), student.getEmail(), student.getName(), false);
        bitbucketRequestMockProvider.mockUpdateUserPassword(student.getLogin(), "newPassword", true, false);

        request.put("/api/admin/users", new ManagedUserVM(student, "newPassword"), HttpStatus.OK);

        var updatedStudent = userRepository.getUserByLoginElseThrow(student.getLogin());

        assertThat(updatedStudent.getFirstName()).isEqualTo(student.getFirstName());
        assertThat(updatedStudent.getPassword()).isNotEqualTo(student.getPassword());
        assertThat(passwordService.checkPasswordMatch("newPassword", updatedStudent.getPassword())).isTrue();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createExternalUser_asAdmin_isSuccessful() throws Exception {
        bitbucketRequestMockProvider.mockUserExists("batman");
        userTestService.createExternalUser_asAdmin_isSuccessful();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createInternalUser_asAdmin_isSuccessful() throws Exception {
        bitbucketRequestMockProvider.mockUserDoesNotExist("batman");
        bitbucketRequestMockProvider.mockCreateUser("batman", "foobar1234", "batman@secret.invalid", TEST_PREFIX + "student1First " + TEST_PREFIX + "student1Last");
        bitbucketRequestMockProvider.mockAddUserToGroups();
        userTestService.createInternalUser_asAdmin_isSuccessful();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createInternalUserWithoutRoles_asAdmin_isSuccessful() throws Exception {
        bitbucketRequestMockProvider.mockUserDoesNotExist("batman");
        bitbucketRequestMockProvider.mockCreateUser("batman", "foobar1234", "batman@secret.invalid", TEST_PREFIX + "student1First " + TEST_PREFIX + "student1Last");
        bitbucketRequestMockProvider.mockAddUserToGroups();
        userTestService.createInternalUserWithoutRoles_asAdmin_isSuccessful();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createUser_asAdmin_hasId() throws Exception {
        userTestService.createUser_asAdmin_hasId();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createUser_asAdmin_existingLogin() throws Exception {
        bitbucketRequestMockProvider.mockUserExists("batman");
        userTestService.createUser_asAdmin_existingLogin();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createUser_asAdmin_existingEmail() throws Exception {
        userTestService.createUser_asAdmin_existingEmail();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createUser_withNullAsPassword_generatesRandomPassword() throws Exception {
        userRepository.findOneByLogin("batman").ifPresent(userRepository::delete);
        bitbucketRequestMockProvider.mockUserExists("batman");
        userTestService.createUser_withNullAsPassword_generatesRandomPassword();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createUser_withExternalUserManagement_vcsManagementHasNotBeenCalled() throws Exception {
        bitbucketRequestMockProvider.mockUserExists("batman");
        userTestService.createUser_withExternalUserManagement();
    }

    /**
     * Tests if the deletion of a user by admin succeeds
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deleteUser_isSuccessful() throws Exception {
        bitbucketRequestMockProvider.mockUpdateAnyUserDetails(false, 1);
        bitbucketRequestMockProvider.mockUpdateAnyUserPassword(false, 1);
        bitbucketRequestMockProvider.mockRemoveAnyUserFromAnyGroups();
        jiraRequestMockProvider.mockRemoveAnyUserFromAnyGroups();
        userTestService.deleteUser_isSuccessful();
    }

    /**
     * Tests if the deletion of the current user by themselves fails.
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deleteSelf_isNotSuccessful() throws Exception {
        userTestService.deleteSelf_isNotSuccessful("admin");
    }

    /**
     * Tests if attempting to delete a number of users, including the current user works as expected.
     * The expected behavior is the deletion of all users except the current user.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "ADMIN")
    void deleteUsers_isSuccessfulForAllUsersExceptSelf() throws Exception {
        bitbucketRequestMockProvider.mockUpdateAnyUserDetails(false, 4);
        bitbucketRequestMockProvider.mockUpdateAnyUserPassword(false, 4);
        bitbucketRequestMockProvider.mockRemoveAnyUserFromAnyGroups();
        jiraRequestMockProvider.mockRemoveAnyUserFromAnyGroups();
        userTestService.deleteUsers(TEST_PREFIX + "tutor1");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getUsers_asAdmin_isSuccessful() throws Exception {
        userTestService.getUsers_asAdmin_isSuccessful();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void searchUsers_asInstructor_isSuccessful() throws Exception {
        userTestService.searchUsers_asInstructor_isSuccessful();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void searchUsers_asAdmin_badRequest() throws Exception {
        userTestService.searchUsers_asAdmin_badRequest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void searchUsers_asTutor_forbidden() throws Exception {
        userTestService.searchUsers_asTutor_forbidden();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getUserViaFilter_asAdmin_isSuccessful() throws Exception {
        userTestService.getUserViaFilter_asAdmin_isSuccessful();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getAuthorities_asAdmin_isSuccessful() throws Exception {
        userTestService.getAuthorities_asAdmin_isSuccessful();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getUsersOrAuthorities_asInstructor_forbidden() throws Exception {
        userTestService.getUsersOrAuthorities_asInstructor_forbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getUsersOrAuthorities_asTutor_forbidden() throws Exception {
        userTestService.getUsersOrAuthorities_asTutor_forbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getUsersOrAuthorities_asStudent_forbidden() throws Exception {
        userTestService.getUsersOrAuthorities_asStudent_forbidden();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getUser_asAdmin_isSuccessful() throws Exception {
        userTestService.getUser_asAdmin_isSuccessful();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void updateUserNotificationDate_asStudent_isSuccessful() throws Exception {
        userTestService.updateUserNotificationDate_asStudent_isSuccessful();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void updateUserNotificationVisibility_showAll_asStudent_isSuccessful() throws Exception {
        userTestService.updateUserNotificationVisibilityShowAllAsStudentIsSuccessful();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void updateUserNotificationVisibility_hideUntil_asStudent_isSuccessful() throws Exception {
        userTestService.updateUserNotificationVisibilityHideUntilAsStudentIsSuccessful();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void initializeUser() throws Exception {
        bitbucketRequestMockProvider.mockUserExists(userTestService.student.getLogin());
        bitbucketRequestMockProvider.mockUpdateUserDetails(userTestService.student.getLogin(), userTestService.student.getEmail(), userTestService.student.getName());
        bitbucketRequestMockProvider.mockUpdateUserPassword(userTestService.student.getLogin(), "ThisIsAPassword", false, true);
        userTestService.initializeUser(false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void initializeUserWithoutFlag() throws Exception {
        userTestService.initializeUserWithoutFlag();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void initializeUserNonLTI() throws Exception {
        userTestService.initializeUserNonLTI();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void initializeUserExternal() throws Exception {
        userTestService.initializeUserExternal();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUserWithoutGroups() throws Exception {
        userTestService.testUserWithoutGroups();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUserWithGroups() throws Exception {
        userTestService.testUserWithGroups();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUserWithActivatedStatus() throws Exception {
        userTestService.testUserWithActivatedStatus();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUserWithDeactivatedStatus() throws Exception {
        userTestService.testUserWithDeactivatedStatus();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUserWithInternalStatus() throws Exception {
        userTestService.testUserWithInternalStatus();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUserWithExternalStatus() throws Exception {
        userTestService.testUserWithExternalStatus();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUserWithExternalAndInternalStatus() throws Exception {
        userTestService.testUserWithExternalAndInternalStatus();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUserWithRegistrationNumber() throws Exception {
        userTestService.testUserWithRegistrationNumber();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUserWithoutRegistrationNumber() throws Exception {
        userTestService.testUserWithoutRegistrationNumber();
    }
}
