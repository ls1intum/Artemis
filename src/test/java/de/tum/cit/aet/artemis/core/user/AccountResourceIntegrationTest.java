package de.tum.cit.aet.artemis.core.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.PasswordChangeDTO;
import de.tum.cit.aet.artemis.core.dto.SelectedLLMUsageDTO;
import de.tum.cit.aet.artemis.core.dto.UserDTO;
import de.tum.cit.aet.artemis.core.dto.vm.KeyAndPasswordVM;
import de.tum.cit.aet.artemis.core.dto.vm.ManagedUserVM;
import de.tum.cit.aet.artemis.core.service.AccountService;
import de.tum.cit.aet.artemis.core.service.user.PasswordService;
import de.tum.cit.aet.artemis.core.user.util.UserFactory;
import de.tum.cit.aet.artemis.core.util.ConfigUtil;
import de.tum.cit.aet.artemis.core.util.PasskeyCredentialUtilService;
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

    @Autowired
    private PasskeyCredentialUtilService passkeyCredentialUtilService;

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
        request.postWithoutLocation("/api/core/public/register", userVM, HttpStatus.CREATED, null);
    }

    @Test
    void registerAccountTooLongPassword() throws Exception {
        // setup user
        User user = UserFactory.generateActivatedUser("ab123cdf");
        ManagedUserVM userVM = new ManagedUserVM(user);
        assertThat(Constants.PASSWORD_MAX_LENGTH).isPositive();
        userVM.setPassword("e".repeat(Constants.PASSWORD_MAX_LENGTH + 1));

        // make request
        request.postWithoutLocation("/api/core/public/register", userVM, HttpStatus.BAD_REQUEST, null);
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
        request.postWithoutLocation("/api/core/public/register", userVM, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    void registerAccountEmptyPassword() throws Exception {
        // setup user
        User user = UserFactory.generateActivatedUser("ab123cdh");
        ManagedUserVM userVM = new ManagedUserVM(user);
        userVM.setPassword("");

        // make request
        request.postWithoutLocation("/api/core/public/register", userVM, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    void registerAccountRegistrationDisabled() throws Throwable {
        testWithRegistrationDisabled(() -> {
            // setup user
            User user = UserFactory.generateActivatedUser("ab123cdi");
            ManagedUserVM userVM = new ManagedUserVM(user);
            userVM.setPassword(getValidPassword());

            // make request
            request.postWithoutLocation("/api/core/public/register", userVM, HttpStatus.FORBIDDEN, null);
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
            request.postWithoutLocation("/api/core/public/register", userVM, HttpStatus.FORBIDDEN, null);
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
            request.postWithoutLocation("/api/core/public/register", userVM, HttpStatus.BAD_REQUEST, null);
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
            request.postWithoutLocation("/api/core/public/register", userVM, HttpStatus.BAD_REQUEST, null);
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
        request.get("/api/core/public/activate", HttpStatus.OK, String.class, params);

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
            request.get("/api/core/public/activate", HttpStatus.FORBIDDEN, String.class, params);
        });
    }

    @Test
    void activateAccountNoUser() throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("key", "");
        request.get("/api/core/public/activate", HttpStatus.INTERNAL_SERVER_ERROR, String.class, params);
    }

    @Test
    @WithMockUser("authenticatedUser")
    void isAuthenticated() throws Exception {
        String userLogin = request.get("/api/core/public/authenticate", HttpStatus.OK, String.class);
        assertThat(userLogin).isNotNull().isEqualTo("authenticatedUser");
    }

    @Test
    void isAuthenticatedWithoutLoggedInUser() throws Exception {
        String user = request.get("/api/core/public/authenticate", HttpStatus.OK, String.class);
        assertThat(user).isEmpty();
    }

    @Test
    @WithMockUser(AUTHENTICATEDUSER)
    void getAccount() throws Exception {
        userUtilService.createAndSaveUser(AUTHENTICATEDUSER);
        UserDTO account = request.get("/api/core/public/account", HttpStatus.OK, UserDTO.class);
        assertThat(account).isNotNull();
        assertThat(account.getAskToSetupPasskey()).isTrue();
        assertThat(account.isLoggedInWithPasskey()).isFalse();
        assertThat(account.isPasskeySuperAdminApproved()).isFalse();
    }

    @Test
    @WithMockUser(username = AUTHENTICATEDUSER)
    void getAccountWithRegisteredPasskey() throws Exception {
        User user = userUtilService.createAndSaveUser(AUTHENTICATEDUSER);
        passkeyCredentialUtilService.createAndSavePasskeyCredential(user);

        UserDTO account = request.get("/api/core/public/account", HttpStatus.OK, UserDTO.class);

        assertThat(account).isNotNull();
        assertThat(account.getAskToSetupPasskey()).isFalse();
    }

    /**
     * <p>
     * Reflects the case when the configuration property
     * <code>artemis.user-management.passkey.ask-users-to-setup</code> is set to <strong>false</strong>.
     * </p>
     *
     * <p>
     * If it were set to <strong>true</strong>, the value of <code>askUserToSetupPasskey</code>
     * would be <strong>true</strong>. However, since the configuration does not display the modal,
     * the value is always returned as <strong>false</strong>.
     * </p>
     */
    @Test
    @WithMockUser(username = AUTHENTICATEDUSER)
    void testGetAccountWhenAskUsersToSetupPasskeyIsFalse() throws Exception {
        User user = userUtilService.createAndSaveUser(AUTHENTICATEDUSER);
        passkeyCredentialUtilService.createAndSavePasskeyCredential(user);

        ReflectionTestUtils.setField(publicAccountResource, "askUsersToSetupPasskey", false);

        UserDTO account = request.get("/api/core/public/account", HttpStatus.OK, UserDTO.class);

        assertThat(account).isNotNull();
        assertThat(account.getAskToSetupPasskey()).isFalse();
    }

    @Test
    @WithAnonymousUser
    void getAccountWithoutLoggedInUser() throws Exception {
        UserDTO user = request.get("/api/core/public/account", HttpStatus.NO_CONTENT, UserDTO.class);
        assertThat(user).isNull();
    }

    @Test
    @WithMockUser(username = AUTHENTICATEDUSER)
    void saveAccount() throws Exception {
        String updatedFirstName = "UpdatedFirstName";
        User user = userUtilService.createAndSaveUser(AUTHENTICATEDUSER);
        user.setFirstName(updatedFirstName);

        request.put("/api/core/account", new UserDTO(user), HttpStatus.OK);

        // check if update successful
        Optional<User> updatedUser = userTestRepository.findOneByLogin(AUTHENTICATEDUSER);
        assertThat(updatedUser).isPresent();
        assertThat(updatedUser.get().getFirstName()).isEqualTo(updatedFirstName);
    }

    @Test
    @WithMockUser(username = AUTHENTICATEDUSER)
    void saveAccountRegistrationDisabled() throws Throwable {
        testWithRegistrationDisabled(() -> {
            User user = userUtilService.createAndSaveUser(AUTHENTICATEDUSER);
            String updatedFirstName = "UpdatedFirstName";
            user.setFirstName(updatedFirstName);

            request.put("/api/core/account", new UserDTO(user), HttpStatus.FORBIDDEN);
        });
    }

    @Test
    @WithMockUser(username = AUTHENTICATEDUSER)
    void saveAccountEmailInUse() throws Exception {
        User user = userUtilService.createAndSaveUser(AUTHENTICATEDUSER);
        User userSameEmail = userUtilService.createAndSaveUser("sameemail");
        // update Email to one already used
        user.setEmail(userSameEmail.getEmail());

        request.put("/api/core/account", new UserDTO(user), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = AUTHENTICATEDUSER)
    void changePassword() throws Exception {
        String updatedPassword = "12345678";
        userUtilService.createAndSaveUser(AUTHENTICATEDUSER, passwordService.hashPassword(UserFactory.USER_PASSWORD));

        PasswordChangeDTO pwChange = new PasswordChangeDTO(UserFactory.USER_PASSWORD, updatedPassword);
        request.postWithoutLocation("/api/core/account/change-password", pwChange, HttpStatus.OK, null);

        // check if update successful
        Optional<User> updatedUser = userTestRepository.findOneByLogin(AUTHENTICATEDUSER);
        assertThat(updatedUser).isPresent();
        assertThat(passwordService.checkPasswordMatch(updatedPassword, updatedUser.get().getPassword())).isTrue();
    }

    @Test
    @WithMockUser(username = AUTHENTICATEDUSER)
    void changePasswordExternalUser() throws Throwable {
        User user = userUtilService.createAndSaveUser(AUTHENTICATEDUSER);
        user.setInternal(false);
        userTestRepository.save(user);

        String updatedPassword = "12345678";
        PasswordChangeDTO pwChange = new PasswordChangeDTO(UserFactory.USER_PASSWORD, updatedPassword);

        request.postWithoutLocation("/api/core/account/change-password", pwChange, HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = AUTHENTICATEDUSER)
    void changePasswordInvalidPassword() throws Exception {
        userUtilService.createAndSaveUser(AUTHENTICATEDUSER);
        String updatedPassword = "";
        PasswordChangeDTO pwChange = new PasswordChangeDTO(UserFactory.USER_PASSWORD, updatedPassword);

        request.postWithoutLocation("/api/core/account/change-password", pwChange, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = AUTHENTICATEDUSER)
    void changePasswordSamePassword() throws Exception {
        userUtilService.createAndSaveUser(AUTHENTICATEDUSER);
        PasswordChangeDTO pwChange = new PasswordChangeDTO(UserFactory.USER_PASSWORD, UserFactory.USER_PASSWORD);

        request.postWithoutLocation("/api/core/account/change-password", pwChange, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = AUTHENTICATEDUSER)
    void changeLanguageKey() throws Exception {
        User user = userUtilService.createAndSaveUser(AUTHENTICATEDUSER);
        user.setLangKey("en");
        User storedUser = userTestRepository.save(user);
        assertThat(storedUser.getLangKey()).isEqualTo("en");

        request.postStringWithoutLocation("/api/core/public/account/change-language", "de", HttpStatus.OK, null);

        // check result
        Optional<User> updatedUser = userTestRepository.findOneByLogin(AUTHENTICATEDUSER);
        assertThat(updatedUser).isPresent();
        assertThat(updatedUser.get().getLangKey()).isEqualTo("de");
    }

    @Test
    @WithMockUser(username = AUTHENTICATEDUSER)
    void changeLanguageKeyNotSupported() throws Exception {
        User user = userUtilService.createAndSaveUser(AUTHENTICATEDUSER);
        user.setLangKey("en");
        User storedUser = userTestRepository.save(user);
        assertThat(storedUser.getLangKey()).isEqualTo("en");

        request.postStringWithoutLocation("/api/core/public/account/change-language", "loremIpsum", HttpStatus.BAD_REQUEST, null);
    }

    @Test
    void passwordResetByEmail() throws Exception {
        User createdUser = userUtilService.createAndSaveUser(AUTHENTICATEDUSER);

        Optional<User> userBefore = userTestRepository.findOneByEmailIgnoreCase(createdUser.getEmail());
        assertThat(userBefore).isPresent();
        String resetKeyBefore = userBefore.get().getResetKey();

        request.postStringWithoutLocation("/api/core/public/account/reset-password/init", createdUser.getEmail(), HttpStatus.OK, null);

        verifyPasswordReset(createdUser, resetKeyBefore);
    }

    @Test
    void passwordResetByUsername() throws Exception {
        User createdUser = userUtilService.createAndSaveUser(AUTHENTICATEDUSER);

        Optional<User> userBefore = userTestRepository.findOneByEmailIgnoreCase(createdUser.getEmail());
        assertThat(userBefore).isPresent();
        String resetKeyBefore = userBefore.get().getResetKey();

        request.postStringWithoutLocation("/api/core/public/account/reset-password/init", createdUser.getLogin(), HttpStatus.OK, null);
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
        request.postWithoutLocation("/api/core/public/account/reset-password/finish", finishResetData, HttpStatus.OK, null);

        // get updated user
        Optional<User> userPasswordResetFinished = userTestRepository.findOneByLogin(AUTHENTICATEDUSER);
        assertThat(userPasswordResetFinished).isPresent();
        assertThat(passwordService.checkPasswordMatch(newPassword, userPasswordResetFinished.get().getPassword())).isTrue();
    }

    @Test
    void passwordResetInvalidEmail() throws Exception {
        User createdUser = userUtilService.createAndSaveUser(AUTHENTICATEDUSER);

        Optional<User> userBefore = userTestRepository.findOneByEmailIgnoreCase(createdUser.getEmail());
        assertThat(userBefore).isPresent();
        String resetKeyBefore = userBefore.get().getResetKey();

        // init password reset
        request.postStringWithoutLocation("/api/core/public/account/reset-password/init", "invalidemail", HttpStatus.OK, null);

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

        request.postStringWithoutLocation("/api/core/public/account/reset-password/init", email, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(AUTHENTICATEDUSER)
    void passwordResetFinishInvalidPassword() throws Throwable {
        KeyAndPasswordVM finishResetData = new KeyAndPasswordVM();
        finishResetData.setNewPassword("");
        request.postWithoutLocation("/api/core/public/account/reset-password/finish", finishResetData, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(AUTHENTICATEDUSER)
    void passwordResetFinishInvalidKey() throws Throwable {
        KeyAndPasswordVM finishResetData = new KeyAndPasswordVM();
        finishResetData.setNewPassword(getValidPassword());
        request.postWithoutLocation("/api/core/public/account/reset-password/finish", finishResetData, HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = AUTHENTICATEDUSER)
    void acceptExternalLLMUsageSuccessful() throws Exception {
        User user = userUtilService.createAndSaveUser(AUTHENTICATEDUSER);
        user.setSelectedLLMUsageTimestamp(null);
        userTestRepository.save(user);

        SelectedLLMUsageDTO selectedLLMUsageDTO = new SelectedLLMUsageDTO(AiSelectionDecision.CLOUD_AI);
        request.put("/api/core/users/select-llm-usage", selectedLLMUsageDTO, HttpStatus.OK);

        Optional<User> updatedUser = userTestRepository.findOneByLogin(AUTHENTICATEDUSER);
        assertThat(updatedUser).isPresent();
        assertThat(updatedUser.get().getSelectedLLMUsageTimestamp()).isNotNull();
    }

    @Test
    @WithMockUser(username = AUTHENTICATEDUSER)
    void selectNoAIUsageSuccessful() throws Exception {
        User user = userUtilService.createAndSaveUser(AUTHENTICATEDUSER);
        user.setSelectedLLMUsageTimestamp(null);
        userTestRepository.save(user);

        SelectedLLMUsageDTO selectedLLMUsageDTO = new SelectedLLMUsageDTO(AiSelectionDecision.NO_AI);
        request.put("/api/core/users/select-llm-usage", selectedLLMUsageDTO, HttpStatus.OK);

        Optional<User> updatedUser = userTestRepository.findOneByLogin(AUTHENTICATEDUSER);
        assertThat(updatedUser).isPresent();
        assertThat(updatedUser.get().getSelectedLLMUsageTimestamp()).isNotNull();
    }

    @Test
    @WithMockUser(username = AUTHENTICATEDUSER)
    void acceptExternalLLMUsageAlreadyAccepted() throws Exception {
        // Create user in repo with existing timestamp
        User user = userUtilService.createAndSaveUser(AUTHENTICATEDUSER);
        ZonedDateTime originalTimestamp = ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        user.setSelectedLLMUsageTimestamp(originalTimestamp);
        userTestRepository.save(user);

        request.put("/api/core/users/select-llm-usage", null, HttpStatus.BAD_REQUEST);

        // Verify timestamp wasn't changed
        Optional<User> unchangedUser = userTestRepository.findOneByLogin(AUTHENTICATEDUSER);
        assertThat(unchangedUser).isPresent();

        ZonedDateTime actualTimestamp = unchangedUser.get().getSelectedLLMUsageTimestamp();
        assertThat(actualTimestamp).isNotNull();
        assertThat(actualTimestamp.truncatedTo(ChronoUnit.MILLIS)).isEqualTo(originalTimestamp);
    }
}
