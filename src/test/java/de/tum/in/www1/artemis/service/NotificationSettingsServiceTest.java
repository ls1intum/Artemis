package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.domain.NotificationOption;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;

public class NotificationSettingsServiceTest {

    @Autowired
    private static NotificationSettingsService notificationSettingsService;

    private static User student1;

    private static NotificationOption unsavedNotificationOptionA;

    private static NotificationOption unsavedNotificationOptionB;

    private static NotificationOption unsavedNotificationOptionC;

    private static NotificationOption completeNotificationOptionA;

    private static NotificationOption completeNotificationOptionB;

    private static NotificationOption completeNotificationOptionC;

    private static NotificationOption[] unsavedNotificationOptions;

    private static NotificationOption[] savedNotificationOptions;

    @BeforeAll
    private static void setup() {
        notificationSettingsService = new NotificationSettingsService();

        student1 = new User();
        student1.setId(555L);

        unsavedNotificationOptionA = new NotificationOption(false, false, "notification.exercise-notification.exercise-open-for-practice");

        unsavedNotificationOptionB = new NotificationOption(true, false, "notification.lecture-notification.attachment-changes");

        unsavedNotificationOptionC = new NotificationOption(false, false, "notification.instructor-exclusive-notification.course-and-exam-archiving-started");

        unsavedNotificationOptions = new NotificationOption[] { unsavedNotificationOptionA, unsavedNotificationOptionB, unsavedNotificationOptionC };

        completeNotificationOptionA = new NotificationOption(13L, student1, false, false, "notification.exercise-notification.exercise-open-for-practice");

        completeNotificationOptionB = new NotificationOption(27L, student1, true, false, "notification.lecture-notification.attachment-changes");

        completeNotificationOptionC = new NotificationOption(42L, student1, false, false, "notification.instructor-exclusive-notification.course-and-exam-archiving-started");

        savedNotificationOptions = new NotificationOption[] { completeNotificationOptionA, completeNotificationOptionB, completeNotificationOptionC };
    }

    @Test
    public void testSetCurrentUser() {
        NotificationOption[] tmpNotificationOptions = Arrays.copyOf(unsavedNotificationOptions, unsavedNotificationOptions.length);

        notificationSettingsService.setCurrentUser(unsavedNotificationOptions, student1);

        for (NotificationOption tmpOption : tmpNotificationOptions) {
            assertThat(tmpOption.getUser()).as("User was correctly set for NotificationOption").isEqualTo(student1);
        }
    }

    @Test
    public void testFindDeactivatedNotificationTypes() {
        NotificationOption[] tmpNotificationOptionsArray = Arrays.copyOf(savedNotificationOptions, savedNotificationOptions.length);
        Set<NotificationOption> tmpNotificationOptionsSet = new HashSet<>(Arrays.asList(tmpNotificationOptionsArray));
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
