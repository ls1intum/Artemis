package de.tum.cit.aet.artemis.core.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.PasswordChangeDTO;
import de.tum.cit.aet.artemis.core.dto.UserDTO;
import de.tum.cit.aet.artemis.core.dto.vm.KeyAndPasswordVM;
import de.tum.cit.aet.artemis.core.dto.vm.ManagedUserVM;
import de.tum.cit.aet.artemis.core.service.AccountService;
import de.tum.cit.aet.artemis.core.service.user.PasswordService;
import de.tum.cit.aet.artemis.core.user.util.UserFactory;
import de.tum.cit.aet.artemis.core.util.ConfigUtil;
import de.tum.cit.aet.artemis.core.web.AccountResource;
import de.tum.cit.aet.artemis.core.web.open.PublicAccountResource;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Tests {@link AccountResource}. Several Tests rely on overwriting AccountResource.registrationEnabled and other attributes with reflections. Any changes to the internal
 * structure will cause these tests to fail.
 */
class AccountResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    public static final String AUTHENTICATEDUSER = "authenticateduser";

    @Autowired
    private PublicAccountResource publicAccountResource;

    @Autowired
    private AccountService accountService;

    @Autowired
    private PasswordService passwordService;

    private void testWithRegistrationDisabled(Executable test) throws Throwable {
        ConfigUtil.testWithChangedConfig(accountService, "registrationEnabled", Optional.of(Boolean.FALSE), test);
    }

    private String getValidPassword() {
        // verify configuration is valid
        assertThat(Constants.PASSWORD_MIN_LENGTH).isLessThan(Constants.PASSWORD_MAX_LENGTH);
        assertThat(Constants.PASSWORD_MIN_LENGTH).isNotNegative();

        // empty password will always get rejected
        return "a".repeat(Math.max(1, Constants.PASSWORD_MIN_LENGTH));
    }

    @Test
    void registerAccount() throws Exception {
        String login = "ab123cde";
        String password = getValidPassword();
        // setup user
        User user = UserFactory.generateActivatedUser(login);
        ManagedUserVM userVM = new ManagedUserVM(user);
        userVM.setPassword(password);

        // make request
        request.postWithoutLocation("/api/public/register", userVM, HttpStatus.CREATED, null);
    }

    @Test
    void registerAccountTooLongPassword() throws Exception {
        // setup user
        User user = UserFactory.generateActivatedUser("ab123cdf");
        ManagedUserVM userVM = new ManagedUserVM(user);
        assertThat(Constants.PASSWORD_MAX_LENGTH).isPositive();
        userVM.setPassword("e".repeat(Constants.PASSWORD_MAX_LENGTH + 1));

        // make request
        request.postWithoutLocation("/api/public/register", userVM, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    void registerAccountTooShortPassword() throws Exception {
        // setup user
        User user = UserFactory.generateActivatedUser("ab123cdg");
        ManagedUserVM userVM = new ManagedUserVM(user);
        assertThat(Constants.PASSWORD_MIN_LENGTH).isNotNegative();
        if (Constants.PASSWORD_MIN_LENGTH == 0) {
            // if all lengths are accepted it cannot be tested for too short passwords
            return;
        }
        userVM.setPassword("e".repeat(Constants.PASSWORD_MIN_LENGTH - 1));

        // make request
        request.postWithoutLocation("/api/public/register", userVM, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    void registerAccountEmptyPassword() throws Exception {
        // setup user
        User user = UserFactory.generateActivatedUser("ab123cdh");
        ManagedUserVM userVM = new ManagedUserVM(user);
        userVM.setPassword("");

        // make request
        request.postWithoutLocation("/api/public/register", userVM, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    void registerAccountRegistrationDisabled() throws Throwable {
        testWithRegistrationDisabled(() -> {
            // setup user
            User user = UserFactory.generateActivatedUser("ab123cdi");
            ManagedUserVM userVM = new ManagedUserVM(user);
            userVM.setPassword(getValidPassword());

            // make request
            request.postWithoutLocation("/api/public/register", userVM, HttpStatus.FORBIDDEN, null);
        });
    }

    @Test
    void registerAccountRegistrationConfigEmpty() throws Throwable {
        ConfigUtil.testWithChangedConfig(accountService, "registrationEnabled", Optional.empty(), () -> {
            // setup user
            User user = UserFactory.generateActivatedUser("ab123cdj");
            ManagedUserVM userVM = new ManagedUserVM(user);
            userVM.setPassword(getValidPassword());

            // make request
            request.postWithoutLocation("/api/public/register", userVM, HttpStatus.FORBIDDEN, null);
        });
    }

    @Test
    void registerAccountInvalidEmail() throws Throwable {
        // Inject email-pattern to be independent of the config
        ConfigUtil.testWithChangedConfig(publicAccountResource, "allowedEmailPattern", Optional.of(Pattern.compile("[a-zA-Z0-9_\\-.+]+@[a-zA-Z0-9_\\-.]+\\.[a-zA-Z]{2,5}")), () -> {
            // setup user
            User user = UserFactory.generateActivatedUser("ab123cdk");
            user.setEmail("-");
            ManagedUserVM userVM = new ManagedUserVM(user);
            userVM.setPassword(getValidPassword());

            // make request
            request.postWithoutLocation("/api/public/register", userVM, HttpStatus.BAD_REQUEST, null);
        });
    }

    @Test
    void registerAccountEmptyEmailPattern() throws Throwable {
        ConfigUtil.testWithChangedConfig(publicAccountResource, "allowedEmailPattern", Optional.empty(), () -> {
            // setup user
            User user = UserFactory.generateActivatedUser("ab123cdl");
            user.setEmail("-");
            ManagedUserVM userVM = new ManagedUserVM(user);
            userVM.setPassword(getValidPassword());

            // make request -> validation fails due to empty email is validated against min size
            request.postWithoutLocation("/api/public/register", userVM, HttpStatus.BAD_REQUEST, null);
        });
    }

    @Test
    void activateAccount() throws Exception {
        // create unactivated user in repo
        String testActivationKey = "testActivationKey";
        User user = UserFactory.generateActivatedUser("ab123cdm");
        user.setActivated(false);
        user.setActivationKey(testActivationKey);
        user = userTestRepository.save(user);

        // make request
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("key", testActivationKey);
        request.get("/api/public/activate", HttpStatus.OK, String.class, params);

        // check result
        Optional<User> updatedUser = userTestRepository.findById(user.getId());
        assertThat(updatedUser).isPresent();
        assertThat(updatedUser.get()).isNotNull();
        assertThat(updatedUser.get().getActivated()).isTrue();
        assertThat(updatedUser.get().getActivationKey()).isNull();
    }

    @Test
    void activateAccountRegistrationDisabled() throws Throwable {
        testWithRegistrationDisabled(() -> {
            String testActivationKey = "testActivationKey";

            // make request
            LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("key", testActivationKey);
            request.get("/api/public/activate", HttpStatus.FORBIDDEN, String.class, params);
        });
    }

    @Test
    void activateAccountNoUser() throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("key", "");
        request.get("/api/public/activate", HttpStatus.INTERNAL_SERVER_ERROR, String.class, params);
    }

    @Test
    @WithMockUser("authenticatedUser")
    void isAuthenticated() throws Exception {
        String userLogin = request.get("/api/public/authenticate", HttpStatus.OK, String.class);
        assertThat(userLogin).isNotNull().isEqualTo("authenticatedUser");
    }

    @Test
    void isAuthenticatedWithoutLoggedInUser() throws Exception {
        String user = request.get("/api/public/authenticate", HttpStatus.OK, String.class);
        assertThat(user).isEmpty();
    }

    @Test
    @WithMockUser(AUTHENTICATEDUSER)
    void getAccount() throws Exception {
        // create user in repo
        userUtilService.createAndSaveUser(AUTHENTICATEDUSER);
        UserDTO account = request.get("/api/public/account", HttpStatus.OK, UserDTO.class);
        assertThat(account).isNotNull();
    }

    @Test
    @WithAnonymousUser
    void getAccountWithoutLoggedInUser() throws Exception {
        UserDTO user = request.get("/api/public/account", HttpStatus.NO_CONTENT, UserDTO.class);
        assertThat(user).isNull();
    }

    @Test
    @WithMockUser(username = AUTHENTICATEDUSER)
    void saveAccount() throws Exception {
        String updatedFirstName = "UpdatedFirstName";
        // create user in repo
        User user = userUtilService.createAndSaveUser(AUTHENTICATEDUSER);

        // update FirstName
        user.setFirstName(updatedFirstName);

        // make request
        request.put("/api/account", new UserDTO(user), HttpStatus.OK);

        // check if update successful
        Optional<User> updatedUser = userTestRepository.findOneByLogin(AUTHENTICATEDUSER);
        assertThat(updatedUser).isPresent();
        assertThat(updatedUser.get().getFirstName()).isEqualTo(updatedFirstName);
    }

    @Test
    @WithMockUser(username = AUTHENTICATEDUSER)
    void saveAccountRegistrationDisabled() throws Throwable {
        testWithRegistrationDisabled(() -> {
            // create user in repo
            User user = userUtilService.createAndSaveUser(AUTHENTICATEDUSER);
            // update FirstName
            String updatedFirstName = "UpdatedFirstName";
            user.setFirstName(updatedFirstName);

            // make request
            request.put("/api/account", new UserDTO(user), HttpStatus.FORBIDDEN);
        });
    }

    @Test
    @WithMockUser(username = AUTHENTICATEDUSER)
    void saveAccountEmailInUse() throws Exception {
        // create user in repo
        User user = userUtilService.createAndSaveUser(AUTHENTICATEDUSER);
        User userSameEmail = userUtilService.createAndSaveUser("sameemail");
        // update Email to one already used
        user.setEmail(userSameEmail.getEmail());

        // make request
        request.put("/api/account", new UserDTO(user), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = AUTHENTICATEDUSER)
    void changePassword() throws Exception {
        // Password Data
        String updatedPassword = "12345678";
        // create user in repo
        User user = userUtilService.createAndSaveUser(AUTHENTICATEDUSER, passwordService.hashPassword(UserFactory.USER_PASSWORD));

        PasswordChangeDTO pwChange = new PasswordChangeDTO(UserFactory.USER_PASSWORD, updatedPassword);
        // make request
        request.postWithoutLocation("/api/account/change-password", pwChange, HttpStatus.OK, null);

        // check if update successful
        Optional<User> updatedUser = userTestRepository.findOneByLogin(AUTHENTICATEDUSER);
        assertThat(updatedUser).isPresent();
        assertThat(passwordService.checkPasswordMatch(updatedPassword, updatedUser.get().getPassword())).isTrue();
    }

    @Test
    @WithMockUser(username = AUTHENTICATEDUSER)
    void changePasswordExternalUser() throws Throwable {
        String newPassword = getValidPassword();
        // create user in repo
        User user = userUtilService.createAndSaveUser(AUTHENTICATEDUSER);
        user.setInternal(false);
        userTestRepository.save(user);

        // Password Data
        String updatedPassword = "12345678";

        PasswordChangeDTO pwChange = new PasswordChangeDTO(UserFactory.USER_PASSWORD, updatedPassword);
        // make request
        request.postWithoutLocation("/api/account/change-password", pwChange, HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = AUTHENTICATEDUSER)
    void changePasswordInvalidPassword() throws Exception {
        userUtilService.createAndSaveUser(AUTHENTICATEDUSER);
        String updatedPassword = "";
        PasswordChangeDTO pwChange = new PasswordChangeDTO(UserFactory.USER_PASSWORD, updatedPassword);
        // make request
        request.postWithoutLocation("/api/account/change-password", pwChange, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = AUTHENTICATEDUSER)
    void changePasswordSamePassword() throws Exception {
        userUtilService.createAndSaveUser(AUTHENTICATEDUSER);
        PasswordChangeDTO pwChange = new PasswordChangeDTO(UserFactory.USER_PASSWORD, UserFactory.USER_PASSWORD);
        // make request
        request.postWithoutLocation("/api/account/change-password", pwChange, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = AUTHENTICATEDUSER)
    void changeLanguageKey() throws Exception {
        // create user in repo
        User user = userUtilService.createAndSaveUser(AUTHENTICATEDUSER);
        user.setLangKey("en");
        User storedUser = userTestRepository.save(user);
        assertThat(storedUser.getLangKey()).isEqualTo("en");

        // make request
        request.postStringWithoutLocation("/api/public/account/change-language", "de", HttpStatus.OK, null);

        // check result
        Optional<User> updatedUser = userTestRepository.findOneByLogin(AUTHENTICATEDUSER);
        assertThat(updatedUser).isPresent();
        assertThat(updatedUser.get().getLangKey()).isEqualTo("de");
    }

    @Test
    @WithMockUser(username = AUTHENTICATEDUSER)
    void changeLanguageKeyNotSupported() throws Exception {
        // create user in repo
        User user = userUtilService.createAndSaveUser(AUTHENTICATEDUSER);
        user.setLangKey("en");
        User storedUser = userTestRepository.save(user);
        assertThat(storedUser.getLangKey()).isEqualTo("en");

        // make request
        request.postStringWithoutLocation("/api/public/account/change-language", "loremIpsum", HttpStatus.BAD_REQUEST, null);
    }

    @Test
    void passwordResetByEmail() throws Exception {
        String newPassword = getValidPassword();
        // create user in repo
        User createdUser = userUtilService.createAndSaveUser(AUTHENTICATEDUSER);

        Optional<User> userBefore = userTestRepository.findOneByEmailIgnoreCase(createdUser.getEmail());
        assertThat(userBefore).isPresent();
        String resetKeyBefore = userBefore.get().getResetKey();

        request.postStringWithoutLocation("/api/public/account/reset-password/init", createdUser.getEmail(), HttpStatus.OK, null);

        verifyPasswordReset(createdUser, resetKeyBefore);
    }

    @Test
    void passwordResetByUsername() throws Exception {
        String newPassword = getValidPassword();
        // create user in repo
        User createdUser = userUtilService.createAndSaveUser(AUTHENTICATEDUSER);

        Optional<User> userBefore = userTestRepository.findOneByEmailIgnoreCase(createdUser.getEmail());
        assertThat(userBefore).isPresent();
        String resetKeyBefore = userBefore.get().getResetKey();

        request.postStringWithoutLocation("/api/public/account/reset-password/init", createdUser.getLogin(), HttpStatus.OK, null);
        verifyPasswordReset(createdUser, resetKeyBefore);
    }

    private void verifyPasswordReset(User createdUser, String resetKeyBefore) throws Exception {
        // check user data
        Optional<User> userPasswordResetInit = userTestRepository.findOneByEmailIgnoreCase(createdUser.getEmail());
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
        request.postWithoutLocation("/api/public/account/reset-password/finish", finishResetData, HttpStatus.OK, null);

        // get updated user
        Optional<User> userPasswordResetFinished = userTestRepository.findOneByLogin(AUTHENTICATEDUSER);
        assertThat(userPasswordResetFinished).isPresent();
        assertThat(passwordService.checkPasswordMatch(newPassword, userPasswordResetFinished.get().getPassword())).isTrue();
    }

    @Test
    void passwordResetInvalidEmail() throws Exception {
        // create user in repo
        User createdUser = userUtilService.createAndSaveUser(AUTHENTICATEDUSER);

        Optional<User> userBefore = userTestRepository.findOneByEmailIgnoreCase(createdUser.getEmail());
        assertThat(userBefore).isPresent();
        String resetKeyBefore = userBefore.get().getResetKey();

        // init password reset
        request.postStringWithoutLocation("/api/public/account/reset-password/init", "invalidemail", HttpStatus.OK, null);

        // check user data
        Optional<User> userPasswordResetInit = userTestRepository.findOneByEmailIgnoreCase(createdUser.getEmail());
        assertThat(userPasswordResetInit).isPresent();
        String resetKey = userPasswordResetInit.get().getResetKey();

        // verify key has not been changed by the invalid request
        assertThat(resetKey).isEqualTo(resetKeyBefore);
    }

    @Test
    void passwordResetInitUserExternal() throws Throwable {
        String login = "testLogin";
        String email = "test@mail.com";
        User user = new User();
        user.setLogin(login);
        user.setEmail(email);
        user.setInternal(false);
        userTestRepository.saveAndFlush(user);

        request.postStringWithoutLocation("/api/public/account/reset-password/init", email, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(AUTHENTICATEDUSER)
    void passwordResetFinishInvalidPassword() throws Throwable {
        KeyAndPasswordVM finishResetData = new KeyAndPasswordVM();
        finishResetData.setNewPassword("");
        request.postWithoutLocation("/api/public/account/reset-password/finish", finishResetData, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(AUTHENTICATEDUSER)
    void passwordResetFinishInvalidKey() throws Throwable {
        KeyAndPasswordVM finishResetData = new KeyAndPasswordVM();
        finishResetData.setNewPassword(getValidPassword());
        request.postWithoutLocation("/api/public/account/reset-password/finish", finishResetData, HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = AUTHENTICATEDUSER)
    void acceptExternalLLMUsageSuccessful() throws Exception {
        // Create user in repo with null timestamp
        User user = userUtilService.createAndSaveUser(AUTHENTICATEDUSER);
        user.setExternalLLMUsageAcceptedTimestamp(null);
        userTestRepository.save(user);

        request.put("/api/users/accept-external-llm-usage", null, HttpStatus.OK);

        // Verify timestamp was set
        Optional<User> updatedUser = userTestRepository.findOneByLogin(AUTHENTICATEDUSER);
        assertThat(updatedUser).isPresent();
        assertThat(updatedUser.get().getExternalLLMUsageAcceptedTimestamp()).isNotNull();
    }

    @Test
    @WithMockUser(username = AUTHENTICATEDUSER)
    void acceptExternalLLMUsageAlreadyAccepted() throws Exception {
        // Create user in repo with existing timestamp
        User user = userUtilService.createAndSaveUser(AUTHENTICATEDUSER);
        user.setExternalLLMUsageAcceptedTimestamp(ZonedDateTime.now());
        userTestRepository.save(user);

        request.put("/api/users/accept-external-llm-usage", null, HttpStatus.BAD_REQUEST);

        // Verify timestamp wasn't changed
        Optional<User> unchangedUser = userTestRepository.findOneByLogin(AUTHENTICATEDUSER);
        assertThat(unchangedUser).isPresent();
        assertThat(unchangedUser.get().getExternalLLMUsageAcceptedTimestamp()).isEqualTo(user.getExternalLLMUsageAcceptedTimestamp());
    }
}
