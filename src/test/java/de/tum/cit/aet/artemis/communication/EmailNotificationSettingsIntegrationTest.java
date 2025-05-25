package de.tum.cit.aet.artemis.communication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.communication.domain.EmailNotificationType;
import de.tum.cit.aet.artemis.communication.service.EmailNotificationSettingService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class EmailNotificationSettingsIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "emailnotificationintegration";

    @Autowired
    private EmailNotificationSettingService emailNotificationSettingService;

    private User testUser;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        testUser = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testEnableNotification() throws Exception {
        Map<String, Boolean> requestBody = new HashMap<>();
        requestBody.put("enabled", true);

        // Enable NEW_LOGIN notification
        request.put("/api/communication/email-notification-settings/" + EmailNotificationType.NEW_LOGIN, requestBody, HttpStatus.OK);
        assertThat(emailNotificationSettingService.isNotificationEnabled(testUser.getId(), EmailNotificationType.NEW_LOGIN)).isTrue();

        // Enable NEW_PASSKEY_ADDED notification
        request.put("/api/communication/email-notification-settings/" + EmailNotificationType.NEW_PASSKEY_ADDED, requestBody, HttpStatus.OK);
        assertThat(emailNotificationSettingService.isNotificationEnabled(testUser.getId(), EmailNotificationType.NEW_PASSKEY_ADDED)).isTrue();

        // Enable VCS_TOKEN_EXPIRED notification
        request.put("/api/communication/email-notification-settings/" + EmailNotificationType.VCS_TOKEN_EXPIRED, requestBody, HttpStatus.OK);
        assertThat(emailNotificationSettingService.isNotificationEnabled(testUser.getId(), EmailNotificationType.VCS_TOKEN_EXPIRED)).isTrue();

        // Enable SSH_KEY_EXPIRED notification
        request.put("/api/communication/email-notification-settings/" + EmailNotificationType.SSH_KEY_EXPIRED, requestBody, HttpStatus.OK);
        assertThat(emailNotificationSettingService.isNotificationEnabled(testUser.getId(), EmailNotificationType.SSH_KEY_EXPIRED)).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDisableNotification() throws Exception {
        // First enable all notifications
        testEnableNotification();

        Map<String, Boolean> requestBody = new HashMap<>();
        requestBody.put("enabled", false);

        // Disable NEW_LOGIN notification
        request.put("/api/communication/email-notification-settings/" + EmailNotificationType.NEW_LOGIN, requestBody, HttpStatus.OK);
        assertThat(emailNotificationSettingService.isNotificationEnabled(testUser.getId(), EmailNotificationType.NEW_LOGIN)).isFalse();

        // Disable NEW_PASSKEY_ADDED notification
        request.put("/api/communication/email-notification-settings/" + EmailNotificationType.NEW_PASSKEY_ADDED, requestBody, HttpStatus.OK);
        assertThat(emailNotificationSettingService.isNotificationEnabled(testUser.getId(), EmailNotificationType.NEW_PASSKEY_ADDED)).isFalse();

        // Disable VCS_TOKEN_EXPIRED notification
        request.put("/api/communication/email-notification-settings/" + EmailNotificationType.VCS_TOKEN_EXPIRED, requestBody, HttpStatus.OK);
        assertThat(emailNotificationSettingService.isNotificationEnabled(testUser.getId(), EmailNotificationType.VCS_TOKEN_EXPIRED)).isFalse();

        // Disable SSH_KEY_EXPIRED notification
        request.put("/api/communication/email-notification-settings/" + EmailNotificationType.SSH_KEY_EXPIRED, requestBody, HttpStatus.OK);
        assertThat(emailNotificationSettingService.isNotificationEnabled(testUser.getId(), EmailNotificationType.SSH_KEY_EXPIRED)).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAllNotificationSettings() throws Exception {
        // Enable all notifications
        testEnableNotification();

        // Get all notification settings
        Map<String, Boolean> settings = request.get("/api/communication/email-notification-settings", HttpStatus.OK, Map.class);

        assertThat(settings).isNotNull();
        assertThat(settings.get(EmailNotificationType.NEW_LOGIN.name())).isTrue();
        assertThat(settings.get(EmailNotificationType.NEW_PASSKEY_ADDED.name())).isTrue();
        assertThat(settings.get(EmailNotificationType.VCS_TOKEN_EXPIRED.name())).isTrue();
        assertThat(settings.get(EmailNotificationType.SSH_KEY_EXPIRED.name())).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateAllNotificationSettings() throws Exception {
        // Update each notification type individually
        Map<String, Boolean> requestBody = new HashMap<>();
        requestBody.put("enabled", true);

        // Enable NEW_LOGIN and NEW_PASSKEY_ADDED
        request.put("/api/communication/email-notification-settings/" + EmailNotificationType.NEW_LOGIN, requestBody, HttpStatus.OK);
        request.put("/api/communication/email-notification-settings/" + EmailNotificationType.NEW_PASSKEY_ADDED, requestBody, HttpStatus.OK);

        // Disable VCS_TOKEN_EXPIRED and SSH_KEY_EXPIRED
        requestBody.put("enabled", false);
        request.put("/api/communication/email-notification-settings/" + EmailNotificationType.VCS_TOKEN_EXPIRED, requestBody, HttpStatus.OK);
        request.put("/api/communication/email-notification-settings/" + EmailNotificationType.SSH_KEY_EXPIRED, requestBody, HttpStatus.OK);

        // Verify the settings
        assertThat(emailNotificationSettingService.isNotificationEnabled(testUser.getId(), EmailNotificationType.NEW_LOGIN)).isTrue();
        assertThat(emailNotificationSettingService.isNotificationEnabled(testUser.getId(), EmailNotificationType.NEW_PASSKEY_ADDED)).isTrue();
        assertThat(emailNotificationSettingService.isNotificationEnabled(testUser.getId(), EmailNotificationType.VCS_TOKEN_EXPIRED)).isFalse();
        assertThat(emailNotificationSettingService.isNotificationEnabled(testUser.getId(), EmailNotificationType.SSH_KEY_EXPIRED)).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testInvalidNotificationType() throws Exception {
        Map<String, Boolean> requestBody = new HashMap<>();
        requestBody.put("enabled", true);
        request.put("/api/communication/email-notification-settings/INVALID_TYPE", requestBody, HttpStatus.BAD_REQUEST);
    }
}
