package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;

import de.tum.in.www1.artemis.util.DatabaseUtilService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.dto.PasswordChangeDTO;
import de.tum.in.www1.artemis.service.dto.UserDTO;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.vm.KeyAndPasswordVM;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;

public class AccountResourceIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    RequestUtilService request;

    @Autowired
    UserRepository userRepo;

    @Autowired
    UserService userService;

    @Autowired
    DatabaseUtilService database;

    @BeforeEach
    public void init() {
        database.addUsers(1, 1, 1);
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    public void registerAccount() throws Exception {
        // setup user
        User user = ModelFactory.generateActivatedUser("ab123cd");
        ManagedUserVM userVM = new ManagedUserVM(user);
        userVM.setPassword("password");

        // make request
        request.postWithoutLocation("/api/register", userVM, HttpStatus.CREATED, null);
    }

    @Test
    public void activateAccount() throws Exception {
        // create unactivated user in repo
        String testActivationKey = "testActivationKey";
        User user = ModelFactory.generateActivatedUser("ab123cd");
        user.setActivated(false);
        user.setActivationKey(testActivationKey);
        user = userRepo.save(user);

        // make request
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("key", testActivationKey);
        request.get("/api/activate", HttpStatus.OK, String.class, params);

        // check result
        Optional<User> updatedUser = userRepo.findById(user.getId());
        assertThat(updatedUser.get()).isNotNull();
        assertThat(updatedUser.get().getActivated()).isTrue();
        assertThat(updatedUser.get().getActivationKey()).isNull();
    }

    @Test
    public void activateAccountNoUser() throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("key", "");
        request.get("/api/activate", HttpStatus.INTERNAL_SERVER_ERROR, String.class, params);
    }

    @Test
    @WithMockUser("authenticatedUser")
    public void isAuthenticated() throws Exception {
        String userLogin = request.get("/api/authenticate", HttpStatus.OK, String.class);
        assertThat(userLogin).isNotNull();
        assertThat(userLogin).isEqualTo("authenticatedUser");
    }

    @Test
    public void isAuthenticatedWithoutLoggedInUser() throws Exception {
        String user = request.get("/api/authenticate", HttpStatus.OK, String.class);
        assertThat(user).isEmpty();
    }

    @Test
    @WithMockUser("authenticateduser")
    public void getAccount() throws Exception {
        // create user in repo
        User user = ModelFactory.generateActivatedUser("authenticateduser");
        user = userRepo.save(user);

        UserDTO account = request.get("/api/account", HttpStatus.OK, UserDTO.class);
        assertThat(account).isNotNull();
    }

    @Test
    public void getAccountWithoutLoggedInUser() throws Exception {
        UserDTO user = request.get("/api/account", HttpStatus.NOT_FOUND, UserDTO.class);
        assertThat(user).isNull();
    }

    @Test
    @WithMockUser(username = "authenticateduser")
    public void getPassword() throws Exception {
        // create user in repo
        User user = ModelFactory.generateActivatedUser("authenticateduser");
        userService.createUser(new ManagedUserVM(user));

        // make request
        Map response = request.get("/api/account/password", HttpStatus.OK, Map.class);
        assertThat(response.get("password")).isNotNull();
        assertThat(response.get("password")).isNotEqualTo("");
    }

    // TODO: check invalid calls

    @Test
    @WithMockUser(username = "authenticateduser", roles = "INSTRUCTOR")
    public void saveAccount() throws Exception {
        // create user in repo
        User user = ModelFactory.generateActivatedUser("authenticateduser");
        User createdUser = userService.createUser(new ManagedUserVM(user));
        // update FirstName
        String updatedFirstName = "UpdatedFirstName";
        createdUser.setFirstName(updatedFirstName);

        // make request
        request.post("/api/account", new UserDTO(createdUser), HttpStatus.OK);

        // check if update successful
        User updatedUser = userRepo.findOneByLogin("authenticateduser").get();
        assertThat(updatedUser.getFirstName()).isEqualTo(updatedFirstName);
    }

    @Test
    @WithMockUser("authenticatedUser")
    public void changePassword() throws Exception {
        // create user in repo
        User user = ModelFactory.generateActivatedUser("authenticatedUser");
        User createdUser = userService.createUser(new ManagedUserVM(user));
        // Password Data
        String updatedPassword = "1234";
        PasswordChangeDTO pwChange = new PasswordChangeDTO(user.getPassword(), updatedPassword);

        // make request
        request.post("/api/account/change-password", pwChange, HttpStatus.OK);

        // check if update successful
        User updatedUser = userRepo.findOneByLogin("authenticatedUser").get();
        assertThat(updatedUser.getPassword()).isEqualTo(updatedPassword);
    }

    @Test
    @WithMockUser("authenticateduser")
    public void passwordReset() throws Exception {
        // create user in repo
        User user = ModelFactory.generateActivatedUser("authenticateduser");
        User createdUser = userService.createUser(new ManagedUserVM(user));
        // init password reset
        request.post("/api/reset-password/init", createdUser.getEmail(), HttpStatus.OK);

        // check user data
        User userPasswordResetInit = userRepo.findOneByLogin("authenticatedUser").get();
        String resetKey = userPasswordResetInit.getResetKey();

        // finish password reset
        String newPassword = "password";
        KeyAndPasswordVM finishResetData = new KeyAndPasswordVM();
        finishResetData.setKey(resetKey);
        finishResetData.setNewPassword(newPassword);

        // finish password reset
        request.post("/api/reset-password/finish", finishResetData, HttpStatus.OK);

        // get updated user
        User userPasswordResetFinished = userRepo.findOneByLogin("authenticatedUser").get();

        assertThat(userPasswordResetFinished.getPassword()).isEqualTo(newPassword);
    }

}
