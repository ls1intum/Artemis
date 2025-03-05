package de.tum.cit.aet.artemis.coursenotification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.coursenotification.domain.NotificationSettingOption;
import de.tum.cit.aet.artemis.coursenotification.domain.UserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.coursenotification.domain.UserCourseNotificationSettingSpecification;
import de.tum.cit.aet.artemis.coursenotification.domain.notifications.CourseNotification;
import de.tum.cit.aet.artemis.coursenotification.domain.notifications.CourseNotificationCategory;
import de.tum.cit.aet.artemis.coursenotification.repository.UserCourseNotificationSettingPresetRepository;
import de.tum.cit.aet.artemis.coursenotification.repository.UserCourseNotificationSettingSpecificationRepository;

@ExtendWith(MockitoExtension.class)
class CourseNotificationSettingServiceTest {

    private CourseNotificationSettingService courseNotificationSettingService;

    @Mock
    private CourseNotificationRegistryService courseNotificationRegistryService;

    @Mock
    private UserCourseNotificationSettingSpecificationRepository userCourseNotificationSettingSpecificationRepository;

    @Mock
    private UserCourseNotificationSettingPresetRepository userCourseNotificationSettingPresetRepository;

    @Mock
    private CourseNotificationSettingPresetRegistryService courseNotificationSettingPresetRegistryService;

    @BeforeEach
    void setUp() {
        courseNotificationSettingService = new CourseNotificationSettingService(courseNotificationRegistryService, userCourseNotificationSettingSpecificationRepository,
                userCourseNotificationSettingPresetRepository, courseNotificationSettingPresetRegistryService);
    }

    @Test
    void shouldFilterRecipientsWhenUsingCustomSettings() {
        TestNotification notification = new TestNotification(123L);
        User user1 = createTestUser(1L);
        User user2 = createTestUser(2L);
        List<User> recipients = List.of(user1, user2);

        UserCourseNotificationSettingPreset customPreset = new UserCourseNotificationSettingPreset();
        customPreset.setSettingPreset((short) 0);
        when(userCourseNotificationSettingPresetRepository.findUserCourseNotificationSettingPresetByUserIdAndCourseId(anyLong(), eq(123L))).thenReturn(customPreset);

        Short notificationTypeId = 1;
        when(courseNotificationRegistryService.getNotificationIdentifier(notification.getClass())).thenReturn(notificationTypeId);

        UserCourseNotificationSettingSpecification user1Spec = new UserCourseNotificationSettingSpecification();
        user1Spec.setCourseNotificationType(notificationTypeId);
        user1Spec.setWebapp(true);
        user1Spec.setPush(false);
        user1Spec.setEmail(false);

        UserCourseNotificationSettingSpecification user2Spec = new UserCourseNotificationSettingSpecification();
        user2Spec.setCourseNotificationType(notificationTypeId);
        user2Spec.setWebapp(false);
        user2Spec.setPush(true);
        user2Spec.setEmail(true);

        when(userCourseNotificationSettingSpecificationRepository.findAllByUserIdAndCourseId(eq(1L), eq(123L))).thenReturn(List.of(user1Spec));
        when(userCourseNotificationSettingSpecificationRepository.findAllByUserIdAndCourseId(eq(2L), eq(123L))).thenReturn(List.of(user2Spec));

        List<User> filteredRecipients = courseNotificationSettingService.filterRecipientsBy(notification, recipients, NotificationSettingOption.WEBAPP);

        assertThat(filteredRecipients).hasSize(1);
        assertThat(filteredRecipients).containsExactly(user1);
    }

    @Test
    void shouldFilterRecipientsWhenUsingPresetSettings() {
        TestNotification notification = new TestNotification(123L);
        User user1 = createTestUser(1L);
        User user2 = createTestUser(2L);
        List<User> recipients = List.of(user1, user2);

        UserCourseNotificationSettingPreset preset1 = new UserCourseNotificationSettingPreset();
        preset1.setSettingPreset((short) 1);
        UserCourseNotificationSettingPreset preset2 = new UserCourseNotificationSettingPreset();
        preset2.setSettingPreset((short) 2);

        when(userCourseNotificationSettingPresetRepository.findUserCourseNotificationSettingPresetByUserIdAndCourseId(eq(1L), eq(123L))).thenReturn(preset1);
        when(userCourseNotificationSettingPresetRepository.findUserCourseNotificationSettingPresetByUserIdAndCourseId(eq(2L), eq(123L))).thenReturn(preset2);

        when(courseNotificationSettingPresetRegistryService.isPresetSettingEnabled(eq(1), any(), eq(NotificationSettingOption.PUSH))).thenReturn(true);
        when(courseNotificationSettingPresetRegistryService.isPresetSettingEnabled(eq(2), any(), eq(NotificationSettingOption.PUSH))).thenReturn(false);

        List<User> filteredRecipients = courseNotificationSettingService.filterRecipientsBy(notification, recipients, NotificationSettingOption.PUSH);

        assertThat(filteredRecipients).hasSize(1);
        assertThat(filteredRecipients).containsExactly(user1);
    }

    @Test
    void shouldReturnEmptyListWhenNoRecipientsMatchFilter() {
        TestNotification notification = new TestNotification(123L);
        User user1 = createTestUser(1L);
        User user2 = createTestUser(2L);
        List<User> recipients = List.of(user1, user2);

        UserCourseNotificationSettingPreset customPreset = new UserCourseNotificationSettingPreset();
        customPreset.setSettingPreset((short) 0);
        when(userCourseNotificationSettingPresetRepository.findUserCourseNotificationSettingPresetByUserIdAndCourseId(anyLong(), eq(123L))).thenReturn(customPreset);

        Short notificationTypeId = 1;
        when(courseNotificationRegistryService.getNotificationIdentifier(notification.getClass())).thenReturn(notificationTypeId);

        UserCourseNotificationSettingSpecification userSpec = new UserCourseNotificationSettingSpecification();
        userSpec.setCourseNotificationType(notificationTypeId);
        userSpec.setWebapp(true);
        userSpec.setPush(true);
        userSpec.setEmail(false);

        when(userCourseNotificationSettingSpecificationRepository.findAllByUserIdAndCourseId(anyLong(), eq(123L))).thenReturn(List.of(userSpec));

        List<User> filteredRecipients = courseNotificationSettingService.filterRecipientsBy(notification, recipients, NotificationSettingOption.EMAIL);

        assertThat(filteredRecipients).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenUserNotificationSpecificationNotFound() {
        TestNotification notification = new TestNotification(123L);
        User user = createTestUser(1L);
        List<User> recipients = List.of(user);

        UserCourseNotificationSettingPreset customPreset = new UserCourseNotificationSettingPreset();
        customPreset.setSettingPreset((short) 0);
        when(userCourseNotificationSettingPresetRepository.findUserCourseNotificationSettingPresetByUserIdAndCourseId(anyLong(), eq(123L))).thenReturn(customPreset);

        Short notificationTypeId = 1;
        when(courseNotificationRegistryService.getNotificationIdentifier(notification.getClass())).thenReturn(notificationTypeId);

        UserCourseNotificationSettingSpecification differentSpec = new UserCourseNotificationSettingSpecification();
        differentSpec.setCourseNotificationType((short) 2); // Different from the notificationTypeId
        differentSpec.setWebapp(true);

        when(userCourseNotificationSettingSpecificationRepository.findAllByUserIdAndCourseId(anyLong(), eq(123L))).thenReturn(List.of(differentSpec));

        List<User> filteredRecipients = courseNotificationSettingService.filterRecipientsBy(notification, recipients, NotificationSettingOption.WEBAPP);

        assertThat(filteredRecipients).isEmpty();
    }

    private User createTestUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setLogin("user" + id);
        return user;
    }

    static class TestNotification extends CourseNotification {

        final Long courseId;

        TestNotification(Long courseId) {
            super(courseId, ZonedDateTime.now());
            this.courseId = courseId;
        }

        @Override
        public String getReadableNotificationType() {
            return "Test Notification";
        }

        @Override
        public CourseNotificationCategory getCourseNotificationCategory() {
            return CourseNotificationCategory.GENERAL;
        }

        @Override
        public List<NotificationSettingOption> getSupportedChannels() {
            return List.of(NotificationSettingOption.WEBAPP, NotificationSettingOption.PUSH, NotificationSettingOption.EMAIL);
        }

        @Override
        public Map<String, String> getParameters() {
            return Map.of("key", "value");
        }

        @Override
        public Duration getCleanupDuration() {
            return Duration.ofDays(30);
        }
    }
}
