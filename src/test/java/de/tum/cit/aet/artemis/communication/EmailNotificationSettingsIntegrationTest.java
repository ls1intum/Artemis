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
        // Enable NEW_LOGIN notification
        request.put("/api/communication/email-notification-settings/" + testUser.getId() + "/" + EmailNotificationType.NEW_LOGIN, null, HttpStatus.OK);
        assertThat(emailNotificationSettingService.isNotificationEnabled(testUser.getId(), EmailNotificationType.NEW_LOGIN)).isTrue();

        // Enable NEW_PASSKEY_ADDED notification
        request.put("/api/communication/email-notification-settings/" + testUser.getId() + "/" + EmailNotificationType.NEW_PASSKEY_ADDED, null, HttpStatus.OK);
        assertThat(emailNotificationSettingService.isNotificationEnabled(testUser.getId(), EmailNotificationType.NEW_PASSKEY_ADDED)).isTrue();

        // Enable VCS_TOKEN_EXPIRED notification
        request.put("/api/communication/email-notification-settings/" + testUser.getId() + "/" + EmailNotificationType.VCS_TOKEN_EXPIRED, null, HttpStatus.OK);
        assertThat(emailNotificationSettingService.isNotificationEnabled(testUser.getId(), EmailNotificationType.VCS_TOKEN_EXPIRED)).isTrue();

        // Enable SSH_KEY_EXPIRED notification
        request.put("/api/communication/email-notification-settings/" + testUser.getId() + "/" + EmailNotificationType.SSH_KEY_EXPIRED, null, HttpStatus.OK);
        assertThat(emailNotificationSettingService.isNotificationEnabled(testUser.getId(), EmailNotificationType.SSH_KEY_EXPIRED)).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDisableNotification() throws Exception {
        // First enable all notifications
        testEnableNotification();

        // Disable NEW_LOGIN notification
        request.delete("/api/communication/email-notification-settings/" + testUser.getId() + "/" + EmailNotificationType.NEW_LOGIN, HttpStatus.OK);
        assertThat(emailNotificationSettingService.isNotificationEnabled(testUser.getId(), EmailNotificationType.NEW_LOGIN)).isFalse();

        // Disable NEW_PASSKEY_ADDED notification
        request.delete("/api/communication/email-notification-settings/" + testUser.getId() + "/" + EmailNotificationType.NEW_PASSKEY_ADDED, HttpStatus.OK);
        assertThat(emailNotificationSettingService.isNotificationEnabled(testUser.getId(), EmailNotificationType.NEW_PASSKEY_ADDED)).isFalse();

        // Disable VCS_TOKEN_EXPIRED notification
        request.delete("/api/communication/email-notification-settings/" + testUser.getId() + "/" + EmailNotificationType.VCS_TOKEN_EXPIRED, HttpStatus.OK);
        assertThat(emailNotificationSettingService.isNotificationEnabled(testUser.getId(), EmailNotificationType.VCS_TOKEN_EXPIRED)).isFalse();

        // Disable SSH_KEY_EXPIRED notification
        request.delete("/api/communication/email-notification-settings/" + testUser.getId() + "/" + EmailNotificationType.SSH_KEY_EXPIRED, HttpStatus.OK);
        assertThat(emailNotificationSettingService.isNotificationEnabled(testUser.getId(), EmailNotificationType.SSH_KEY_EXPIRED)).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAllNotificationSettings() throws Exception {
        // Enable all notifications
        testEnableNotification();

        // Get all notification settings
        Map<EmailNotificationType, Boolean> settings = request.get("/api/communication/email-notification-settings/" + testUser.getId(), HttpStatus.OK, Map.class);

        assertThat(settings).isNotNull();
        assertThat(settings.get(EmailNotificationType.NEW_LOGIN)).isTrue();
        assertThat(settings.get(EmailNotificationType.NEW_PASSKEY_ADDED)).isTrue();
        assertThat(settings.get(EmailNotificationType.VCS_TOKEN_EXPIRED)).isTrue();
        assertThat(settings.get(EmailNotificationType.SSH_KEY_EXPIRED)).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateAllNotificationSettings() throws Exception {
        Map<EmailNotificationType, Boolean> settings = new HashMap<>();
        settings.put(EmailNotificationType.NEW_LOGIN, true);
        settings.put(EmailNotificationType.NEW_PASSKEY_ADDED, true);
        settings.put(EmailNotificationType.VCS_TOKEN_EXPIRED, false);
        settings.put(EmailNotificationType.SSH_KEY_EXPIRED, false);

        request.put("/api/email-notification-settings/" + testUser.getId(), settings, HttpStatus.OK);

        assertThat(emailNotificationSettingService.isNotificationEnabled(testUser.getId(), EmailNotificationType.NEW_LOGIN)).isTrue();
        assertThat(emailNotificationSettingService.isNotificationEnabled(testUser.getId(), EmailNotificationType.NEW_PASSKEY_ADDED)).isTrue();
        assertThat(emailNotificationSettingService.isNotificationEnabled(testUser.getId(), EmailNotificationType.VCS_TOKEN_EXPIRED)).isFalse();
        assertThat(emailNotificationSettingService.isNotificationEnabled(testUser.getId(), EmailNotificationType.SSH_KEY_EXPIRED)).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testInvalidNotificationType() throws Exception {
        request.put("/api/communication/email-notification-settings/" + testUser.getId() + "/INVALID_TYPE", null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUnauthorizedAccess() throws Exception {
        // Try to access another user's settings
        User otherUser = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        request.put("/api/communication/email-notification-settings/" + otherUser.getId() + "/" + EmailNotificationType.NEW_LOGIN, null, HttpStatus.FORBIDDEN);
    }
}
