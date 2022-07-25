package de.tum.in.www1.artemis;

import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsService.DEFAULT_NOTIFICATION_SETTINGS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.NotificationSetting;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.*;

class NotificationSettingsResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationSettingRepository notificationSettingRepository;

    private NotificationSetting settingA;

    private NotificationSetting settingsB;

    private User student1;

    /**
     * Prepares the common variables and data for testing
     */
    @BeforeEach
    void initTestCase() {
        List<User> users = database.addUsers(2, 1, 1, 1);

        student1 = users.get(0);
        users.set(0, student1);
        userRepository.save(student1);

        settingA = new NotificationSetting(student1, true, false, "notification.lecture-notification.attachment-changes");
        settingsB = new NotificationSetting(student1, false, false, "notification.exercise-notification.exercise-open-for-practice");
    }

    /**
     * Cleans the test environment to make sure different test do not influence each other
     */
    @AfterEach
    void tearDown() {
        database.resetDatabase();
        notificationSettingRepository.deleteAll();
    }

    /**
     * Tests the getNotificationSettingsForCurrentUser method if the user already has saved settings in the DB
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetNotificationSettingsForCurrentUserWith_DB_NOT_EMPTY() throws Exception {
        notificationSettingRepository.save(settingA);
        notificationSettingRepository.save(settingsB);

        List<NotificationSetting> notificationSettings = request.getList("/api/notification-settings", HttpStatus.OK, NotificationSetting.class);

        assertThat(notificationSettings).as("notificationSettings A with recipient equal to current user is returned").contains(settingA);
        assertThat(notificationSettings).as("notificationSettings B with recipient equal to current user is returned").contains(settingsB);
        assertThat(notificationSettings).hasSameSizeAs(DEFAULT_NOTIFICATION_SETTINGS);
    }

    /**
     * Tests the getNotificationSettingsForCurrentUser method if the user has not yet saved the settings
     * The default settings should be returned
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetNotificationSettingsForCurrentUserWith_DB_EMTPY() throws Exception {
        List<NotificationSetting> notificationSettings = request.getList("/api/notification-settings", HttpStatus.OK, NotificationSetting.class);
        assertThat(notificationSettings).hasSameSizeAs(DEFAULT_NOTIFICATION_SETTINGS);
    }

    /**
     * Tests the saveNotificationSettingsForCurrentUser method under normal (successful) conditions
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testSaveNotificationSettingsForCurrentUser_OK() throws Exception {
        NotificationSetting[] newlyChangedSettingsToSave = { settingA, settingsB };

        NotificationSetting[] notificationSettingsResponse = request.putWithResponseBody("/api/notification-settings", newlyChangedSettingsToSave, NotificationSetting[].class,
                HttpStatus.OK);

        boolean foundA = false;
        boolean foundB = false;
        for (NotificationSetting setting : notificationSettingsResponse) {
            if (setting.getSettingId().equals(settingA.getSettingId())) {
                foundA = true;
            }
            if (setting.getSettingId().equals(settingA.getSettingId())) {
                foundB = true;
            }
        }

        assertThat(foundA && foundB).as("Saved and received Notification Settings A & B correctly").isTrue();
    }

    /**
     * Tests the saveNotificationSettingsForCurrentUser method if a bad request occurs
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testSaveNotificationSettingsForCurrentUser_BAD_REQUEST() throws Exception {
        request.putWithResponseBody("/api/notification-settings", null, NotificationSetting[].class, HttpStatus.BAD_REQUEST);
    }
}
