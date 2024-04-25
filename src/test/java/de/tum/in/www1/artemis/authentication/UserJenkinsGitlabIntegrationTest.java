package de.tum.in.www1.artemis.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;

import java.util.Collections;
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
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.connectors.gitlab.GitLabPersonalAccessTokenManagementService;
import de.tum.in.www1.artemis.service.connectors.gitlab.GitLabUserManagementService;
import de.tum.in.www1.artemis.service.connectors.jenkins.JenkinsUserManagementService;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.user.UserFactory;
import de.tum.in.www1.artemis.user.UserTestService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;

class UserJenkinsGitlabIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    private static final String TEST_PREFIX = "userjenk"; // shorter prefix as user's name is limited to 50 chars

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

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private GitLabPersonalAccessTokenManagementService gitLabPersonalAccessTokenManagementService;

    @BeforeEach
    void setUp() throws Exception {
        userTestService.setup(TEST_PREFIX, this);
        gitlabRequestMockProvider.enableMockingOfRequests();
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
    }

    @AfterEach
    void teardown() throws Exception {
        jenkinsRequestMockProvider.reset();
        gitlabRequestMockProvider.reset();
        userTestService.tearDown();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateUser_asAdmin_isSuccessful() throws Exception {
        userTestService.updateUser_asAdmin_isSuccessful();
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
    void createUserWithPseudonymsIsSuccessful() throws Exception {
        ReflectionTestUtils.setField(jenkinsUserManagementService, "usePseudonyms", true);
        ReflectionTestUtils.setField(gitLabUserManagementService, "usePseudonyms", true);
        userTestService.createExternalUser_asAdmin_isSuccessful();
        ReflectionTestUtils.setField(jenkinsUserManagementService, "usePseudonyms", usePseudonymsJenkins);
        ReflectionTestUtils.setField(gitLabUserManagementService, "usePseudonyms", usePseudonymsGitlab);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createAdminUserSkippedInJenkins() throws Exception {
        ReflectionTestUtils.setField(jenkinsUserManagementService, "jenkinsAdminUsername", "batman");
        userTestService.createExternalUser_asAdmin_isSuccessful();
        ReflectionTestUtils.setField(jenkinsUserManagementService, "jenkinsAdminUsername", jenkinsAdminUsername);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createAdminInternalUserSkippedInJenkins() throws Exception {
        ReflectionTestUtils.setField(jenkinsUserManagementService, "jenkinsAdminUsername", "batman");
        userTestService.createInternalUser_asAdmin_isSuccessful();
        ReflectionTestUtils.setField(jenkinsUserManagementService, "jenkinsAdminUsername", jenkinsAdminUsername);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deleteAdminUserSkippedInJenkins() throws Exception {
        ReflectionTestUtils.setField(jenkinsUserManagementService, "jenkinsAdminUsername", TEST_PREFIX + "student1");
        jenkinsRequestMockProvider.mockGetAnyUser(false, 1);
        userTestService.deleteUser_isSuccessful();
        ReflectionTestUtils.setField(jenkinsUserManagementService, "jenkinsAdminUsername", jenkinsAdminUsername);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateAdminUserSkippedInJenkins() throws Exception {
        ReflectionTestUtils.setField(jenkinsUserManagementService, "jenkinsAdminUsername", TEST_PREFIX + "student1");
        userTestService.updateUser_asAdmin_isSuccessful();
        ReflectionTestUtils.setField(jenkinsUserManagementService, "jenkinsAdminUsername", jenkinsAdminUsername);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createUser_asAdmin_isSuccessful() throws Exception {
        userTestService.createExternalUser_asAdmin_isSuccessful();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createInternalUser_asAdmin_with_vcsAccessToken_isSuccessful() throws Exception {
        gitlabRequestMockProvider.mockCreationOfUser("batman");
        ReflectionTestUtils.setField(gitLabPersonalAccessTokenManagementService, "versionControlAccessToken", true);
        userTestService.createInternalUser_asAdmin_isSuccessful();
        ReflectionTestUtils.setField(gitLabPersonalAccessTokenManagementService, "versionControlAccessToken", false);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createInternalUserWithoutRoles_isSuccessful() throws Exception {
        gitlabRequestMockProvider.mockCreationOfUser("batman");
        ReflectionTestUtils.setField(gitLabPersonalAccessTokenManagementService, "versionControlAccessToken", true);
        userTestService.createInternalUserWithoutRoles_asAdmin_isSuccessful();
        ReflectionTestUtils.setField(gitLabPersonalAccessTokenManagementService, "versionControlAccessToken", false);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createUser_asAdmin_hasId() throws Exception {
        userTestService.createUser_asAdmin_hasId();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createUser_asAdmin_existingLogin() throws Exception {
        userTestService.createUser_asAdmin_existingLogin();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createUser_asAdmin_existingEmail() throws Exception {
        userTestService.createUser_asAdmin_existingEmail();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createUserAsAdminExistsInCi() throws Exception {
        userTestService.createUserAsAdminExistsInCi();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createUser_asAdmin_illegalLogin_internalError() throws Exception {
        userTestService.createUser_asAdmin_illegalLogin_internalError();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createUser_asAdmin_failInExternalUserManagement_internalError() throws Exception {
        userTestService.createUser_asAdmin_failInExternalCiUserManagement_internalError();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createUser_asAdmin_failInExternalCiUserManagement_cannotGetCiUser_internalError() throws Exception {
        userTestService.createUser_asAdmin_failInExternalCiUserManagement_cannotGetCiUser_internalError();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createUser_asAdmin_failInExternalVcsUserManagement_internalError() throws Exception {
        userTestService.createUser_asAdmin_failInExternalVcsUserManagement_internalError();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createUser_withNullAsPassword_generatesRandomPassword() throws Exception {
        userTestService.createUser_withNullAsPassword_generatesRandomPassword();
    }

    /**
     * Tests if the deletion of a user by admin succeeds
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deleteUser_isSuccessful() throws Exception {
        jenkinsRequestMockProvider.mockGetAnyUser(false, 1);
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
        jenkinsRequestMockProvider.mockGetAnyUser(true, 4);
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
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createUserWithGroups() throws Exception {
        userTestService.createUserWithGroups();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createUserWithGroupsAlreadyExistsInGitlab() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        ProgrammingExercise programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);

        User newUser = userTestService.student;
        newUser.setId(null);
        newUser.setLogin("batman");
        newUser.setEmail("foobar@tum.com");
        newUser.setGroups(Set.of("tutor", "instructor"));

        gitlabRequestMockProvider.mockAddUserToGroupsUserExists(newUser, programmingExercise.getProjectKey());
        jenkinsRequestMockProvider.mockCreateUser(newUser, false, false, false);
        request.post("/api/admin/users", new ManagedUserVM(newUser), HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createUserWithGroupsAlreadyFailsInGitlab() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        ProgrammingExercise programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);

        User newUser = userTestService.student;
        newUser.setId(null);
        newUser.setLogin("batman");
        newUser.setEmail("foobar@tum.com");
        newUser.setGroups(Set.of("tutor", "instructor2"));

        gitlabRequestMockProvider.mockAddUserToGroupsFails(newUser, programmingExercise.getProjectKey());
        request.post("/api/admin/users", new ManagedUserVM(newUser), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateUserGroups() throws Exception {
        userTestService.student.setPassword(passwordService.hashPassword("this is a password"));
        userRepository.save(userTestService.student);
        userTestService.updateUserGroups();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateUserLogin() throws Exception {
        userTestService.updateUserLogin();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateUserEmptyRoles() throws Exception {
        userTestService.updateUserWithEmptyRoles();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldFailIfCannotUpdateActivatedUserInGitlab() throws Exception {
        String oldLogin = userTestService.student.getLogin();
        User user = userTestService.student;
        user.setLogin("new-login");

        jenkinsRequestMockProvider.mockUpdateUserAndGroups(oldLogin, user, user.getGroups(), Set.of(), true);
        gitlabRequestMockProvider.mockUpdateVcsUserFailToActivate(oldLogin, user);
        request.put("/api/admin/users", new ManagedUserVM(user, "some-new-password"), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldFailIfCannotUpdateDeactivatedUserInGitlab() throws Exception {
        // create unactivated user in repo
        User user = UserFactory.generateActivatedUser("ab123cd");
        user.setActivated(false);
        user.setActivationKey("testActivationKey");

        // Register the user
        ManagedUserVM userVM = new ManagedUserVM(user);
        userVM.setPassword("password");
        gitlabRequestMockProvider.mockCreateVcsUser(user, false);
        gitlabRequestMockProvider.mockDeactivateUser(user.getLogin(), false);
        request.postWithoutLocation("/api/public/register", userVM, HttpStatus.CREATED, null);

        Optional<User> registeredUser = userTestService.getUserRepository().findOneWithGroupsAndAuthoritiesByLogin(user.getLogin());
        assertThat(registeredUser).isPresent();

        // Update user and assert
        String oldLogin = user.getLogin();
        user = registeredUser.get();
        user.setLogin("some-new-login");

        jenkinsRequestMockProvider.mockUpdateUserAndGroups(oldLogin, user, user.getGroups(), Set.of(), true);
        gitlabRequestMockProvider.mockUpdateVcsUserFailToActivate(oldLogin, user);
        request.put("/api/admin/users", new ManagedUserVM(user, "some-new-password"), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldBlockUserInGitlabIfAccountNotActivated() throws Exception {
        String password = "this is a password";
        userTestService.student.setPassword(passwordService.hashPassword(password));
        userRepository.save(userTestService.student);
        var oldLogin = userTestService.student.getLogin();
        User user = userTestService.student;
        user.setLogin("new-login");
        user.setActivated(false);

        jenkinsRequestMockProvider.mockUpdateUserAndGroups(oldLogin, user, user.getGroups(), Set.of(), true);
        gitlabRequestMockProvider.mockUpdateVcsUser(oldLogin, user, Set.of(), user.getGroups(), true);

        request.put("/api/admin/users", new ManagedUserVM(user, password), HttpStatus.OK);

        UserRepository userRepository = userTestService.getUserRepository();
        final var userInDB = userRepository.findById(user.getId());
        assertThat(userInDB).isPresent();
        assertThat(userInDB.get().getLogin()).isEqualTo(user.getLogin());

        verify(gitlabRequestMockProvider.getMockedUserApi()).blockUser(anyLong());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void initializeUser() throws Exception {
        userTestService.initializeUser(true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void initializeUserWithoutFlag() throws Exception {
        userTestService.initializeUserWithoutFlag();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void initializeUserNonLTI() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        jenkinsRequestMockProvider.mockUpdateUserAndGroups(user.getLogin(), user, Collections.emptySet(), Collections.emptySet(), true);
        userTestService.initializeUserNonLTI();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void initializeUserExternal() throws Exception {
        userTestService.initializeUserExternal();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUser() throws Exception {
        userTestService.testUser();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUserWithoutGroups() throws Exception {
        userTestService.testUserWithoutGroups();
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
