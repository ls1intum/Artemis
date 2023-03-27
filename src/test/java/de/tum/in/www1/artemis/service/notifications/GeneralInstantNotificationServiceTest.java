package de.tum.in.www1.artemis.service.notifications;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.domain.notification.GroupNotification;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.service.notifications.push_notifications.ApplePushNotificationService;
import de.tum.in.www1.artemis.service.notifications.push_notifications.FirebasePushNotificationService;

class GeneralInstantNotificationServiceTest {

    @Mock
    private NotificationSettingsService notificationSettingsService;

    @Mock
    private ApplePushNotificationService applePushNotificationService;

    @Mock
    private FirebasePushNotificationService firebasePushNotificationService;

    @Mock
    private MailService mailService;

    private User student1;

    private User student2;

    private GeneralInstantNotificationService generalInstantNotificationService;

    private Notification notification;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        student1 = new User();
        student1.setId(1L);
        student1.setLogin("1");
        student2 = new User();
        student2.setId(2L);
        student2.setLogin("2");

        notification = new GroupNotification(null, "test", "test", false, new String[0], student1, GroupNotificationType.STUDENT);

        generalInstantNotificationService = new GeneralInstantNotificationService(applePushNotificationService, firebasePushNotificationService, mailService,
                notificationSettingsService);
    }

    /**
     * Very basic test that checks if emails and pushes are send for one user
     */
    @Test
    void testSendAllNotifications() {
        when(notificationSettingsService.checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(notification, student1,
                NotificationSettingsCommunicationChannel.EMAIL)).thenReturn(true);
        when(notificationSettingsService.checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(notification, student1,
                NotificationSettingsCommunicationChannel.PUSH)).thenReturn(true);

        generalInstantNotificationService.sendNotification(notification, student1, null);

        verify(applePushNotificationService, times(1)).sendNotification(notification, student1, null);
        verify(firebasePushNotificationService, times(1)).sendNotification(notification, student1, null);
        verify(mailService, times(1)).sendNotification(notification, student1, null);
    }

    /**
     * Very basic test that checks if emails are not send and pushes are send for one user
     */
    @Test
    void testSendOnlyPushNotifications() {
        when(notificationSettingsService.checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(notification, student1,
                NotificationSettingsCommunicationChannel.EMAIL)).thenReturn(false);
        when(notificationSettingsService.checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(notification, student1,
                NotificationSettingsCommunicationChannel.PUSH)).thenReturn(true);

        generalInstantNotificationService.sendNotification(notification, student1, null);

        verify(applePushNotificationService, times(1)).sendNotification(notification, student1, null);
        verify(firebasePushNotificationService, times(1)).sendNotification(notification, student1, null);
        verify(mailService, times(0)).sendNotification(notification, student1, null);
    }

    /**
     * Very basic test that checks if emails are send and pushes are not send for one user
     */
    @Test
    void testSendOnlyEmailNotifications() {
        when(notificationSettingsService.checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(notification, student1,
                NotificationSettingsCommunicationChannel.EMAIL)).thenReturn(true);
        when(notificationSettingsService.checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(notification, student1,
                NotificationSettingsCommunicationChannel.PUSH)).thenReturn(false);

        generalInstantNotificationService.sendNotification(notification, student1, null);

        verify(applePushNotificationService, times(0)).sendNotification(notification, student1, null);
        verify(firebasePushNotificationService, times(0)).sendNotification(notification, student1, null);
        verify(mailService, times(1)).sendNotification(notification, student1, null);
    }

    /**
     * Test that checks if emails and pushes are send for multiple users
     */
    @Test
    void testSendAllNotificationsToMultipleUsers() {
        when(notificationSettingsService.checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(notification, student1,
                NotificationSettingsCommunicationChannel.EMAIL)).thenReturn(true);
        when(notificationSettingsService.checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(notification, student1,
                NotificationSettingsCommunicationChannel.PUSH)).thenReturn(true);
        when(notificationSettingsService.checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(notification, student2,
                NotificationSettingsCommunicationChannel.EMAIL)).thenReturn(true);
        when(notificationSettingsService.checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(notification, student2,
                NotificationSettingsCommunicationChannel.PUSH)).thenReturn(true);

        List<User> studentList = new ArrayList<>();
        studentList.add(student1);
        studentList.add(student2);
        generalInstantNotificationService.sendNotification(notification, studentList, null);

        verify(applePushNotificationService, times(1)).sendNotification(notification, studentList, null);
        verify(firebasePushNotificationService, times(1)).sendNotification(notification, studentList, null);
        verify(mailService, times(1)).sendNotification(notification, studentList, null);
    }

    @Test
    void testSendEmailOnlyToOneUser() {
        when(notificationSettingsService.checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(notification, student1,
                NotificationSettingsCommunicationChannel.EMAIL)).thenReturn(true);
        when(notificationSettingsService.checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(notification, student1,
                NotificationSettingsCommunicationChannel.PUSH)).thenReturn(true);
        when(notificationSettingsService.checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(notification, student2,
                NotificationSettingsCommunicationChannel.EMAIL)).thenReturn(false);
        when(notificationSettingsService.checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(notification, student2,
                NotificationSettingsCommunicationChannel.PUSH)).thenReturn(true);

        List<User> studentList = new ArrayList<>();
        studentList.add(student1);
        studentList.add(student2);
        generalInstantNotificationService.sendNotification(notification, studentList, null);

        verify(applePushNotificationService, times(1)).sendNotification(notification, studentList, null);
        verify(firebasePushNotificationService, times(1)).sendNotification(notification, studentList, null);
        verify(mailService, times(1)).sendNotification(notification, Collections.singletonList(student1), null);
    }

    @Test
    void testSendPushOnlyToOneUser() {
        when(notificationSettingsService.checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(notification, student1,
                NotificationSettingsCommunicationChannel.EMAIL)).thenReturn(true);
        when(notificationSettingsService.checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(notification, student1,
                NotificationSettingsCommunicationChannel.PUSH)).thenReturn(true);
        when(notificationSettingsService.checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(notification, student2,
                NotificationSettingsCommunicationChannel.EMAIL)).thenReturn(true);
        when(notificationSettingsService.checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(notification, student2,
                NotificationSettingsCommunicationChannel.PUSH)).thenReturn(false);

        List<User> studentList = new ArrayList<>();
        studentList.add(student1);
        studentList.add(student2);
        generalInstantNotificationService.sendNotification(notification, studentList, null);

        verify(applePushNotificationService, times(1)).sendNotification(notification, Collections.singletonList(student1), null);
        verify(firebasePushNotificationService, times(1)).sendNotification(notification, Collections.singletonList(student1), null);
        verify(mailService, times(1)).sendNotification(notification, studentList, null);
    }
}
