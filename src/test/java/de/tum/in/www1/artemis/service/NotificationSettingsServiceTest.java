package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.domain.NotificationSetting;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;

public class NotificationSettingsServiceTest {

    @Autowired
    private static NotificationSettingsService notificationSettingsService;

    private static User student1;

    private static NotificationSetting unsavedNotificationOptionA;

    private static NotificationSetting unsavedNotificationOptionB;

    private static NotificationSetting unsavedNotificationOptionC;

    private static NotificationSetting completeNotificationOptionA;

    private static NotificationSetting completeNotificationOptionB;

    private static NotificationSetting completeNotificationOptionC;

    private static NotificationSetting[] unsavedNotificationOptions;

    private static NotificationSetting[] savedNotificationOptions;

    /**
     * Prepares the needed values and objects for testing
     */
    @BeforeAll
    public static void setUp() {
        notificationSettingsService = new NotificationSettingsService();

        student1 = new User();
        student1.setId(555L);

        unsavedNotificationOptionA = new NotificationSetting(student1, false, false, "notification.exercise-notification.exercise-open-for-practice");

        unsavedNotificationOptionB = new NotificationSetting(student1, true, false, "notification.lecture-notification.attachment-changes");

        unsavedNotificationOptionC = new NotificationSetting(student1, false, false, "notification.instructor-exclusive-notification.course-and-exam-archiving-started");

        unsavedNotificationOptions = new NotificationSetting[] { unsavedNotificationOptionA, unsavedNotificationOptionB, unsavedNotificationOptionC };

        completeNotificationOptionA = new NotificationSetting(student1, false, false, "notification.exercise-notification.exercise-open-for-practice");

        completeNotificationOptionB = new NotificationSetting(student1, true, false, "notification.lecture-notification.attachment-changes");

        completeNotificationOptionC = new NotificationSetting(student1, false, false, "notification.instructor-exclusive-notification.course-and-exam-archiving-started");

        savedNotificationOptions = new NotificationSetting[] { completeNotificationOptionA, completeNotificationOptionB, completeNotificationOptionC };
    }

    /**
     * Tests the method setCurrentUser
     * Each provided notification option should have the same user afterwards
     */
    @Test
    public void testSetCurrentUser() {
        NotificationSetting[] tmpNotificationOptions = Arrays.copyOf(unsavedNotificationOptions, unsavedNotificationOptions.length);

        notificationSettingsService.setCurrentUser(unsavedNotificationOptions, student1);

        for (NotificationSetting tmpOption : tmpNotificationOptions) {
            assertThat(tmpOption.getUser()).as("User was correctly set for NotificationOption").isEqualTo(student1);
        }
    }

    /**
     * Tests the method findDeactivatedNotificationTypes
     * This test also tests the private methods convertNotificationOptionsToNotificationTypesWithActivationStatus
     * & convertNotificationOptionsToNotificationTypesWithActivationStatus
     */
    @Test
    public void testFindDeactivatedNotificationTypes() {
        NotificationSetting[] tmpNotificationOptionsArray = Arrays.copyOf(savedNotificationOptions, savedNotificationOptions.length);
        Set<NotificationSetting> tmpNotificationOptionsSet = new HashSet<>(Arrays.asList(tmpNotificationOptionsArray));
        Set<NotificationType> resultingTypeSet = notificationSettingsService.findDeactivatedNotificationTypes(tmpNotificationOptionsSet);
        // OptionA : exercise-open-for-practice -> [EXERCISE_PRACTICE] : webapp deactivated
        // OptionB : attachment-changes -> [ATTACHMENT_CHANGE] : webapp activated <- not part of set
        // OptionC : course-and-exam-archiving-started -> [EXAM_ARCHIVE_STARTED, COURSE_ARCHIVE_STARTED] : webapp deactivated
        assertThat(resultingTypeSet).hasSize(3);
        assertThat(resultingTypeSet).contains(NotificationType.EXERCISE_PRACTICE);
        assertThat(resultingTypeSet).contains(NotificationType.EXAM_ARCHIVE_STARTED);
        assertThat(resultingTypeSet).contains(NotificationType.COURSE_ARCHIVE_STARTED);
        assertThat(resultingTypeSet).contains(NotificationType.COURSE_ARCHIVE_STARTED);
        assertThat(!resultingTypeSet.contains(NotificationType.ATTACHMENT_CHANGE));
    }
}
