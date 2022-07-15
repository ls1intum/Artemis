package de.tum.in.www1.artemis.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.connectors.gitlab.GitLabUserManagementService;
import de.tum.in.www1.artemis.service.connectors.jenkins.JenkinsUserManagementService;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.UserTestService;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;

public class UserJenkinsGitlabIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Value("${artemis.continuous-integration.user}")
    private String jenkinsAdminUsername;

    @Value("${jenkins.use-pseudonyms:#{false}}")
    private boolean usePseudonymsJenkins;

    @Value("${gitlab.use-pseudonyms:#{false}}")
    private boolean usePseudonymsGitlab;

    @Autowired
    private UserTestService userTestService;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JenkinsUserManagementService jenkinsUserManagementService;

    @Autowired
    private GitLabUserManagementService gitLabUserManagementService;

    @BeforeEach
    public void setUp() throws Exception {
        userTestService.setup(this);
        gitlabRequestMockProvider.enableMockingOfRequests();
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
    }

    @AfterEach
    public void teardown() throws IOException {
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
    public void createUserWithPseudonymsIsSuccessful() throws Exception {
        ReflectionTestUtils.setField(jenkinsUserManagementService, "usePseudonyms", true);
        ReflectionTestUtils.setField(gitLabUserManagementService, "usePseudonyms", true);
        userTestService.createExternalUser_asAdmin_isSuccessful();
        ReflectionTestUtils.setField(jenkinsUserManagementService, "usePseudonyms", usePseudonymsJenkins);
        ReflectionTestUtils.setField(gitLabUserManagementService, "usePseudonyms", usePseudonymsGitlab);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void createAdminUserSkippedInJenkins() throws Exception {
        ReflectionTestUtils.setField(jenkinsUserManagementService, "jenkinsAdminUsername", "batman");
        userTestService.createExternalUser_asAdmin_isSuccessful();
        ReflectionTestUtils.setField(jenkinsUserManagementService, "jenkinsAdminUsername", jenkinsAdminUsername);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void createAdminInternalUserSkippedInJenkins() throws Exception {
        ReflectionTestUtils.setField(jenkinsUserManagementService, "jenkinsAdminUsername", "batman");
        userTestService.createInternalUser_asAdmin_isSuccessful();
        ReflectionTestUtils.setField(jenkinsUserManagementService, "jenkinsAdminUsername", jenkinsAdminUsername);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void deleteAdminUserSkippedInJenkins() throws Exception {
        ReflectionTestUtils.setField(jenkinsUserManagementService, "jenkinsAdminUsername", "student1");
        userTestService.deleteUser_isSuccessful();
        ReflectionTestUtils.setField(jenkinsUserManagementService, "jenkinsAdminUsername", jenkinsAdminUsername);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void updateAdminUserSkippedInJenkins() throws Exception {
        ReflectionTestUtils.setField(jenkinsUserManagementService, "jenkinsAdminUsername", "student1");
        userTestService.updateUser_asAdmin_isSuccessful();
        ReflectionTestUtils.setField(jenkinsUserManagementService, "jenkinsAdminUsername", jenkinsAdminUsername);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void createUser_asAdmin_isSuccessful() throws Exception {
        userTestService.createExternalUser_asAdmin_isSuccessful();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void createInternalUser_asAdmin_with_vcsAccessToken_isSuccessful() throws Exception {
        gitlabRequestMockProvider.mockCreationOfUser("batman");
        ReflectionTestUtils.setField(gitLabUserManagementService, "versionControlAccessToken", true);
        userTestService.createInternalUser_asAdmin_isSuccessful();
        ReflectionTestUtils.setField(gitLabUserManagementService, "versionControlAccessToken", false);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void createUser_asAdmin_hasId() throws Exception {
        userTestService.createUser_asAdmin_hasId();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void createUser_asAdmin_existingLogin() throws Exception {
        userTestService.createUser_asAdmin_existingLogin();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void createUser_asAdmin_existingEmail() throws Exception {
        userTestService.createUser_asAdmin_existingEmail();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void createUser_asAdmin_existsInCi_internalError() throws Exception {
        userTestService.createUser_asAdmin_existsInCi_internalError();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void createUser_asAdmin_illegalLogin_internalError() throws Exception {
        userTestService.createUser_asAdmin_illegalLogin_internalError();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void createUser_asAdmin_failInExternalUserManagement_internalError() throws Exception {
        userTestService.createUser_asAdmin_failInExternalCiUserManagement_internalError();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void createUser_asAdmin_failInExternalCiUserManagement_cannotGetCiUser_internalError() throws Exception {
        userTestService.createUser_asAdmin_failInExternalCiUserManagement_cannotGetCiUser_internalError();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void createUser_asAdmin_failInExternalVcsUserManagement_internalError() throws Exception {
        userTestService.createUser_asAdmin_failInExternalVcsUserManagement_internalError();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void createUser_withNullAsPassword_generatesRandomPassword() throws Exception {
        userTestService.createUser_withNullAsPassword_generatesRandomPassword();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void deleteUser_isSuccessful() throws Exception {
        userTestService.deleteUser_isSuccessful();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void deleteUser_failToGetUserIdInGitlab() throws Exception {
        User student = userTestService.student;
        gitlabRequestMockProvider.mockDeleteVcsUserFailToGetUserId(student.getLogin());
        request.delete("/api/users/" + student.getLogin(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void deleteUser_doesntExistInUserManagement_isSuccessful() throws Exception {
        userTestService.deleteUser_doesntExistInUserManagement_isSuccessful();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void deleteUser_FailsInExternalCiUserManagement_isNotSuccessful() throws Exception {
        userTestService.deleteUser_FailsInExternalCiUserManagement_isNotSuccessful();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void deleteUser_FailsInExternalVcsUserManagement_isNotSuccessful() throws Exception {
        userTestService.deleteUser_FailsInExternalVcsUserManagement_isNotSuccessful();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void deleteUsers() throws Exception {
        userTestService.deleteUsers();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void deleteUsersException() throws Exception {
        userTestService.deleteUsersException();
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
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void createUserWithGroups() throws Exception {
        userTestService.createUserWithGroups();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void createUserWithGroupsAlreadyExistsInGitlab() throws Exception {
        Course course = database.addEmptyCourse();
        ProgrammingExercise programmingExercise = database.addProgrammingExerciseToCourse(course, false);

        User newUser = userTestService.student;
        newUser.setId(null);
        newUser.setLogin("batman");
        newUser.setEmail("foobar@tum.com");
        newUser.setGroups(Set.of("tutor", "instructor"));

        gitlabRequestMockProvider.mockAddUserToGroupsUserExists(newUser, programmingExercise.getProjectKey());
        jenkinsRequestMockProvider.mockCreateUser(newUser, false, false, false);
        request.post("/api/users", new ManagedUserVM(newUser), HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void createUserWithGroupsAlreadyFailsInGitlab() throws Exception {
        Course course = database.addEmptyCourse();
        ProgrammingExercise programmingExercise = database.addProgrammingExerciseToCourse(course, false);

        User newUser = userTestService.student;
        newUser.setId(null);
        newUser.setLogin("batman");
        newUser.setEmail("foobar@tum.com");
        newUser.setGroups(Set.of("tutor", "instructor2"));

        gitlabRequestMockProvider.mockAddUserToGroupsFails(newUser, programmingExercise.getProjectKey());
        request.post("/api/users", new ManagedUserVM(newUser), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void updateUserGroups() throws Exception {
        userTestService.student.setPassword(passwordService.hashPassword("this is a password"));
        userRepository.save(userTestService.student);
        userTestService.updateUserGroups();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void updateUserLogin() throws Exception {
        userTestService.updateUserLogin();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void shouldFailIfCannotUpdateActivatedUserInGitlab() throws Exception {
        String oldLogin = userTestService.student.getLogin();
        User user = userTestService.student;
        user.setLogin("new-login");

        jenkinsRequestMockProvider.mockUpdateUserAndGroups(oldLogin, user, user.getGroups(), Set.of(), true);
        gitlabRequestMockProvider.mockUpdateVcsUserFailToActivate(oldLogin, user);
        request.put("/api/users", new ManagedUserVM(user, "some-new-password"), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void shouldFailIfCannotUpdateDeactivatedUserInGitlab() throws Exception {
        // create unactivated user in repo
        User user = ModelFactory.generateActivatedUser("ab123cd");
        user.setActivated(false);
        user.setActivationKey("testActivationKey");

        // Register the user
        ManagedUserVM userVM = new ManagedUserVM(user);
        userVM.setPassword("password");
        gitlabRequestMockProvider.mockCreateVcsUser(user, false);
        gitlabRequestMockProvider.mockDeactivateUser(user.getLogin(), false);
        request.postWithoutLocation("/api/register", userVM, HttpStatus.CREATED, null);

        Optional<User> registeredUser = userTestService.getUserRepository().findOneWithGroupsAndAuthoritiesByLogin(user.getLogin());
        assertThat(registeredUser).isPresent();

        // Update user and assert
        String oldLogin = user.getLogin();
        user = registeredUser.get();
        user.setLogin("some-new-login");

        jenkinsRequestMockProvider.mockUpdateUserAndGroups(oldLogin, user, user.getGroups(), Set.of(), true);
        gitlabRequestMockProvider.mockUpdateVcsUserFailToActivate(oldLogin, user);
        request.put("/api/users", new ManagedUserVM(user, "some-new-password"), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void shouldBlockUserInGitlabIfAccountNotActivated() throws Exception {
        String password = "this is a password";
        userTestService.student.setPassword(passwordService.hashPassword(password));
        userRepository.save(userTestService.student);
        var oldLogin = userTestService.student.getLogin();
        User user = userTestService.student;
        user.setLogin("new-login");
        user.setActivated(false);

        jenkinsRequestMockProvider.mockUpdateUserAndGroups(oldLogin, user, user.getGroups(), Set.of(), true);
        gitlabRequestMockProvider.mockUpdateVcsUser(oldLogin, user, Set.of(), user.getGroups(), true);

        request.put("/api/users", new ManagedUserVM(user, password), HttpStatus.OK);

        UserRepository userRepository = userTestService.getUserRepository();
        final var userInDB = userRepository.findById(user.getId());
        assertThat(userInDB).isPresent();
        assertThat(userInDB.get().getLogin()).isEqualTo(user.getLogin());

        verify(gitlabRequestMockProvider.getMockedUserApi()).blockUser(anyLong());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void initializeUser() throws Exception {
        userTestService.initializeUser(true);
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
}
