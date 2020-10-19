package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import de.tum.in.www1.artemis.connector.gitlab.GitlabRequestMockProvider;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;

public class AccountResourceWithGitLabIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    RequestUtilService request;

    @Autowired
    UserRepository userRepo;

    @Autowired
    UserService userService;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    private GitlabRequestMockProvider gitlabRequestMockProvider;

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
        gitlabRequestMockProvider.reset();
    }

    @BeforeEach
    public void setUp() {
        gitlabRequestMockProvider.enableMockingOfRequests();
    }

    @Test
    public void testUnactivatedUserIsDeletedCorrectly() throws Exception {
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
        gitlabRequestMockProvider.mockCreationOfUser(user.getLogin());

        // make request and assert Status Created
        request.postWithoutLocation("/api/register", userVM, HttpStatus.CREATED, null);

        // Assert that old user data was deleted and user was written to db
        Optional<User> updatedUser = userRepo.findOneByLogin(user.getLogin());
        assertThat(updatedUser.isPresent()).isTrue();
        assertThat(updatedUser.get().getFirstName()).isEqualTo("New Firstname");
        assertThat(updatedUser.get().getActivated()).isFalse();
    }

}
