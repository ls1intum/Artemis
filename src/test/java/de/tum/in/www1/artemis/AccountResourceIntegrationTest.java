package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.dto.PasswordChangeDTO;
import de.tum.in.www1.artemis.service.dto.UserDTO;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.service.user.UserCreationService;
import de.tum.in.www1.artemis.util.ConfigUtil;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.AccountResource;
import de.tum.in.www1.artemis.web.rest.vm.KeyAndPasswordVM;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;

/**
 * Tests {@link AccountResource}. Several Tests rely on overwriting AccountResource.registrationEnabled and other attributes with reflections. Any changes to the internal
 * structure will cause these tests to fail.
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

    @BeforeEach
    public void setup() {
        bitbucketRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    public void resetDatabase() {
        bitbucketRequestMockProvider.reset();
        database.resetDatabase();
    }

    private void testWithRegistrationDisabled(Executable test) throws Throwable {
        ConfigUtil.testWithChangedConfig(accountResource, "registrationEnabled", Optional.of(Boolean.FALSE), test);
    }

    private String getValidPassword() {
        // verify configuration is valid
        assertThat(Constants.PASSWORD_MIN_LENGTH).isLessThan(Constants.PASSWORD_MAX_LENGTH);
        assertThat(Constants.PASSWORD_MIN_LENGTH).isNotNegative();

        // empty password will always get rejected
        return "a".repeat(Math.max(1, Constants.PASSWORD_MIN_LENGTH));
    }

    @Test
    public void registerAccount() throws Exception {
        String login = "abd123cd";
        String password = getValidPassword();
        // setup user
        User user = ModelFactory.generateActivatedUser(login);
        ManagedUserVM userVM = new ManagedUserVM(user);
        userVM.setPassword(password);

        bitbucketRequestMockProvider.mockUserDoesNotExist(login);
        bitbucketRequestMockProvider.mockCreateUser(login, password, login + "@test.de", login + "First " + login + "Last");
        bitbucketRequestMockProvider.mockAddUserToGroups();

        // make request
        request.postWithoutLocation("/api/register", userVM, HttpStatus.CREATED, null);
    }

    @Test
    public void registerAccountTooLongPassword() throws Exception {
        // setup user
        User user = ModelFactory.generateActivatedUser("ab123cd");
        ManagedUserVM userVM = new ManagedUserVM(user);
        assertThat(Constants.PASSWORD_MAX_LENGTH).isPositive();
        userVM.setPassword("e".repeat(Constants.PASSWORD_MAX_LENGTH + 1));

        // make request
        request.postWithoutLocation("/api/register", userVM, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    public void registerAccountTooShortPassword() throws Exception {
        // setup user
        User user = ModelFactory.generateActivatedUser("ab123cd");
        ManagedUserVM userVM = new ManagedUserVM(user);
        assertThat(Constants.PASSWORD_MIN_LENGTH).isNotNegative();
        if (Constants.PASSWORD_MIN_LENGTH == 0) {
            // if all lengths are accepted it cannot be tested for too short passwords
            return;
        }
        userVM.setPassword("e".repeat(Constants.PASSWORD_MIN_LENGTH - 1));

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
            userVM.setPassword(getValidPassword());

            // make request
            request.postWithoutLocation("/api/register", userVM, HttpStatus.FORBIDDEN, null);
        });
    }

    @Test
    public void registerAccountRegistrationConfigEmpty() throws Throwable {
        ConfigUtil.testWithChangedConfig(accountResource, "registrationEnabled", Optional.empty(), () -> {
            // setup user
            User user = ModelFactory.generateActivatedUser("ab123cd");
            ManagedUserVM userVM = new ManagedUserVM(user);
            userVM.setPassword(getValidPassword());

            // make request
            request.postWithoutLocation("/api/register", userVM, HttpStatus.FORBIDDEN, null);
        });
    }

    @Test
    public void registerAccountInvalidEmail() throws Throwable {
        // Inject email-pattern to be independent of the config
        ConfigUtil.testWithChangedConfig(accountResource, "allowedEmailPattern", Optional.of(Pattern.compile("[a-zA-Z0-9_\\-.+]+@[a-zA-Z0-9_\\-.]+\\.[a-zA-Z]{2,5}")), () -> {
            // setup user
            User user = ModelFactory.generateActivatedUser("ab123cd");
            user.setEmail("-");
            ManagedUserVM userVM = new ManagedUserVM(user);
            userVM.setPassword(getValidPassword());

            // make request
            request.postWithoutLocation("/api/register", userVM, HttpStatus.BAD_REQUEST, null);
        });
    }

    @Test
    public void registerAccountEmptyEmailPattern() throws Throwable {
        ConfigUtil.testWithChangedConfig(accountResource, "allowedEmailPattern", Optional.empty(), () -> {
            // setup user
            User user = ModelFactory.generateActivatedUser("ab123cd");
            user.setEmail("-");
            ManagedUserVM userVM = new ManagedUserVM(user);
            userVM.setPassword(getValidPassword());

            // make request -> validation fails due to empty email is validated against min size
            request.postWithoutLocation("/api/register", userVM, HttpStatus.BAD_REQUEST, null);
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
        assertThat(userLogin).isNotNull().isEqualTo("authenticatedUser");
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
    public void saveAccount() throws Exception {
        String updatedFirstName = "UpdatedFirstName";
        // create user in repo
        User user = ModelFactory.generateActivatedUser("authenticateduser");
        bitbucketRequestMockProvider.mockUserExists("authenticateduser");
        bitbucketRequestMockProvider.mockUpdateUserDetails(user.getLogin(), user.getEmail(), updatedFirstName + " " + user.getLastName());
        bitbucketRequestMockProvider.mockUpdateUserPassword(user.getLogin(), ModelFactory.USER_PASSWORD, true, true);
        User createdUser = userCreationService.createUser(new ManagedUserVM(user, user.getPassword()));

        // update FirstName
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
            bitbucketRequestMockProvider.mockUserExists("authenticateduser");
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
        bitbucketRequestMockProvider.mockUserExists("authenticateduser");
        bitbucketRequestMockProvider.mockUserExists("sameemail");
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
        // Password Data
        String updatedPassword = "12345678password-reset-init.component.spec.ts";
        // create user in repo
        User user = ModelFactory.generateActivatedUser("authenticateduser");
        bitbucketRequestMockProvider.mockUserExists("authenticateduser");
        bitbucketRequestMockProvider.mockUpdateUserDetails(user.getLogin(), user.getEmail(), user.getName());
        bitbucketRequestMockProvider.mockUpdateUserPassword(user.getLogin(), updatedPassword, true, true);
        userCreationService.createUser(new ManagedUserVM(user, ModelFactory.USER_PASSWORD));

        PasswordChangeDTO pwChange = new PasswordChangeDTO(ModelFactory.USER_PASSWORD, updatedPassword);
        // make request
        request.postWithoutLocation("/api/account/change-password", pwChange, HttpStatus.OK, null);

        // check if update successful
        Optional<User> updatedUser = userRepository.findOneByLogin("authenticateduser");
        assertThat(updatedUser).isPresent();
        assertThat(passwordService.checkPasswordMatch(updatedPassword, updatedUser.get().getPassword())).isTrue();
    }

    @Test
    @WithMockUser(username = "authenticateduser")
    public void changePasswordExternalUser() throws Throwable {
        String newPassword = getValidPassword();
        // create user in repo
        User user = ModelFactory.generateActivatedUser("authenticateduser");
        bitbucketRequestMockProvider.mockUserExists("authenticateduser");
        bitbucketRequestMockProvider.mockUpdateUserDetails(user.getLogin(), user.getEmail(), user.getName());
        bitbucketRequestMockProvider.mockUpdateUserPassword(user.getLogin(), newPassword, true, true);
        User createdUser = userCreationService.createUser(new ManagedUserVM(user));
        createdUser.setInternal(false);
        userRepository.save(createdUser);

        // Password Data
        String updatedPassword = "12345678password-reset-init.component.spec.ts";

        PasswordChangeDTO pwChange = new PasswordChangeDTO(ModelFactory.USER_PASSWORD, updatedPassword);
        // make request
        request.postWithoutLocation("/api/account/change-password", pwChange, HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = "authenticateduser")
    public void changePasswordInvalidPassword() throws Exception {
        User user = ModelFactory.generateActivatedUser("authenticateduser");
        bitbucketRequestMockProvider.mockUserExists("authenticateduser");
        userCreationService.createUser(new ManagedUserVM(user));
        String updatedPassword = "";

        PasswordChangeDTO pwChange = new PasswordChangeDTO(ModelFactory.USER_PASSWORD, updatedPassword);
        // make request
        request.postWithoutLocation("/api/account/change-password", pwChange, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = "authenticateduser")
    public void changePasswordSamePassword() throws Exception {
        User user = ModelFactory.generateActivatedUser("authenticateduser");
        bitbucketRequestMockProvider.mockUserExists("authenticateduser");
        userCreationService.createUser(new ManagedUserVM(user));

        PasswordChangeDTO pwChange = new PasswordChangeDTO(ModelFactory.USER_PASSWORD, ModelFactory.USER_PASSWORD);
        // make request
        request.postWithoutLocation("/api/account/change-password", pwChange, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = "authenticateduser")
    public void changeLanguageKey() throws Exception {
        // create user in repo
        User user = ModelFactory.generateActivatedUser("authenticateduser");
        user.setLangKey("en");
        bitbucketRequestMockProvider.mockUserExists("authenticateduser");
        User createdUser = userCreationService.createUser(new ManagedUserVM(user));
        assertThat(createdUser.getLangKey()).isEqualTo("en");

        // make request
        request.postWithoutLocation("/api/account/change-language", "de", HttpStatus.OK, null);

        // check result
        Optional<User> updatedUser = userRepository.findOneByLogin("authenticateduser");
        assertThat(updatedUser).isPresent();
        assertThat(updatedUser.get().getLangKey()).isEqualTo("de");
    }

    @Test
    @WithMockUser(username = "authenticateduser")
    public void changeLanguageKeyNotSupported() throws Exception {
        // create user in repo
        User user = ModelFactory.generateActivatedUser("authenticateduser");
        user.setLangKey("en");
        bitbucketRequestMockProvider.mockUserExists("authenticateduser");
        User createdUser = userCreationService.createUser(new ManagedUserVM(user));
        assertThat(createdUser.getLangKey()).isEqualTo("en");

        // make request
        request.postWithoutLocation("/api/account/change-language", "loremIpsum", HttpStatus.BAD_REQUEST, null);
    }

    @Test
    public void passwordResetByEmail() throws Exception {
        String newPassword = getValidPassword();
        // create user in repo
        User user = ModelFactory.generateActivatedUser("authenticateduser");
        bitbucketRequestMockProvider.mockUserExists("authenticateduser");
        bitbucketRequestMockProvider.mockUpdateUserDetails(user.getLogin(), user.getEmail(), user.getName());
        bitbucketRequestMockProvider.mockUpdateUserPassword(user.getLogin(), newPassword, true, true);
        User createdUser = userCreationService.createUser(new ManagedUserVM(user));

        Optional<User> userBefore = userRepository.findOneByEmailIgnoreCase(createdUser.getEmail());
        assertThat(userBefore).isPresent();
        String resetKeyBefore = userBefore.get().getResetKey();

        var req = MockMvcRequestBuilders.post(new URI("/api/account/reset-password/init")).contentType(MediaType.APPLICATION_JSON).content(createdUser.getEmail());
        request.getMvc().perform(req).andExpect(status().is(HttpStatus.OK.value())).andReturn();
        ReflectionTestUtils.invokeMethod(request, "restoreSecurityContext");

        verifyPasswordReset(createdUser, resetKeyBefore);
    }

    @Test
    public void passwordResetByUsername() throws Exception {
        String newPassword = getValidPassword();
        // create user in repo
        User user = ModelFactory.generateActivatedUser("authenticateduser");
        bitbucketRequestMockProvider.mockUserExists("authenticateduser");
        bitbucketRequestMockProvider.mockUpdateUserDetails(user.getLogin(), user.getEmail(), user.getName());
        bitbucketRequestMockProvider.mockUpdateUserPassword(user.getLogin(), newPassword, true, true);
        User createdUser = userCreationService.createUser(new ManagedUserVM(user));

        Optional<User> userBefore = userRepository.findOneByEmailIgnoreCase(createdUser.getEmail());
        assertThat(userBefore).isPresent();
        String resetKeyBefore = userBefore.get().getResetKey();

        var req = MockMvcRequestBuilders.post(new URI("/api/account/reset-password/init")).contentType(MediaType.APPLICATION_JSON).content(createdUser.getLogin());
        request.getMvc().perform(req).andExpect(status().is(HttpStatus.OK.value())).andReturn();
        ReflectionTestUtils.invokeMethod(request, "restoreSecurityContext");

        verifyPasswordReset(createdUser, resetKeyBefore);
    }

    private void verifyPasswordReset(User createdUser, String resetKeyBefore) throws Exception {
        // check user data
        Optional<User> userPasswordResetInit = userRepository.findOneByEmailIgnoreCase(createdUser.getEmail());
        assertThat(userPasswordResetInit).isPresent();
        String resetKey = userPasswordResetInit.get().getResetKey();

        // verify key has been changed by the request
        assertThat(resetKey).isNotEqualTo(resetKeyBefore);

        // finish password reset
        String newPassword = getValidPassword();
        KeyAndPasswordVM finishResetData = new KeyAndPasswordVM();
        finishResetData.setKey(resetKey);
        finishResetData.setNewPassword(newPassword);

        // finish password reset
        request.postWithoutLocation("/api/account/reset-password/finish", finishResetData, HttpStatus.OK, null);

        // get updated user
        Optional<User> userPasswordResetFinished = userRepository.findOneByLogin("authenticateduser");
        assertThat(userPasswordResetFinished).isPresent();
        assertThat(passwordService.checkPasswordMatch(newPassword, userPasswordResetFinished.get().getPassword())).isTrue();
    }

    @Test
    public void passwordResetInvalidEmail() throws Exception {
        // create user in repo
        User user = ModelFactory.generateActivatedUser("authenticateduser");
        bitbucketRequestMockProvider.mockUserExists("authenticateduser");
        User createdUser = userCreationService.createUser(new ManagedUserVM(user));

        Optional<User> userBefore = userRepository.findOneByEmailIgnoreCase(createdUser.getEmail());
        assertThat(userBefore).isPresent();
        String resetKeyBefore = userBefore.get().getResetKey();

        // init password reset
        var req = MockMvcRequestBuilders.post(new URI("/api/account/reset-password/init")).contentType(MediaType.APPLICATION_JSON).content("invalidemail");
        request.getMvc().perform(req).andExpect(status().is(HttpStatus.OK.value())).andReturn();
        ReflectionTestUtils.invokeMethod(request, "restoreSecurityContext");

        // check user data
        Optional<User> userPasswordResetInit = userRepository.findOneByEmailIgnoreCase(createdUser.getEmail());
        assertThat(userPasswordResetInit).isPresent();
        String resetKey = userPasswordResetInit.get().getResetKey();

        // verify key has not been changed by the invalid request
        assertThat(resetKey).isEqualTo(resetKeyBefore);
    }

    @Test
    public void passwordResetInitUserExternal() throws Throwable {
        String login = "testLogin";
        String email = "test@mail.com";
        User user = new User();
        user.setLogin(login);
        user.setEmail(email);
        user.setInternal(false);
        userRepository.saveAndFlush(user);

        var req = MockMvcRequestBuilders.post(new URI("/api/account/reset-password/init")).contentType(MediaType.APPLICATION_JSON).content(email);
        request.getMvc().perform(req).andExpect(status().is(HttpStatus.BAD_REQUEST.value())).andReturn();
        ReflectionTestUtils.invokeMethod(request, "restoreSecurityContext");
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
        finishResetData.setNewPassword(getValidPassword());
        request.postWithoutLocation("/api/account/reset-password/finish", finishResetData, HttpStatus.FORBIDDEN, null);
    }
}
