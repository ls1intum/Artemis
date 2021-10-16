package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.domain.NotificationSetting;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.repository.NotificationSettingRepository;

public class NotificationSettingsServiceTest {

    @Autowired
    private static NotificationSettingsService notificationSettingsService;

    @Autowired
    private static NotificationSettingRepository notificationSettingRepository;
  
    private static User student1;

    private static NotificationSetting unsavedNotificationSettingA;

    private static NotificationSetting unsavedNotificationSettingB;

    private static NotificationSetting unsavedNotificationSettingC;

    private static NotificationSetting completeNotificationSettingA;

    private static NotificationSetting completeNotificationSettingB;

    private static NotificationSetting completeNotificationSettingC;

    private static NotificationSetting[] unsavedNotificationSettings;

    private static NotificationSetting[] savedNotificationSettings;

    /**
     * Prepares the needed values and objects for testing
     */
    @BeforeAll
    public static void setUp() {
        notificationSettingsService = new NotificationSettingsService(notificationSettingRepository);

        student1 = new User();
        student1.setId(555L);

        unsavedNotificationSettingA = new NotificationSetting(student1, false, false, "notification.exercise-notification.exercise-open-for-practice");

        unsavedNotificationSettingB = new NotificationSetting(student1, true, false, "notification.lecture-notification.attachment-changes");

        unsavedNotificationSettingC = new NotificationSetting(student1, false, false, "notification.instructor-exclusive-notification.course-and-exam-archiving-started");

        unsavedNotificationSettings = new NotificationSetting[] { unsavedNotificationSettingA, unsavedNotificationSettingB, unsavedNotificationSettingC };

        completeNotificationSettingA = new NotificationSetting(student1, false, false, "notification.exercise-notification.exercise-open-for-practice");

        completeNotificationSettingB = new NotificationSetting(student1, true, false, "notification.lecture-notification.attachment-changes");

        completeNotificationSettingC = new NotificationSetting(student1, false, false, "notification.instructor-exclusive-notification.course-and-exam-archiving-started");

        savedNotificationSettings = new NotificationSetting[] { completeNotificationSettingA, completeNotificationSettingB, completeNotificationSettingC };
    }

    /**
     * Tests the method setCurrentUser
     * Each provided notification setting should have the same user afterwards
     */
    @Test
    public void testSetCurrentUser() {
        NotificationSetting[] tmpNotificationSettings = Arrays.copyOf(unsavedNotificationSettings, unsavedNotificationSettings.length);

        notificationSettingsService.setCurrentUser(unsavedNotificationSettings, student1);

        for (NotificationSetting tmpSetting : tmpNotificationSettings) {
            assertThat(tmpSetting.getUser()).as("User was correctly set for NotificationSetting").isEqualTo(student1);
        }
    }

    /**
     * Tests the method findDeactivatedNotificationTypes
     * This test also tests the private methods convertNotificationSettingsToNotificationTypesWithActivationStatus
     * & convertNotificationSettingsToNotificationTypesWithActivationStatus
     */
    @Test
    public void testFindDeactivatedNotificationTypes() {
        NotificationSetting[] tmpNotificationSettingsArray = Arrays.copyOf(savedNotificationSettings, savedNotificationSettings.length);
        Set<NotificationSetting> tmpNotificationSettingsSet = new HashSet<>(Arrays.asList(tmpNotificationSettingsArray));
        Set<NotificationType> resultingTypeSet = notificationSettingsService.findDeactivatedNotificationTypes(true, tmpNotificationSettingsSet);
        // SettingA : exercise-open-for-practice -> [EXERCISE_PRACTICE] : webapp deactivated
        // SettingB : attachment-changes -> [ATTACHMENT_CHANGE] : webapp activated <- not part of set
        // SettingC : course-and-exam-archiving-started -> [EXAM_ARCHIVE_STARTED, COURSE_ARCHIVE_STARTED] : webapp deactivated
        assertThat(resultingTypeSet).hasSize(3);
        assertThat(resultingTypeSet).contains(NotificationType.EXERCISE_PRACTICE);
        assertThat(resultingTypeSet).contains(NotificationType.EXAM_ARCHIVE_STARTED);
        assertThat(resultingTypeSet).contains(NotificationType.COURSE_ARCHIVE_STARTED);
        assertThat(resultingTypeSet).contains(NotificationType.COURSE_ARCHIVE_STARTED);
        assertThat(!resultingTypeSet.contains(NotificationType.ATTACHMENT_CHANGE));
    }
}
