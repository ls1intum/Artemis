package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.gitlab4j.api.UserApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.connector.GitlabRequestMockProvider;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;

public class AccountResourceWithGitLabIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    private RequestUtilService request;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private DatabaseUtilService database;

    @Autowired
    private GitlabRequestMockProvider gitlabRequestMockProvider;

    @BeforeEach
    public void setUp() {
        gitlabRequestMockProvider.enableMockingOfRequests();
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
        gitlabRequestMockProvider.reset();
    }

    /**
     * Tests the registration of a user when an old unactivated User existed.
     * Also tries to verify that the inability to delete such user in GitLab does not hinder the operation.
     * @throws Exception on unknown failure
     */
    @Test
    public void testUnactivatedUserIsDeletedDespiteUnableToDeleteInGitlab() throws Exception {
        // create unactivated user in repo
        String testActivationKey = "testActivationKey";
        User user = ModelFactory.generateActivatedUser("ab123cd");
        user.setActivated(false);
        user.setFirstName("Old Firstname");
        user.setActivationKey(testActivationKey);
        user = userRepo.save(user);

        // setup user to register
        user.setFirstName("New Firstname");
        ManagedUserVM userVM = new ManagedUserVM(user);
        userVM.setPassword("password");

        // Simulate failure to delete GitLab user, this should not keep Artemis from creating a new user
        gitlabRequestMockProvider.mockFailOnGetUserById(user.getLogin());
        // Simulate creation of GitLab user
        gitlabRequestMockProvider.mockCanCreateVcsUser(user);

        SecurityUtils.setAuthorizationObject();
        jenkinsRequestMockProvider.mockDeleteUser(user, true, false);
        jenkinsRequestMockProvider.mockCanCreateUser(user, false);

        // make request and assert Status Created
        request.postWithoutLocation("/api/register", userVM, HttpStatus.CREATED, null);

        // Assert that old user data was deleted and user was written to db
        Optional<User> updatedUser = userRepo.findOneByLogin(user.getLogin());
        assertThat(updatedUser.isPresent()).isTrue();
        assertThat(updatedUser.get().getFirstName()).isEqualTo("New Firstname");
        assertThat(updatedUser.get().getActivated()).isFalse();
    }

    /**
     * Tests the registration of a user can not overwrite and existing user.
     * @throws Exception on unknown failure
     */
    @Test
    public void testFailureWhenTryingToDeleteActivatedUser() throws Exception {
        // create activated user in repo
        User user = ModelFactory.generateActivatedUser("ab123cd");
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
        request.postWithoutLocation("/api/register", userVM, HttpStatus.BAD_REQUEST, null);

        // Assert that old user data is still there
        Optional<User> updatedUser = userRepo.findOneByLogin(user.getLogin());
        assertThat(updatedUser.isPresent()).isTrue();
        assertThat(updatedUser.get().getFirstName()).isEqualTo("Old Firstname");
        assertThat(updatedUser.get().getActivated()).isTrue();
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testShouldNotRegisterUserIfCannotCreateInGitlab() throws Exception {
        // create unactivated user in repo
        User user = ModelFactory.generateActivatedUser("ab123cd");
        user.setActivated(false);
        user.setActivationKey("testActivationKey");

        // setup user to register
        ManagedUserVM userVM = new ManagedUserVM(user);
        userVM.setPassword("password");

        // make request and assert
        gitlabRequestMockProvider.mockGetUserId(user.getLogin(), true);
        request.postWithoutLocation("/api/register", userVM, HttpStatus.INTERNAL_SERVER_ERROR, null);

        // The account shouldn't be saved
        assertThat(userRepo.findOneByLogin(user.getLogin())).isEmpty();

        // make another request
        doReturn(new org.gitlab4j.api.models.User().withId(1)).when(mock(UserApi.class)).getUser(user.getLogin());
        gitlabRequestMockProvider.mockCreateVcsUser(user, true);
        request.postWithoutLocation("/api/register", userVM, HttpStatus.INTERNAL_SERVER_ERROR, null);

        // The account shouldn't be saved
        assertThat(userRepo.findOneByLogin(user.getLogin())).isEmpty();
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testShouldNotRegisterUserIfCannotCreateInJenkins() throws Exception {
        // create unactivated user in repo
        User user = ModelFactory.generateActivatedUser("ab123cd");
        user.setActivated(false);
        user.setActivationKey("testActivationKey");

        // setup user to register
        ManagedUserVM userVM = new ManagedUserVM(user);
        userVM.setPassword("password");

        gitlabRequestMockProvider.mockCanCreateVcsUser(user);
        jenkinsRequestMockProvider.mockCanCreateUser(user, true);
        request.postWithoutLocation("/api/register", userVM, HttpStatus.INTERNAL_SERVER_ERROR, null);

        // The account shouldn't be saved
        assertThat(userRepo.findOneByLogin(user.getLogin())).isEmpty();
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testShouldRegisterUserIfCanCreateInJenkinsAndGitlab() throws Exception {
        // create unactivated user in repo
        User user = ModelFactory.generateActivatedUser("ab123cd");
        user.setActivated(false);
        user.setActivationKey("testActivationKey");

        // setup user to register
        ManagedUserVM userVM = new ManagedUserVM(user);
        userVM.setPassword("password");

        gitlabRequestMockProvider.mockCanCreateVcsUser(user);
        jenkinsRequestMockProvider.mockCanCreateUser(user, false);
        request.postWithoutLocation("/api/register", userVM, HttpStatus.CREATED, null);

        assertThat(userRepo.findOneByLogin(user.getLogin())).isPresent();
    }
}
