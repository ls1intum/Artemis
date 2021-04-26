package de.tum.in.www1.artemis;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.dto.PasswordChangeDTO;
import de.tum.in.www1.artemis.service.dto.UserDTO;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.service.user.UserCreationService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.AccountResource;
import de.tum.in.www1.artemis.web.rest.vm.KeyAndPasswordVM;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.LinkedMultiValueMap;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link AccountResource}. Several Tests rely on overwriting AccountResource.registrationEnabled and other attributes with reflections. Any changes to the internal structure will cause these tests to fail.
 */
public class AccountResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {
    @Autowired
    private AccountResource accountResource;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCreationService userCreationService;

    @Autowired
    private PasswordService passwordService;

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    /**
     * Runs a test but changes the specified property beforehand and resets it to the previous property afterwards
     *
     * @param configName  the name of the attribute
     * @param configValue the value it should be changed to
     * @param test        the test to execute
     * @throws Throwable if the test throws anything
     */
    private void testWithChangedConfig(String configName, Object configValue, Executable test) throws Throwable {
        var oldValue = ReflectionTestUtils.getField(accountResource, configName);
        ReflectionTestUtils.setField(accountResource, configName, configValue);

        try {
            test.execute();
        } finally {
            ReflectionTestUtils.setField(accountResource, configName, oldValue);
        }
    }

    private void testWithRegistrationDisabled(Executable test) throws Throwable {
        testWithChangedConfig("registrationEnabled", Optional.of(Boolean.FALSE), test);
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
    public void registerAccountTooLongPassword() throws Exception {
        // setup user
        User user = ModelFactory.generateActivatedUser("ab123cd");
        ManagedUserVM userVM = new ManagedUserVM(user);
        userVM.setPassword("e".repeat(ManagedUserVM.PASSWORD_MAX_LENGTH + 1));

        // make request
        request.postWithoutLocation("/api/register", userVM, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    public void registerAccountTooShortPassword() throws Exception {
        // setup user
        User user = ModelFactory.generateActivatedUser("ab123cd");
        ManagedUserVM userVM = new ManagedUserVM(user);
        userVM.setPassword("e".repeat(Math.max(0, ManagedUserVM.PASSWORD_MIN_LENGTH - 1)));

        // make request
        request.postWithoutLocation("/api/register", userVM, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    public void registerAccountEmptyPassword() throws Exception {
        // setup user
        User user = ModelFactory.generateActivatedUser("ab123cd");
        ManagedUserVM userVM = new ManagedUserVM(user);
        userVM.setPassword("");

        // make request
        request.postWithoutLocation("/api/register", userVM, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    public void registerAccountRegistrationDisabled() throws Throwable {
        testWithRegistrationDisabled(() -> {
            // setup user
            User user = ModelFactory.generateActivatedUser("ab123cd");
            ManagedUserVM userVM = new ManagedUserVM(user);
            userVM.setPassword("password");

            // make request
            request.postWithoutLocation("/api/register", userVM, HttpStatus.FORBIDDEN, null);
        });
    }

    @Test
    public void registerAccountRegistrationConfigEmpty() throws Throwable {
        testWithChangedConfig("registrationEnabled", Optional.empty(), () -> {
            // setup user
            User user = ModelFactory.generateActivatedUser("ab123cd");
            ManagedUserVM userVM = new ManagedUserVM(user);
            userVM.setPassword("password");

            // make request
            request.postWithoutLocation("/api/register", userVM, HttpStatus.FORBIDDEN, null);
        });
    }

    @Test
    public void registerAccountInvalidEmail() throws Throwable {
        // Inject email-pattern to be independent of the config
        testWithChangedConfig("allowedEmailPattern", Optional.of(Pattern.compile("[a-zA-Z0-9_\\-.+]+@[a-zA-Z0-9_\\-.]+\\.[a-zA-Z]{2,5}")), () -> {
            // setup user
            User user = ModelFactory.generateActivatedUser("ab123cd");
            user.setEmail("-");
            ManagedUserVM userVM = new ManagedUserVM(user);
            userVM.setPassword("password");

            // make request
            request.postWithoutLocation("/api/register", userVM, HttpStatus.BAD_REQUEST, null);
        });
    }

    @Test
    public void registerAccountEmptyEmailPattern() throws Throwable {
        // Inject email-pattern to be independent of the config
        testWithChangedConfig("allowedEmailPattern", Optional.empty(), () -> {
            // setup user
            User user = ModelFactory.generateActivatedUser("ab123cd");
            user.setEmail("-");
            ManagedUserVM userVM = new ManagedUserVM(user);
            userVM.setPassword("password");

            // make request
            request.postWithoutLocation("/api/register", userVM, HttpStatus.CREATED, null);
        });
    }

    @Test
    public void activateAccount() throws Exception {
        // create unactivated user in repo
        String testActivationKey = "testActivationKey";
        User user = ModelFactory.generateActivatedUser("ab123cd");
        user.setActivated(false);
        user.setActivationKey(testActivationKey);
        user = userRepository.save(user);

        // make request
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("key", testActivationKey);
        request.get("/api/activate", HttpStatus.OK, String.class, params);

        // check result
        Optional<User> updatedUser = userRepository.findById(user.getId());
        assertThat(updatedUser).isPresent();
        assertThat(updatedUser.get()).isNotNull();
        assertThat(updatedUser.get().getActivated()).isTrue();
        assertThat(updatedUser.get().getActivationKey()).isNull();
    }

    @Test
    public void activateAccountRegistrationDisabled() throws Throwable {
        testWithRegistrationDisabled(() -> {
            String testActivationKey = "testActivationKey";

            // make request
            LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("key", testActivationKey);
            request.get("/api/activate", HttpStatus.FORBIDDEN, String.class, params);
        });
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
        userRepository.save(user);
        UserDTO account = request.get("/api/account", HttpStatus.OK, UserDTO.class);
        assertThat(account).isNotNull();
    }

    @Test
    @WithAnonymousUser
    public void getAccountWithoutLoggedInUser() throws Exception {
        UserDTO user = request.get("/api/account", HttpStatus.UNAUTHORIZED, UserDTO.class);
        assertThat(user).isNull();
    }

    @Test
    @WithMockUser(username = "authenticateduser")
    public void getPassword() throws Exception {
        // create user in repo
        User user = ModelFactory.generateActivatedUser("authenticateduser");
        userCreationService.createUser(new ManagedUserVM(user));

        // make request
        @SuppressWarnings("rawtypes") Map response = request.get("/api/account/password", HttpStatus.OK, Map.class);
        assertThat(response.get("password")).isNotNull();
        assertThat(response.get("password")).isNotEqualTo("");
    }

    @Test
    @WithMockUser(username = "authenticateduser")
    public void saveAccount() throws Exception {
        // create user in repo
        User user = ModelFactory.generateActivatedUser("authenticateduser");
        User createdUser = userCreationService.createUser(new ManagedUserVM(user));
        // update FirstName
        String updatedFirstName = "UpdatedFirstName";
        createdUser.setFirstName(updatedFirstName);

        // make request
        request.put("/api/account", new UserDTO(createdUser), HttpStatus.OK);

        // check if update successful
        Optional<User> updatedUser = userRepository.findOneByLogin("authenticateduser");
        assertThat(updatedUser).isPresent();
        assertThat(updatedUser.get().getFirstName()).isEqualTo(updatedFirstName);
    }

    @Test
    @WithMockUser(username = "authenticateduser")
    public void saveAccountRegistrationDisabled() throws Throwable {
        testWithRegistrationDisabled(() -> {
            // create user in repo
            User user = ModelFactory.generateActivatedUser("authenticateduser");
            User createdUser = userCreationService.createUser(new ManagedUserVM(user));
            // update FirstName
            String updatedFirstName = "UpdatedFirstName";
            createdUser.setFirstName(updatedFirstName);

            // make request
            request.put("/api/account", new UserDTO(createdUser), HttpStatus.FORBIDDEN);
        });
    }

    @Test
    @WithMockUser(username = "authenticateduser")
    public void saveAccountEmailInUse() throws Exception {
        // create user in repo
        User user = ModelFactory.generateActivatedUser("authenticateduser");
        User createdUser = userCreationService.createUser(new ManagedUserVM(user));
        User userSameEmail = ModelFactory.generateActivatedUser("sameemail");
        User createdUserSameEmail = userCreationService.createUser(new ManagedUserVM(userSameEmail));
        // update Email to one already used
        createdUser.setEmail(createdUserSameEmail.getEmail());

        // make request
        request.put("/api/account", new UserDTO(createdUser), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "authenticateduser")
    public void changePassword() throws Exception {
        // create user in repo
        User user = ModelFactory.generateActivatedUser("authenticateduser");
        User createdUser = userCreationService.createUser(new ManagedUserVM(user));
        // Password Data
        String updatedPassword = "12345678password-reset-init.component.spec.ts";

        PasswordChangeDTO pwChange = new PasswordChangeDTO(passwordService.decryptPassword(createdUser.getPassword()), updatedPassword);
        // make request
        request.postWithoutLocation("/api/account/change-password", pwChange, HttpStatus.OK, null);

        // check if update successful
        Optional<User> updatedUser = userRepository.findOneByLogin("authenticateduser");
        assertThat(updatedUser).isPresent();
        assertThat(passwordService.decryptPassword(updatedUser.get().getPassword())).isEqualTo(updatedPassword);
    }

    @Test
    @WithMockUser(username = "authenticateduser")
    public void changePasswordRegistrationDisabled() throws Throwable {
        testWithRegistrationDisabled(() -> {
            // create user in repo
            User user = ModelFactory.generateActivatedUser("authenticateduser");
            User createdUser = userCreationService.createUser(new ManagedUserVM(user));
            // Password Data
            String updatedPassword = "12345678password-reset-init.component.spec.ts";

            PasswordChangeDTO pwChange = new PasswordChangeDTO(passwordService.decryptPassword(createdUser.getPassword()), updatedPassword);
            // make request
            request.postWithoutLocation("/api/account/change-password", pwChange, HttpStatus.FORBIDDEN, null);
        });
    }

    @Test
    @WithMockUser(username = "authenticateduser")
    public void changePasswordSaml2Disabled() throws Throwable {
        testWithRegistrationDisabled(() -> {
            testWithChangedConfig("saml2EnablePassword", Optional.of(Boolean.FALSE), () -> {
                // create user in repo
                User user = ModelFactory.generateActivatedUser("authenticateduser");
                User createdUser = userCreationService.createUser(new ManagedUserVM(user));
                // Password Data
                String updatedPassword = "12345678password-reset-init.component.spec.ts";

                PasswordChangeDTO pwChange = new PasswordChangeDTO(passwordService.decryptPassword(createdUser.getPassword()), updatedPassword);
                // make request
                request.postWithoutLocation("/api/account/change-password", pwChange, HttpStatus.FORBIDDEN, null);
            });
        });
    }

    @Test
    @WithMockUser(username = "authenticateduser")
    public void changePasswordSaml2ConfigEmpty() throws Throwable {
        testWithRegistrationDisabled(() -> {
            testWithChangedConfig("saml2EnablePassword", Optional.empty(), () -> {
                // create user in repo
                User user = ModelFactory.generateActivatedUser("authenticateduser");
                User createdUser = userCreationService.createUser(new ManagedUserVM(user));
                // Password Data
                String updatedPassword = "12345678password-reset-init.component.spec.ts";

                PasswordChangeDTO pwChange = new PasswordChangeDTO(passwordService.decryptPassword(createdUser.getPassword()), updatedPassword);
                // make request
                request.postWithoutLocation("/api/account/change-password", pwChange, HttpStatus.FORBIDDEN, null);
            });
        });
    }

    @Test
    @WithMockUser(username = "authenticateduser")
    public void changePasswordInvalidPassword() throws Exception {
        User user = ModelFactory.generateActivatedUser("authenticateduser");
        User createdUser = userCreationService.createUser(new ManagedUserVM(user));
        String updatedPassword = "123";

        PasswordChangeDTO pwChange = new PasswordChangeDTO(passwordService.decryptPassword(createdUser.getPassword()), updatedPassword);
        // make request
        request.postWithoutLocation("/api/account/change-password", pwChange, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    //@WithMockUser("authenticateduser")
    public void passwordReset() throws Exception {
        // create user in repo
        User user = ModelFactory.generateActivatedUser("authenticateduser");
        User createdUser = userCreationService.createUser(new ManagedUserVM(user));

        // init password reset
        request.postWithoutLocation("/api/account/reset-password/init", createdUser.getEmail(), HttpStatus.OK, null);

        // check user data
        Optional<User> userPasswordResetInit = userRepository.findOneByEmailIgnoreCase(createdUser.getEmail());
        assertThat(userPasswordResetInit).isPresent();
        String resetKey = userPasswordResetInit.get().getResetKey();

        // finish password reset
        String newPassword = "password";
        KeyAndPasswordVM finishResetData = new KeyAndPasswordVM();
        finishResetData.setKey(resetKey);
        finishResetData.setNewPassword(newPassword);

        // finish password reset
        request.postWithoutLocation("/api/account/reset-password/finish", finishResetData, HttpStatus.OK, null);

        // get updated user
        Optional<User> userPasswordResetFinished = userRepository.findOneByLogin("authenticateduser");
        assertThat(userPasswordResetFinished).isPresent();
        assertThat(passwordService.decryptPassword(userPasswordResetFinished.get().getPassword())).isEqualTo(newPassword);
    }

    @Test
    @WithMockUser("authenticateduser")
    public void passwordResetInitRegistrationDisabled() throws Throwable {
        testWithRegistrationDisabled(() -> {
            // create user in repo
            User user = ModelFactory.generateActivatedUser("authenticateduser");
            User createdUser = userCreationService.createUser(new ManagedUserVM(user));
            // attempt password reset
            request.postWithoutLocation("/api/account/reset-password/init", createdUser.getEmail(), HttpStatus.FORBIDDEN, null);
        });
    }

    @Test
    @WithMockUser("authenticateduser")
    public void passwordResetInitSaml2Disabled() throws Throwable {
        testWithRegistrationDisabled(() -> {
            testWithChangedConfig("saml2EnablePassword", Optional.of(Boolean.FALSE), () -> {
                // create user in repo
                User user = ModelFactory.generateActivatedUser("authenticateduser");
                User createdUser = userCreationService.createUser(new ManagedUserVM(user));
                // attempt password reset
                request.postWithoutLocation("/api/account/reset-password/init", createdUser.getEmail(), HttpStatus.FORBIDDEN, null);
            });
        });
    }

    @Test
    @WithMockUser("authenticateduser")
    public void passwordResetFinishRegistrationDisabled() throws Throwable {
        testWithRegistrationDisabled(() -> {
            KeyAndPasswordVM finishResetData = new KeyAndPasswordVM();
            request.postWithoutLocation("/api/account/reset-password/finish", finishResetData, HttpStatus.FORBIDDEN, null);
        });
    }

    @Test
    @WithMockUser("authenticateduser")
    public void passwordResetFinishInvalidPassword() throws Throwable {
        KeyAndPasswordVM finishResetData = new KeyAndPasswordVM();
        finishResetData.setNewPassword("");
        request.postWithoutLocation("/api/account/reset-password/finish", finishResetData, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser("authenticateduser")
    public void passwordResetFinishInvalidKey() throws Throwable {
        KeyAndPasswordVM finishResetData = new KeyAndPasswordVM();
        finishResetData.setNewPassword("password");
        request.postWithoutLocation("/api/account/reset-password/finish", finishResetData, HttpStatus.FORBIDDEN, null);
    }
}
