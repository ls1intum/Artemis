package de.tum.in.www1.artemis.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.util.UserTestService;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;

public class UserBambooBitbucketJiraIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private UserTestService userTestService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordService passwordService;

    @BeforeEach
    public void setUp() throws Exception {
        userTestService.setup(this);
        jiraRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    public void teardown() throws IOException {
        bitbucketRequestMockProvider.reset();
        userTestService.tearDown();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void updateUser_asAdmin_isSuccessful() throws Exception {
        userTestService.updateUser_asAdmin_isSuccessful();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void updateUserInvalidId() throws Exception {
        userTestService.updateUserInvalidId();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void updateUserExistingEmail() throws Exception {
        userTestService.updateUserExistingEmail();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void updateUser_withNullPassword_oldPasswordNotChanged() throws Exception {
        bitbucketRequestMockProvider.mockUpdateUserDetails(userTestService.student.getLogin(), userTestService.student.getEmail(), userTestService.student.getName());
        userTestService.updateUser_withNullPassword_oldPasswordNotChanged();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateUser_asInstructor_forbidden() throws Exception {
        request.put("/api/users", new ManagedUserVM(userTestService.getStudent()), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void updateUser_asTutor_forbidden() throws Exception {
        request.put("/api/users", new ManagedUserVM(userTestService.getStudent()), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void updateUser_withExternalUserManagement_vcsManagementHasNotBeenCalled() throws Exception {
        bitbucketRequestMockProvider.mockUpdateUserDetails(userTestService.student.getLogin(), userTestService.student.getEmail(),
                "changed " + userTestService.student.getLastName());
        userTestService.updateUser_withExternalUserManagement();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void updateUser_withVcsUserNotExisting_isSuccessful() throws Exception {
        var student = userTestService.student;
        student.setInternal(true);
        student = userRepository.save(student);
        student.setFirstName("changed");
        jiraRequestMockProvider.mockIsGroupAvailable("testgroup");
        jiraRequestMockProvider.mockIsGroupAvailable("tumuser");
        bitbucketRequestMockProvider.mockUpdateUserDetails(student.getLogin(), student.getEmail(), student.getName(), false);
        bitbucketRequestMockProvider.mockUpdateUserPassword(student.getLogin(), "newPassword", true, false);

        request.put("/api/users", new ManagedUserVM(student, "newPassword"), HttpStatus.OK);

        var updatedStudent = userRepository.getUserByLoginElseThrow(student.getLogin());

        assertThat(updatedStudent.getFirstName()).isEqualTo(student.getFirstName());
        assertThat(updatedStudent.getPassword()).isNotEqualTo(student.getPassword());
        assertThat(passwordService.checkPasswordMatch("newPassword", updatedStudent.getPassword())).isTrue();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void createExternalUser_asAdmin_isSuccessful() throws Exception {
        bitbucketRequestMockProvider.mockUserExists("batman");
        userTestService.createExternalUser_asAdmin_isSuccessful();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void createInternalUser_asAdmin_isSuccessful() throws Exception {
        bitbucketRequestMockProvider.mockUserDoesNotExist("batman");
        bitbucketRequestMockProvider.mockCreateUser("batman", "foobar1234", "batman@secret.invalid", "student1First student1Last");
        bitbucketRequestMockProvider.mockAddUserToGroups();
        userTestService.createInternalUser_asAdmin_isSuccessful();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void createUser_asAdmin_hasId() throws Exception {
        userTestService.createUser_asAdmin_hasId();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void createUser_asAdmin_existingLogin() throws Exception {
        bitbucketRequestMockProvider.mockUserExists("batman");
        userTestService.createUser_asAdmin_existingLogin();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void createUser_asAdmin_existingEmail() throws Exception {
        userTestService.createUser_asAdmin_existingEmail();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void createUser_withNullAsPassword_generatesRandomPassword() throws Exception {
        bitbucketRequestMockProvider.mockUserExists("batman");
        userTestService.createUser_withNullAsPassword_generatesRandomPassword();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void createUser_withExternalUserManagement_vcsManagementHasNotBeenCalled() throws Exception {
        bitbucketRequestMockProvider.mockUserExists("batman");
        userTestService.createUser_withExternalUserManagement();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void deleteUser_withExternalUserManagement_vcsManagementHasNotBeenCalled() throws Exception {
        bitbucketRequestMockProvider.mockDeleteUser(userTestService.getStudent().getLogin(), false);
        bitbucketRequestMockProvider.mockEraseDeletedUser(userTestService.getStudent().getLogin());
        request.delete("/api/users/" + userTestService.getStudent().getLogin(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void deleteUser_isSuccessful() throws Exception {
        bitbucketRequestMockProvider.mockDeleteUser("student1", false);
        bitbucketRequestMockProvider.mockEraseDeletedUser("student1");
        userTestService.deleteUser_isSuccessful();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void deleteUser_doesntExistInUserManagement_isSuccessful() throws Exception {
        userTestService.deleteUser_doesntExistInUserManagement_isSuccessful();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void deleteUsers() throws Exception {
        userTestService.deleteUsers();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void getUsers_asAdmin_isSuccessful() throws Exception {
        userTestService.getUsers_asAdmin_isSuccessful();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void searchUsers_asInstructor_isSuccessful() throws Exception {
        userTestService.searchUsers_asInstructor_isSuccessful();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void searchUsers_asAdmin_badRequest() throws Exception {
        userTestService.searchUsers_asAdmin_badRequest();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void searchUsers_asTutor_forbidden() throws Exception {
        userTestService.searchUsers_asTutor_forbidden();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void getUserViaFilter_asAdmin_isSuccessful() throws Exception {
        userTestService.getUserViaFilter_asAdmin_isSuccessful();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void getAuthorities_asAdmin_isSuccessful() throws Exception {
        userTestService.getAuthorities_asAdmin_isSuccessful();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getUsersOrAuthorities_asInstructor_forbidden() throws Exception {
        userTestService.getUsersOrAuthorities_asInstructor_forbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getUsersOrAuthorities_asTutor_forbidden() throws Exception {
        userTestService.getUsersOrAuthorities_asTutor_forbidden();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getUsersOrAuthorities_asStudent_forbidden() throws Exception {
        userTestService.getUsersOrAuthorities_asStudent_forbidden();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void getUser_asAdmin_isSuccessful() throws Exception {
        userTestService.getUser_asAdmin_isSuccessful();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void updateUserNotificationDate_asStudent_isSuccessful() throws Exception {
        userTestService.updateUserNotificationDate_asStudent_isSuccessful();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void updateUserNotificationVisibility_showAll_asStudent_isSuccessful() throws Exception {
        userTestService.updateUserNotificationVisibilityShowAllAsStudentIsSuccessful();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void updateUserNotificationVisibility_hideUntil_asStudent_isSuccessful() throws Exception {
        userTestService.updateUserNotificationVisibilityHideUntilAsStudentIsSuccessful();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void initializeUser() throws Exception {
        bitbucketRequestMockProvider.mockUserExists(userTestService.student.getLogin());
        bitbucketRequestMockProvider.mockUpdateUserDetails(userTestService.student.getLogin(), userTestService.student.getEmail(), userTestService.student.getName());
        bitbucketRequestMockProvider.mockUpdateUserPassword(userTestService.student.getLogin(), "ThisIsAPassword", false, true);
        userTestService.initializeUser(false);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void initializeUserWithoutFlag() throws Exception {
        userTestService.initializeUserWithoutFlag();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void initializeUserNonLTI() throws Exception {
        userTestService.initializeUserNonLTI();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void initializeUserExternal() throws Exception {
        userTestService.initializeUserExternal();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testUserWithoutGroups() throws Exception {
        userTestService.testUserWithoutGroups();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testUserWithGroups() throws Exception {
        userTestService.testUserWithGroups();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testUserWithActivatedStatus() throws Exception {
        userTestService.testUserWithActivatedStatus();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testUserWithDeactivatedStatus() throws Exception {
        userTestService.testUserWithDeactivatedStatus();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testUserWithInternalStatus() throws Exception {
        userTestService.testUserWithInternalStatus();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testUserWithExternalStatus() throws Exception {
        userTestService.testUserWithExternalStatus();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testUserWithExternalAndInternalStatus() throws Exception {
        userTestService.testUserWithExternalAndInternalStatus();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testUserWithRegistrationNumber() throws Exception {
        userTestService.testUserWithRegistrationNumber();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testUserWithoutRegistrationNumber() throws Exception {
        userTestService.testUserWithoutRegistrationNumber();
    }
}
