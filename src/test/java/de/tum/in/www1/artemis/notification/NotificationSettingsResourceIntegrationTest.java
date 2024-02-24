package de.tum.in.www1.artemis.notification;

import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsService.DEFAULT_NOTIFICATION_SETTINGS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.NotificationSetting;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.post.ConversationUtilService;
import de.tum.in.www1.artemis.repository.NotificationSettingRepository;
import de.tum.in.www1.artemis.user.UserUtilService;

class NotificationSettingsResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "notificationsettingsresourrce";

    @Autowired
    private NotificationSettingRepository notificationSettingRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ConversationUtilService conversationUtilService;

    private NotificationSetting settingA;

    private NotificationSetting settingsB;

    private User student1;

    /**
     * Prepares the common variables and data for testing
     */
    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        settingA = new NotificationSetting(student1, true, false, true, "notification.lecture-notification.attachment-changes");
        settingsB = new NotificationSetting(student1, false, false, true, "notification.exercise-notification.exercise-open-for-practice");
    }

    /**
     * Cleans the test environment to make sure different test do not influence each other
     */
    @AfterEach
    void tearDown() {
        notificationSettingRepository.deleteAll();
    }

    /**
     * Tests the getNotificationSettingsForCurrentUser method if the user already has saved settings in the DB
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetNotificationSettingsForCurrentUserWith_DB_NOT_EMPTY() throws Exception {
        notificationSettingRepository.save(settingA);
        notificationSettingRepository.save(settingsB);

        List<NotificationSetting> notificationSettings = request.getList("/api/notification-settings", HttpStatus.OK, NotificationSetting.class);

        assertThat(notificationSettings).hasSameSizeAs(DEFAULT_NOTIFICATION_SETTINGS);
        assertThat(notificationSettings).as("notificationSettings A with recipient equal to current user is returned")
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id", "user").contains(settingA);
        assertThat(notificationSettings).as("notificationSettings B with recipient equal to current user is returned")
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id", "user").contains(settingsB);

        for (NotificationSetting setting : notificationSettings) {
            assertThat(setting.getUser()).as("User of the returned notification settings is the current user").isEqualTo(student1);
        }
    }

    /**
     * Tests the getNotificationSettingsForCurrentUser method if the user has not yet saved the settings
     * The default settings should be returned
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetNotificationSettingsForCurrentUserWith_DB_EMTPY() throws Exception {
        List<NotificationSetting> notificationSettings = request.getList("/api/notification-settings", HttpStatus.OK, NotificationSetting.class);
        assertThat(notificationSettings).hasSameSizeAs(DEFAULT_NOTIFICATION_SETTINGS);
    }

    /**
     * Tests the saveNotificationSettingsForCurrentUser method under normal (successful) conditions
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
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
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSaveNotificationSettingsForCurrentUser_BAD_REQUEST() throws Exception {
        request.putWithResponseBody("/api/notification-settings", null, NotificationSetting[].class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testLoadMutedConversations() throws Exception {
        Course course = courseUtilService.createCourse();
        Channel mutedChannel = conversationUtilService.createCourseWideChannel(course, "muted");
        Channel channel = conversationUtilService.createCourseWideChannel(course, "test");
        conversationUtilService.addParticipantToConversation(mutedChannel, TEST_PREFIX + "student1", true);
        conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student1");

        List<Long> mutedConversations = request.getList("/api/muted-conversations", HttpStatus.OK, Long.class);
        assertThat(mutedConversations).hasSize(1);
        assertThat(mutedConversations).contains(mutedChannel.getId());
    }
}
