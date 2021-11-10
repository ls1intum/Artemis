package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.domain.NotificationSetting;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants;
import de.tum.in.www1.artemis.repository.NotificationSettingRepository;

public class NotificationSettingsServiceTest {

    @Autowired
    private static NotificationSettingsService notificationSettingsService;

    @Mock
    private static NotificationSettingRepository notificationSettingRepository;

    @Mock
    private static Notification notification;

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
        student1 = new User();
        student1.setId(555L);

        unsavedNotificationSettingA = new NotificationSetting(false, true, "notification.exercise-notification.exercise-open-for-practice");
        unsavedNotificationSettingB = new NotificationSetting(true, true, "notification.lecture-notification.attachment-changes");
        unsavedNotificationSettingC = new NotificationSetting(false, false, "notification.instructor-exclusive-notification.course-and-exam-archiving-started");
        unsavedNotificationSettings = new NotificationSetting[] { unsavedNotificationSettingA, unsavedNotificationSettingB, unsavedNotificationSettingC };

        completeNotificationSettingA = new NotificationSetting(student1, false, true, "notification.exercise-notification.exercise-open-for-practice");
        completeNotificationSettingB = new NotificationSetting(student1, true, true, "notification.lecture-notification.attachment-changes");
        completeNotificationSettingC = new NotificationSetting(student1, false, false, "notification.instructor-exclusive-notification.course-and-exam-archiving-started");
        savedNotificationSettings = new NotificationSetting[] { completeNotificationSettingA, completeNotificationSettingB, completeNotificationSettingC };

        notificationSettingRepository = mock(NotificationSettingRepository.class);
        when(notificationSettingRepository.findAllNotificationSettingsForRecipientWithId(student1.getId())).thenReturn(new HashSet<>(Arrays.asList(savedNotificationSettings)));

        notificationSettingsService = new NotificationSettingsService(notificationSettingRepository);

        notification = mock(Notification.class);
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
        assertThat(resultingTypeSet).contains(EXERCISE_PRACTICE);
        assertThat(resultingTypeSet).contains(EXAM_ARCHIVE_STARTED);
        assertThat(resultingTypeSet).contains(COURSE_ARCHIVE_STARTED);
        assertThat(resultingTypeSet).contains(COURSE_ARCHIVE_STARTED);
        assertThat(!resultingTypeSet.contains(ATTACHMENT_CHANGE));
    }

    /**
     * Tests the method checkIfNotificationEmailIsAllowedBySettingsForGivenUser
     * Checks if the given user should receive an email based on the specific notification (type) or not based on the user's notification settings
     */
    @Test
    public void testCheckIfNotificationEmailIsAllowedBySettingsForGivenUser() {
        when(notification.getTitle()).thenReturn(NotificationTitleTypeConstants.findCorrespondingNotificationTitle(ATTACHMENT_CHANGE));
        assertThat(notificationSettingsService.checkIfNotificationEmailIsAllowedBySettingsForGivenUser(notification, student1)).isTrue();

        when(notification.getTitle()).thenReturn(NotificationTitleTypeConstants.findCorrespondingNotificationTitle(EXERCISE_PRACTICE));
        assertThat(notificationSettingsService.checkIfNotificationEmailIsAllowedBySettingsForGivenUser(notification, student1)).isTrue();

        when(notification.getTitle()).thenReturn(NotificationTitleTypeConstants.findCorrespondingNotificationTitle(EXAM_ARCHIVE_STARTED));
        assertThat(notificationSettingsService.checkIfNotificationEmailIsAllowedBySettingsForGivenUser(notification, student1)).isFalse();
    }

    /**
     * Tests the method checkLoadedNotificationSettingsForCorrectness with an empty input
     */
    @Test
    public void testCheckLoadedNotificationSettingsForCorrectness_empty() {
        Set<NotificationSetting> testSet = new HashSet<>();
        testSet = notificationSettingsService.checkLoadedNotificationSettingsForCorrectness(testSet);
        assertThat(testSet.size()).isEqualTo(NotificationSettingsService.DEFAULT_NOTIFICATION_SETTINGS.size());
        verify(notificationSettingRepository, times(0)).saveAll(any());
    }

    /**
     * Tests the method checkLoadedNotificationSettingsForCorrectness with an incomplete input
     */
    @Test
    public void testCheckLoadedNotificationSettingsForCorrectness_incomplete() {
        Set<NotificationSetting> testSet = new HashSet<>();
        testSet.add(completeNotificationSettingA);
        testSet = notificationSettingsService.checkLoadedNotificationSettingsForCorrectness(testSet);
        assertThat(testSet.size()).isEqualTo(NotificationSettingsService.DEFAULT_NOTIFICATION_SETTINGS.size());
        assertThat(testSet).contains(completeNotificationSettingA);
        verify(notificationSettingRepository, times(1)).saveAll(any());
    }
}
