package de.tum.cit.aet.artemis.communication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.communication.domain.GlobalNotificationType;
import de.tum.cit.aet.artemis.communication.repository.GlobalNotificationSettingRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.user.UserService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class GlobalNotificationSettingsIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "globalnotificationintegration";

    @Autowired
    private GlobalNotificationSettingRepository globalNotificationSettingRepository;

    @Autowired
    private UserService userService;

    private User testUser;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        testUser = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldEnableAllNotifications() throws Exception {
        enableAllNotifications();

        assertThat(globalNotificationSettingRepository.isNotificationEnabled(testUser.getId(), GlobalNotificationType.NEW_LOGIN)).isTrue();
        assertThat(globalNotificationSettingRepository.isNotificationEnabled(testUser.getId(), GlobalNotificationType.NEW_PASSKEY_ADDED)).isTrue();
        assertThat(globalNotificationSettingRepository.isNotificationEnabled(testUser.getId(), GlobalNotificationType.VCS_TOKEN_EXPIRED)).isTrue();
        assertThat(globalNotificationSettingRepository.isNotificationEnabled(testUser.getId(), GlobalNotificationType.SSH_KEY_EXPIRED)).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldDisableEachNotification() throws Exception {
        enableAllNotifications();

        Map<String, Boolean> requestBody = new HashMap<>();
        requestBody.put("enabled", false);

        request.put("/api/communication/global-notification-settings/" + GlobalNotificationType.NEW_LOGIN, requestBody, HttpStatus.OK);
        assertThat(globalNotificationSettingRepository.isNotificationEnabled(testUser.getId(), GlobalNotificationType.NEW_LOGIN)).isFalse();

        request.put("/api/communication/global-notification-settings/" + GlobalNotificationType.NEW_PASSKEY_ADDED, requestBody, HttpStatus.OK);
        assertThat(globalNotificationSettingRepository.isNotificationEnabled(testUser.getId(), GlobalNotificationType.NEW_PASSKEY_ADDED)).isFalse();

        request.put("/api/communication/global-notification-settings/" + GlobalNotificationType.VCS_TOKEN_EXPIRED, requestBody, HttpStatus.OK);
        assertThat(globalNotificationSettingRepository.isNotificationEnabled(testUser.getId(), GlobalNotificationType.VCS_TOKEN_EXPIRED)).isFalse();

        request.put("/api/communication/global-notification-settings/" + GlobalNotificationType.SSH_KEY_EXPIRED, requestBody, HttpStatus.OK);
        assertThat(globalNotificationSettingRepository.isNotificationEnabled(testUser.getId(), GlobalNotificationType.SSH_KEY_EXPIRED)).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnAllNotificationSettings() throws Exception {
        enableAllNotifications();

        Map<String, Boolean> settings = request.get("/api/communication/global-notification-settings", HttpStatus.OK, Map.class);

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

        request.put("/api/communication/global-notification-settings/" + GlobalNotificationType.NEW_LOGIN, requestBody, HttpStatus.OK);
        request.put("/api/communication/global-notification-settings/" + GlobalNotificationType.NEW_PASSKEY_ADDED, requestBody, HttpStatus.OK);

        requestBody.put("enabled", false);
        request.put("/api/communication/global-notification-settings/" + GlobalNotificationType.VCS_TOKEN_EXPIRED, requestBody, HttpStatus.OK);
        request.put("/api/communication/global-notification-settings/" + GlobalNotificationType.SSH_KEY_EXPIRED, requestBody, HttpStatus.OK);

        assertThat(globalNotificationSettingRepository.isNotificationEnabled(testUser.getId(), GlobalNotificationType.NEW_LOGIN)).isTrue();
        assertThat(globalNotificationSettingRepository.isNotificationEnabled(testUser.getId(), GlobalNotificationType.NEW_PASSKEY_ADDED)).isTrue();
        assertThat(globalNotificationSettingRepository.isNotificationEnabled(testUser.getId(), GlobalNotificationType.VCS_TOKEN_EXPIRED)).isFalse();
        assertThat(globalNotificationSettingRepository.isNotificationEnabled(testUser.getId(), GlobalNotificationType.SSH_KEY_EXPIRED)).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnBadRequestWhenNotificationTypeIsInvalid() throws Exception {
        Map<String, Boolean> requestBody = new HashMap<>();
        requestBody.put("enabled", true);
        request.put("/api/communication/global-notification-settings/INVALID_TYPE", requestBody, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldDeleteNotificationSettingsWhenUserIsSoftDeleted() throws Exception {
        enableAllNotifications();

        assertThat(globalNotificationSettingRepository.isNotificationEnabled(testUser.getId(), GlobalNotificationType.NEW_LOGIN)).isTrue();
        assertThat(globalNotificationSettingRepository.isNotificationEnabled(testUser.getId(), GlobalNotificationType.NEW_PASSKEY_ADDED)).isTrue();

        userService.softDeleteUser(testUser.getLogin());

        assertThat(globalNotificationSettingRepository.findByUserIdAndNotificationType(testUser.getId(), GlobalNotificationType.NEW_LOGIN)).isEmpty();
        assertThat(globalNotificationSettingRepository.findByUserIdAndNotificationType(testUser.getId(), GlobalNotificationType.NEW_PASSKEY_ADDED)).isEmpty();
        assertThat(globalNotificationSettingRepository.findByUserIdAndNotificationType(testUser.getId(), GlobalNotificationType.VCS_TOKEN_EXPIRED)).isEmpty();
        assertThat(globalNotificationSettingRepository.findByUserIdAndNotificationType(testUser.getId(), GlobalNotificationType.SSH_KEY_EXPIRED)).isEmpty();
    }

    private void enableAllNotifications() throws Exception {
        Map<String, Boolean> requestBody = new HashMap<>();
        requestBody.put("enabled", true);

        request.put("/api/communication/global-notification-settings/" + GlobalNotificationType.NEW_LOGIN, requestBody, HttpStatus.OK);
        request.put("/api/communication/global-notification-settings/" + GlobalNotificationType.NEW_PASSKEY_ADDED, requestBody, HttpStatus.OK);
        request.put("/api/communication/global-notification-settings/" + GlobalNotificationType.VCS_TOKEN_EXPIRED, requestBody, HttpStatus.OK);
        request.put("/api/communication/global-notification-settings/" + GlobalNotificationType.SSH_KEY_EXPIRED, requestBody, HttpStatus.OK);
    }
}
