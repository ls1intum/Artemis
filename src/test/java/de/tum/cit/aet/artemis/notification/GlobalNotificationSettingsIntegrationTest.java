package de.tum.cit.aet.artemis.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.user.UserService;
import de.tum.cit.aet.artemis.notification.domain.GlobalNotificationType;
import de.tum.cit.aet.artemis.notification.dto.GlobalNotificationSettingDTO;
import de.tum.cit.aet.artemis.notification.repository.GlobalNotificationSettingRepository;
import de.tum.cit.aet.artemis.notification.service.GlobalNotificationSettingService;
import de.tum.cit.aet.artemis.notification.service.TestAccountEmailService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class GlobalNotificationSettingsIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "globalnotificationintegration";

    /**
     * Matches {@code artemis.mail.test-account-login-patterns} of the test {@code application-artemis.yml}.
     */
    private static final String TEST_ACCOUNT_LOGIN = TEST_PREFIX + "flaggedtestuser";

    private static final String UNFLAGGED_LOGIN = "artemis_test_user_9002";

    @Autowired
    private GlobalNotificationSettingRepository globalNotificationSettingRepository;

    @Autowired
    private GlobalNotificationSettingService globalNotificationSettingService;

    @Autowired
    private TestAccountEmailService testAccountEmailPolicy;

    @Autowired
    private UserService userService;

    private User testUser;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        testUser = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        deleteSettingsOfTheUsersOfThisClass();
    }

    @AfterEach
    void removeSettings() {
        // The database outlives a test, and what a user switched on or off is what the next test would read as its default.
        deleteSettingsOfTheUsersOfThisClass();
    }

    private void deleteSettingsOfTheUsersOfThisClass() {
        globalNotificationSettingRepository.deleteAllByUserId(testUser.getId());
        userTestRepository.findOneByLogin(TEST_ACCOUNT_LOGIN).ifPresent(testAccount -> globalNotificationSettingRepository.deleteAllByUserId(testAccount.getId()));
    }

    private User testAccount() {
        return userTestRepository.findOneByLogin(TEST_ACCOUNT_LOGIN).orElseGet(() -> {
            var user = userUtilService.createAndSaveUser(TEST_ACCOUNT_LOGIN);
            user.setTestUser(true);
            return userTestRepository.save(user);
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldEnableAllNotifications() throws Exception {
        enableAllNotifications();

        assertThat(globalNotificationSettingService.isNotificationEnabled(testUser, GlobalNotificationType.NEW_LOGIN)).isTrue();
        assertThat(globalNotificationSettingService.isNotificationEnabled(testUser, GlobalNotificationType.NEW_PASSKEY_ADDED)).isTrue();
        assertThat(globalNotificationSettingService.isNotificationEnabled(testUser, GlobalNotificationType.VCS_TOKEN_EXPIRED)).isTrue();
        assertThat(globalNotificationSettingService.isNotificationEnabled(testUser, GlobalNotificationType.SSH_KEY_EXPIRED)).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldDisableEachNotification() throws Exception {
        enableAllNotifications();

        Map<String, Boolean> requestBody = new HashMap<>();
        requestBody.put("enabled", false);

        request.put("/api/notification/global-notification-settings/" + GlobalNotificationType.NEW_LOGIN, requestBody, HttpStatus.OK);
        assertThat(globalNotificationSettingService.isNotificationEnabled(testUser, GlobalNotificationType.NEW_LOGIN)).isFalse();

        request.put("/api/notification/global-notification-settings/" + GlobalNotificationType.NEW_PASSKEY_ADDED, requestBody, HttpStatus.OK);
        assertThat(globalNotificationSettingService.isNotificationEnabled(testUser, GlobalNotificationType.NEW_PASSKEY_ADDED)).isFalse();

        request.put("/api/notification/global-notification-settings/" + GlobalNotificationType.VCS_TOKEN_EXPIRED, requestBody, HttpStatus.OK);
        assertThat(globalNotificationSettingService.isNotificationEnabled(testUser, GlobalNotificationType.VCS_TOKEN_EXPIRED)).isFalse();

        request.put("/api/notification/global-notification-settings/" + GlobalNotificationType.SSH_KEY_EXPIRED, requestBody, HttpStatus.OK);
        assertThat(globalNotificationSettingService.isNotificationEnabled(testUser, GlobalNotificationType.SSH_KEY_EXPIRED)).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnGlobalNotificationSettingDTOWhenUpdatingSetting() throws Exception {
        Map<String, Boolean> requestBody = Map.of("enabled", false);

        GlobalNotificationSettingDTO response = request.putWithResponseBody("/api/notification/global-notification-settings/" + GlobalNotificationType.NEW_LOGIN, requestBody,
                GlobalNotificationSettingDTO.class, HttpStatus.OK);

        assertThat(response.id()).isNotNull();
        assertThat(response.userId()).isEqualTo(testUser.getId());
        assertThat(response.notificationType()).isEqualTo(GlobalNotificationType.NEW_LOGIN);
        assertThat(response.enabled()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnAllNotificationSettings() throws Exception {
        enableAllNotifications();

        Map<String, Boolean> settings = request.get("/api/notification/global-notification-settings", HttpStatus.OK, Map.class);

        assertThat(settings).isNotNull();
        assertThat(settings.get(GlobalNotificationType.NEW_LOGIN.name())).isTrue();
        assertThat(settings.get(GlobalNotificationType.NEW_PASSKEY_ADDED.name())).isTrue();
        assertThat(settings.get(GlobalNotificationType.VCS_TOKEN_EXPIRED.name())).isTrue();
        assertThat(settings.get(GlobalNotificationType.SSH_KEY_EXPIRED.name())).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldUpdateAllNotificationSettings() throws Exception {
        Map<String, Boolean> requestBody = new HashMap<>();
        requestBody.put("enabled", true);

        request.put("/api/notification/global-notification-settings/" + GlobalNotificationType.NEW_LOGIN, requestBody, HttpStatus.OK);
        request.put("/api/notification/global-notification-settings/" + GlobalNotificationType.NEW_PASSKEY_ADDED, requestBody, HttpStatus.OK);

        requestBody.put("enabled", false);
        request.put("/api/notification/global-notification-settings/" + GlobalNotificationType.VCS_TOKEN_EXPIRED, requestBody, HttpStatus.OK);
        request.put("/api/notification/global-notification-settings/" + GlobalNotificationType.SSH_KEY_EXPIRED, requestBody, HttpStatus.OK);

        assertThat(globalNotificationSettingService.isNotificationEnabled(testUser, GlobalNotificationType.NEW_LOGIN)).isTrue();
        assertThat(globalNotificationSettingService.isNotificationEnabled(testUser, GlobalNotificationType.NEW_PASSKEY_ADDED)).isTrue();
        assertThat(globalNotificationSettingService.isNotificationEnabled(testUser, GlobalNotificationType.VCS_TOKEN_EXPIRED)).isFalse();
        assertThat(globalNotificationSettingService.isNotificationEnabled(testUser, GlobalNotificationType.SSH_KEY_EXPIRED)).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnBadRequestWhenNotificationTypeIsInvalid() throws Exception {
        Map<String, Boolean> requestBody = new HashMap<>();
        requestBody.put("enabled", true);
        request.put("/api/notification/global-notification-settings/INVALID_TYPE", requestBody, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldDeleteNotificationSettingsWhenUserIsSoftDeleted() throws Exception {
        enableAllNotifications();

        assertThat(globalNotificationSettingService.isNotificationEnabled(testUser, GlobalNotificationType.NEW_LOGIN)).isTrue();
        assertThat(globalNotificationSettingService.isNotificationEnabled(testUser, GlobalNotificationType.NEW_PASSKEY_ADDED)).isTrue();

        userService.softDeleteUser(testUser.getLogin());

        assertThat(globalNotificationSettingRepository.findByUserIdAndNotificationType(testUser.getId(), GlobalNotificationType.NEW_LOGIN)).isEmpty();
        assertThat(globalNotificationSettingRepository.findByUserIdAndNotificationType(testUser.getId(), GlobalNotificationType.NEW_PASSKEY_ADDED)).isEmpty();
        assertThat(globalNotificationSettingRepository.findByUserIdAndNotificationType(testUser.getId(), GlobalNotificationType.VCS_TOKEN_EXPIRED)).isEmpty();
        assertThat(globalNotificationSettingRepository.findByUserIdAndNotificationType(testUser.getId(), GlobalNotificationType.SSH_KEY_EXPIRED)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldEnableAllNotificationsByDefaultForARegularUser() throws Exception {
        Map<String, Boolean> settings = request.get("/api/notification/global-notification-settings", HttpStatus.OK, Map.class);

        assertThat(settings).hasSize(GlobalNotificationType.values().length).containsOnlyKeys(enumNames()).containsValue(true).doesNotContainValue(false);
        assertThat(globalNotificationSettingService.isNotificationEnabled(testUser, GlobalNotificationType.NEW_LOGIN)).isTrue();
        assertThat(testAccountEmailPolicy.suppressesEmailTo(testUser.getLogin(), testUser.isTestUser())).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_ACCOUNT_LOGIN, roles = "USER")
    void shouldDisableAllNotificationsByDefaultForATestUser() throws Exception {
        var testAccount = testAccount();

        Map<String, Boolean> settings = request.get("/api/notification/global-notification-settings", HttpStatus.OK, Map.class);

        assertThat(settings).containsOnlyKeys(enumNames()).containsValue(false).doesNotContainValue(true);
        assertThat(globalNotificationSettingService.isNotificationEnabled(testAccount, GlobalNotificationType.NEW_LOGIN)).isFalse();
        assertThat(testAccountEmailPolicy.suppressesEmailTo(TEST_ACCOUNT_LOGIN, true)).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_ACCOUNT_LOGIN, roles = "USER")
    void shouldEnableOnlyWhatATestUserSwitchedOn() throws Exception {
        var testAccount = testAccount();

        request.put("/api/notification/global-notification-settings/" + GlobalNotificationType.NEW_LOGIN, Map.of("enabled", true), HttpStatus.OK);

        // The account asked for exactly this notification, so it gets it and nothing else...
        assertThat(globalNotificationSettingService.isNotificationEnabled(testAccount, GlobalNotificationType.NEW_LOGIN)).isTrue();
        assertThat(globalNotificationSettingService.isNotificationEnabled(testAccount, GlobalNotificationType.PASSWORD_CHANGED)).isFalse();
        // ...and since it has shown that it wants e-mail, the mails that have no setting of their own are no longer suppressed.
        assertThat(testAccountEmailPolicy.suppressesEmailTo(TEST_ACCOUNT_LOGIN, true)).isFalse();

        Map<String, Boolean> settings = request.get("/api/notification/global-notification-settings", HttpStatus.OK, Map.class);
        assertThat(settings).containsEntry(GlobalNotificationType.NEW_LOGIN.name(), true).containsEntry(GlobalNotificationType.PASSWORD_CHANGED.name(), false);
    }

    @Test
    @WithMockUser(username = TEST_ACCOUNT_LOGIN, roles = "USER")
    void shouldKeepSuppressingEmailToATestUserThatOnlySwitchedNotificationsOff() throws Exception {
        testAccount();

        request.put("/api/notification/global-notification-settings/" + GlobalNotificationType.NEW_LOGIN, Map.of("enabled", false), HttpStatus.OK);

        // An explicit "off" is not a request for mail.
        assertThat(testAccountEmailPolicy.suppressesEmailTo(TEST_ACCOUNT_LOGIN, true)).isTrue();
    }

    @Test
    void shouldTreatAnAccountAsATestUserOnlyWhenTheFlagIsSet() {
        // The login looks like the one of a test user, but the flag is what decides.
        var unflagged = userTestRepository.findOneByLogin(UNFLAGGED_LOGIN).orElseGet(() -> userUtilService.createAndSaveUser(UNFLAGGED_LOGIN));

        assertThat(globalNotificationSettingService.isNotificationEnabled(unflagged, GlobalNotificationType.NEW_LOGIN)).isTrue();
        assertThat(testAccountEmailPolicy.suppressesEmailTo(UNFLAGGED_LOGIN, unflagged.isTestUser())).isFalse();
    }

    private static String[] enumNames() {
        return Arrays.stream(GlobalNotificationType.values()).map(Enum::name).toArray(String[]::new);
    }

    private void enableAllNotifications() throws Exception {
        Map<String, Boolean> requestBody = new HashMap<>();
        requestBody.put("enabled", true);

        request.put("/api/notification/global-notification-settings/" + GlobalNotificationType.NEW_LOGIN, requestBody, HttpStatus.OK);
        request.put("/api/notification/global-notification-settings/" + GlobalNotificationType.NEW_PASSKEY_ADDED, requestBody, HttpStatus.OK);
        request.put("/api/notification/global-notification-settings/" + GlobalNotificationType.VCS_TOKEN_EXPIRED, requestBody, HttpStatus.OK);
        request.put("/api/notification/global-notification-settings/" + GlobalNotificationType.SSH_KEY_EXPIRED, requestBody, HttpStatus.OK);
    }
}
