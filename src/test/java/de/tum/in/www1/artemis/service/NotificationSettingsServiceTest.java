package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.domain.NotificationOption;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.notification.GroupNotification;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.notification.SingleUserNotification;
import de.tum.in.www1.artemis.repository.NotificationOptionRepository;

public class NotificationSettingsServiceTest {

    @Autowired
    private static NotificationSettingsService notificationSettingsService;

    @Autowired
    private static NotificationOptionRepository notificationOptionRepository;

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
        student1 = new User();
        student1.setId(555L);

        unsavedNotificationOptionA = new NotificationOption(student1, false, false, "notification.exercise-notification.exercise-open-for-practice");

        unsavedNotificationOptionB = new NotificationOption(student1, true, false, "notification.lecture-notification.attachment-changes");

        unsavedNotificationOptionC = new NotificationOption(student1, false, false, "notification.instructor-exclusive-notification.course-and-exam-archiving-started");

        unsavedNotificationOptions = new NotificationOption[] { unsavedNotificationOptionA, unsavedNotificationOptionB, unsavedNotificationOptionC };

        completeNotificationOptionA = new NotificationOption(student1, false, false, "notification.exercise-notification.exercise-open-for-practice");

        completeNotificationOptionB = new NotificationOption(student1, true, true, "notification.lecture-notification.attachment-changes");

        completeNotificationOptionC = new NotificationOption(student1, false, false, "notification.instructor-exclusive-notification.course-and-exam-archiving-started");

        savedNotificationOptions = new NotificationOption[] { completeNotificationOptionA, completeNotificationOptionB, completeNotificationOptionC };

        Set<NotificationOption> mockNotificationOptionSet = new HashSet<>(Arrays.asList(savedNotificationOptions));
        notificationOptionRepository = mock(NotificationOptionRepository.class);
        when(notificationOptionRepository.findAllNotificationOptionsForRecipientWithId(student1.getId())).thenReturn(mockNotificationOptionSet);

        notificationSettingsService = new NotificationSettingsService(notificationOptionRepository);
    }

    /**
     * Tests the method setCurrentUser
     * Each provided notification option should have the same user afterwards
     */
    @Test
    public void testSetCurrentUser() {
        NotificationOption[] tmpNotificationOptions = Arrays.copyOf(unsavedNotificationOptions, unsavedNotificationOptions.length);

        notificationSettingsService.setCurrentUser(unsavedNotificationOptions, student1);

        for (NotificationOption tmpOption : tmpNotificationOptions) {
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
        // OptionA : exercise-open-for-practice -> [EXERCISE_PRACTICE] : webapp & email deactivated
        // OptionB : attachment-changes -> [ATTACHMENT_CHANGE] : webapp & email activated <- not part of set
        // OptionC : course-and-exam-archiving-started -> [EXAM_ARCHIVE_STARTED, COURSE_ARCHIVE_STARTED] : webapp deactivated & no email support (no need)
        NotificationOption[] tmpNotificationOptionsArray = Arrays.copyOf(savedNotificationOptions, savedNotificationOptions.length);
        Set<NotificationOption> tmpNotificationOptionsSet = new HashSet<>(Arrays.asList(tmpNotificationOptionsArray));
        // webApp
        Set<NotificationType> resultingTypeSet = notificationSettingsService.findDeactivatedNotificationTypes(true, tmpNotificationOptionsSet);
        testFindDeactivatedNotificationTypesAssertAux(resultingTypeSet);
        // email
        resultingTypeSet = notificationSettingsService.findDeactivatedNotificationTypes(false, tmpNotificationOptionsSet);
        testFindDeactivatedNotificationTypesAssertAux(resultingTypeSet);
    }

    /**
     * Auxiliary method to help assert the resulting type set
     * @param resultingTypeSet that is created after findDeactivatedNotificationTypes()
     */
    private void testFindDeactivatedNotificationTypesAssertAux(Set<NotificationType> resultingTypeSet) {
        assertThat(resultingTypeSet).hasSize(3);
        assertThat(resultingTypeSet).contains(NotificationType.EXERCISE_PRACTICE);
        assertThat(resultingTypeSet).contains(NotificationType.EXAM_ARCHIVE_STARTED);
        assertThat(resultingTypeSet).contains(NotificationType.COURSE_ARCHIVE_STARTED);
        assertThat(resultingTypeSet).contains(NotificationType.COURSE_ARCHIVE_STARTED);
        assertThat(!resultingTypeSet.contains(NotificationType.ATTACHMENT_CHANGE));
    }

    @Test
    public void testCheckIfNotificationEmailIsAllowedBySettingsForGivenUser() {
        NotificationType allowedType = NotificationType.EXERCISE_PRACTICE;
        NotificationType blockedType = NotificationType.EXERCISE_UPDATED;
        NotificationType alwaysActiveType = NotificationType.ILLEGAL_SUBMISSION;

        Notification notificationA = new SingleUserNotification();
        notificationA.setOriginalNotificationType(allowedType);

        Notification notificationB = new GroupNotification();
        notificationB.setOriginalNotificationType(blockedType);

        Notification notificationC = new GroupNotification();
        notificationB.setOriginalNotificationType(alwaysActiveType);

        boolean resultA = notificationSettingsService.checkIfNotificationEmailIsAllowedBySettingsForGivenUser(notificationA, student1);
        boolean resultB = notificationSettingsService.checkIfNotificationEmailIsAllowedBySettingsForGivenUser(notificationB, student1);
        boolean resultC = notificationSettingsService.checkIfNotificationEmailIsAllowedBySettingsForGivenUser(notificationC, student1);

        assertThat(resultA).as("Allowed Type was correctly checked").isTrue();
        assertThat(resultB).as("Blocked Type was correctly checked").isFalse();
        assertThat(resultC).as("Always active Type was correctly checked").isTrue();
    }
}
