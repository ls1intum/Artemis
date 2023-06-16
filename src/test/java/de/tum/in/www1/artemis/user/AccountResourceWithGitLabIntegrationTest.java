package de.tum.in.www1.artemis.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.gitlab4j.api.UserApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.connector.GitlabRequestMockProvider;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;

class AccountResourceWithGitLabIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    private RequestUtilService request;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private GitlabRequestMockProvider gitlabRequestMockProvider;

    @Autowired
    private UserUtilService userUtilService;

    @BeforeEach
    void setUp() {
        gitlabRequestMockProvider.enableMockingOfRequests();
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
    }

    @AfterEach
    void tearDown() throws Exception {
        gitlabRequestMockProvider.reset();
    }

    @Test
    void registerAccount() throws Exception {
        String login = "abd123cd";
        String password = "this is a password";
        // setup user
        User user = UserFactory.generateActivatedUser(login);
        ManagedUserVM userVM = new ManagedUserVM(user);
        userVM.setPassword(password);

        gitlabRequestMockProvider.mockCreateVcsUser(user, false);
        gitlabRequestMockProvider.mockGetUserId(login, true, false);
        gitlabRequestMockProvider.mockDeactivateUser(login, false);

        // make request
        request.postWithoutLocation("/api/public/register", userVM, HttpStatus.CREATED, null);
    }

    /**
     * Tests the registration of a user when an old unactivated User existed.
     * Also tries to verify that the inability to delete such user in GitLab does not hinder the operation.
     *
     * @throws Exception on unknown failure
     */
    @Test
    void testUnactivatedUserIsDeletedDespiteUnableToDeleteInGitlab() throws Exception {
        // create unactivated user in repo
        String testActivationKey = "testActivationKey";
        User user = UserFactory.generateActivatedUser("ab123cd");
        user.setActivated(false);
        user.setFirstName("Old Firstname");
        user.setActivationKey(testActivationKey);
        user = userRepo.save(user);

        // setup user to register
        user.setFirstName("New Firstname");
        ManagedUserVM userVM = new ManagedUserVM(user);
        userVM.setPassword("password");

        // Simulate failure to delete GitLab user, this should not keep Artemis from creating a new user
        gitlabRequestMockProvider.mockDeleteVcsUser(user.getLogin(), true, true);

        // Simulate creation of GitLab user
        gitlabRequestMockProvider.mockCreateVcsUser(user, false);
        gitlabRequestMockProvider.mockDeactivateUser(user.getLogin(), false);

        // make request and assert Status Created
        request.postWithoutLocation("/api/public/register", userVM, HttpStatus.CREATED, null);

        // Assert that old user data was deleted and user was written to db
        Optional<User> updatedUser = userRepo.findOneByLogin(user.getLogin());
        assertThat(updatedUser).isPresent();
        assertThat(updatedUser.get().getFirstName()).isEqualTo("New Firstname");
        assertThat(updatedUser.get().getActivated()).isFalse();
    }

    /**
     * Tests the registration of a user can not overwrite and existing user.
     *
     * @throws Exception on unknown failure
     */
    @Test
    void testFailureWhenTryingToDeleteActivatedUser() throws Exception {
        // create activated user in repo
        User user = UserFactory.generateActivatedUser("ab123cde");
        user.setFirstName("Old Firstname");
        user = userRepo.save(user);

        // setup user to register
        user.setFirstName("New Firstname");
        ManagedUserVM userVM = new ManagedUserVM(user);
        userVM.setPassword("password");

        // Simulate failure to delete GitLab user, this should not keep Artemis from creating a new user
        gitlabRequestMockProvider.mockFailOnGetUserById(user.getLogin());
        // Simulate creation of GitLab user
        gitlabRequestMockProvider.mockCreationOfUser(user.getLogin());

        // make request and assert Status 400
        request.postWithoutLocation("/api/public/register", userVM, HttpStatus.BAD_REQUEST, null);

        // Assert that old user data is still there
        Optional<User> updatedUser = userRepo.findOneByLogin(user.getLogin());
        assertThat(updatedUser).isPresent();
        assertThat(updatedUser.get().getFirstName()).isEqualTo("Old Firstname");
        assertThat(updatedUser.get().getActivated()).isTrue();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testShouldNotRegisterUserIfCannotCreateInGitlab() throws Exception {
        // create unactivated user in repo
        User user = UserFactory.generateActivatedUser("ab123cdf");
        user.setActivated(false);
        user.setActivationKey("testActivationKey");

        // setup user to register
        ManagedUserVM userVM = new ManagedUserVM(user);
        userVM.setPassword("password");

        // make request and assert
        gitlabRequestMockProvider.mockCreateVcsUser(user, true);
        request.postWithoutLocation("/api/public/register", userVM, HttpStatus.INTERNAL_SERVER_ERROR, null);

        // The account shouldn't be saved
        assertThat(userRepo.findOneByLogin(user.getLogin())).isEmpty();

        // make another request
        doReturn(new org.gitlab4j.api.models.User().withId(1L)).when(mock(UserApi.class)).getUser(user.getLogin());
        gitlabRequestMockProvider.mockCreateVcsUser(user, true);
        request.postWithoutLocation("/api/public/register", userVM, HttpStatus.INTERNAL_SERVER_ERROR, null);

        // The account shouldn't be saved
        assertThat(userRepo.findOneByLogin(user.getLogin())).isEmpty();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testShouldRegisterUserIfCanCreateAndDeactivateAccountInGitlab() throws Exception {
        // create unactivated user in repo
        User user = UserFactory.generateActivatedUser("ab123cdg");
        user.setActivated(false);
        user.setActivationKey("testActivationKey");

        // setup user to register
        ManagedUserVM userVM = new ManagedUserVM(user);
        userVM.setPassword("password");

        gitlabRequestMockProvider.mockCreateVcsUser(user, false);
        gitlabRequestMockProvider.mockDeactivateUser(user.getLogin(), false);
        request.postWithoutLocation("/api/public/register", userVM, HttpStatus.CREATED, null);

        assertThat(userRepo.findOneByLogin(user.getLogin())).isPresent();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testShouldAbortRegistrationAndFailIfCannotDeactivateAccountInGitlab() throws Exception {
        // create unactivated user in repo
        User user = UserFactory.generateActivatedUser("ab123cdh");
        user.setActivated(false);
        user.setActivationKey("testActivationKey");

        // setup user to register
        ManagedUserVM userVM = new ManagedUserVM(user);
        userVM.setPassword("password");

        gitlabRequestMockProvider.mockCreateVcsUser(user, false);
        gitlabRequestMockProvider.mockDeactivateUser(user.getLogin(), true);
        request.postWithoutLocation("/api/public/register", userVM, HttpStatus.INTERNAL_SERVER_ERROR, null);

        assertThat(userRepo.findOneByLogin(user.getLogin())).isEmpty();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testShouldActivateUserInGitlab() throws Exception {
        // create unactivated user in repo
        User user = UserFactory.generateActivatedUser("ab123cdi");
        user.setActivated(false);
        user.setActivationKey("testActivationKey");

        // setup user to register
        ManagedUserVM userVM = new ManagedUserVM(user);
        userVM.setPassword("password");

        gitlabRequestMockProvider.mockCreateVcsUser(user, false);
        gitlabRequestMockProvider.mockDeactivateUser(user.getLogin(), false);
        request.postWithoutLocation("/api/public/register", userVM, HttpStatus.CREATED, null);

        Optional<User> registeredUser = userRepo.findOneByLogin(user.getLogin());
        assertThat(registeredUser).isPresent();

        // Activate the user
        gitlabRequestMockProvider.mockActivateUser(user.getLogin(), false);
        String activationKey = registeredUser.get().getActivationKey();
        request.get("/api/public/activate?key=" + activationKey, HttpStatus.OK, Void.class);
        verify(gitlabRequestMockProvider.getMockedUserApi()).unblockUser(anyLong());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testShouldThrowErrorIfCannotActivateUserInGitlab() throws Exception {
        // create unactivated user in repo
        User user = UserFactory.generateActivatedUser("ab123cdj");
        user.setActivated(false);
        user.setActivationKey("testActivationKey");

        // Register the user
        ManagedUserVM userVM = new ManagedUserVM(user);
        userVM.setPassword("password");
        gitlabRequestMockProvider.mockCreateVcsUser(user, false);
        gitlabRequestMockProvider.mockDeactivateUser(user.getLogin(), false);
        request.postWithoutLocation("/api/public/register", userVM, HttpStatus.CREATED, null);

        Optional<User> registeredUser = userRepo.findOneByLogin(user.getLogin());
        assertThat(registeredUser).isPresent();

        // Activate the user
        gitlabRequestMockProvider.mockActivateUser(user.getLogin(), true);
        String activationKey = registeredUser.get().getActivationKey();
        request.get("/api/public/activate?key=" + activationKey, HttpStatus.INTERNAL_SERVER_ERROR, Void.class);
        verify(gitlabRequestMockProvider.getMockedUserApi()).unblockUser(anyLong());

        assertThat(registeredUser.get().getActivationKey()).isNotNull();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testShouldBlockRegistrationIfUnactivatedUserWithSameLogin() throws Exception {
        // Create existing non activated user
        createAndSaveUser("userLogin", "userLogin@mytum.de");

        User newUser = createUser("userLogin", "newUser@mytum.de");

        // Register the user
        ManagedUserVM userVM = new ManagedUserVM(newUser);
        userVM.setPassword("password");
        gitlabRequestMockProvider.mockCreateVcsUser(newUser, false);
        gitlabRequestMockProvider.mockDeactivateUser(newUser.getLogin(), false);
        request.postWithoutLocation("/api/public/register", userVM, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testShouldBlockRegistrationIfUnactivatedUserWithSameEmail() throws Exception {
        createAndSaveUser("existingLogin", "existingUser@mytum.de");
        User newUser = createUser("newLogin", "existingUser@mytum.de");

        // Register the user
        ManagedUserVM userVM = new ManagedUserVM(newUser);
        userVM.setPassword("password");
        gitlabRequestMockProvider.mockCreateVcsUser(newUser, false);
        gitlabRequestMockProvider.mockDeactivateUser(newUser.getLogin(), false);
        request.postWithoutLocation("/api/public/register", userVM, HttpStatus.BAD_REQUEST, null);
    }

    @NotNull
    private static User createUser(String newLogin, String email) {
        User newUser = UserFactory.generateActivatedUser(newLogin);
        newUser.setActivated(false);
        newUser.setEmail(email);
        return newUser;
    }

    private void createAndSaveUser(String login, String email) {
        if (!userUtilService.userExistsWithLogin(login)) {
            User user = UserFactory.generateActivatedUser(login);
            user.setEmail(email);
            user.setActivated(false);
            userRepo.save(user);
        }
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testShouldFailRegistrationIfActivatedUserWithSameEmail() throws Exception {
        createAndSaveUser("existingLogin2", "existingUser2@mytum.de");
        User newUser = createUser("newLogin", "existingUser@mytum.de");

        // Register the user
        ManagedUserVM userVM = new ManagedUserVM(newUser);
        userVM.setPassword("password");
        gitlabRequestMockProvider.mockCreateVcsUser(newUser, false);
        gitlabRequestMockProvider.mockDeactivateUser(newUser.getLogin(), false);
        request.postWithoutLocation("/api/public/register", userVM, HttpStatus.BAD_REQUEST, null);
    }
}
