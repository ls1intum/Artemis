package de.tum.cit.aet.artemis.communication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.communication.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.communication.domain.UserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.communication.domain.UserCourseNotificationSettingSpecification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.CourseNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.CourseNotificationCategory;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.NewPostNotification;
import de.tum.cit.aet.artemis.communication.domain.setting_presets.DefaultUserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.communication.test_repository.UserCourseNotificationSettingPresetTestRepository;
import de.tum.cit.aet.artemis.communication.test_repository.UserCourseNotificationSettingSpecificationTestRepository;
import de.tum.cit.aet.artemis.core.domain.User;

@ExtendWith(MockitoExtension.class)
class CourseNotificationSettingServiceTest {

    private CourseNotificationSettingService courseNotificationSettingService;

    @Mock
    private CourseNotificationRegistryService courseNotificationRegistryService;

    @Mock
    private UserCourseNotificationSettingSpecificationTestRepository userCourseNotificationSettingSpecificationRepository;

    @Mock
    private UserCourseNotificationSettingPresetTestRepository userCourseNotificationSettingPresetRepository;

    @Mock
    private CourseNotificationSettingPresetRegistryService courseNotificationSettingPresetRegistryService;

    @Mock
    private CourseNotificationCacheService courseNotificationCacheService;

    @Mock
    private de.tum.cit.aet.artemis.communication.domain.setting_presets.UserCourseNotificationSettingPreset mockPreset;

    private final Long userId = 1L;

    private final Long courseId = 2L;

    private final Short customPresetId = 0;

    private final Short notificationTypeId = 1;

    @BeforeEach
    void setUp() {
        courseNotificationSettingService = new CourseNotificationSettingService(courseNotificationRegistryService, courseNotificationCacheService,
                userCourseNotificationSettingSpecificationRepository, userCourseNotificationSettingPresetRepository, courseNotificationSettingPresetRegistryService);
    }

    @Test
    void shouldCreateNewPresetWhenNoneExists() {
        var defaultPresetId = 1;
        when(courseNotificationSettingPresetRegistryService.getPresetId(DefaultUserCourseNotificationSettingPreset.class)).thenReturn(defaultPresetId);
        when(userCourseNotificationSettingPresetRepository.findUserCourseNotificationSettingPresetByUserIdAndCourseId(userId, courseId)).thenReturn(null);

        courseNotificationSettingService.applyPreset((short) 2, userId, courseId);

        verify(userCourseNotificationSettingPresetRepository).save(any(UserCourseNotificationSettingPreset.class));
        verify(courseNotificationCacheService).invalidateCourseNotificationSettingSpecificationCacheForUser(userId, courseId);
    }

    @Test
    void shouldDoNothingWhenSamePresetIsSelected() {
        UserCourseNotificationSettingPreset existingPreset = new UserCourseNotificationSettingPreset();
        existingPreset.setSettingPreset((short) 2);

        when(userCourseNotificationSettingPresetRepository.findUserCourseNotificationSettingPresetByUserIdAndCourseId(userId, courseId)).thenReturn(existingPreset);

        courseNotificationSettingService.applyPreset((short) 2, userId, courseId);

        verify(userCourseNotificationSettingPresetRepository, never()).save(any(UserCourseNotificationSettingPreset.class));
        verify(courseNotificationCacheService, never()).invalidateCourseNotificationSettingSpecificationCacheForUser(anyLong(), anyLong());
    }

    @Test
    void shouldSaveCustomSpecificationsWhenCustomPresetIsSelected() {
        UserCourseNotificationSettingPreset existingPreset = new UserCourseNotificationSettingPreset();
        existingPreset.setSettingPreset((short) 2);

        when(userCourseNotificationSettingPresetRepository.findUserCourseNotificationSettingPresetByUserIdAndCourseId(userId, courseId)).thenReturn(existingPreset);
        when(courseNotificationSettingPresetRegistryService.getPresetById(anyShort())).thenReturn(mockPreset);

        Map<Class<? extends CourseNotification>, Map<NotificationChannelOption, Boolean>> presetMap = new HashMap<>();
        Map<NotificationChannelOption, Boolean> channelSettings = new HashMap<>();
        channelSettings.put(NotificationChannelOption.EMAIL, true);
        channelSettings.put(NotificationChannelOption.WEBAPP, true);
        channelSettings.put(NotificationChannelOption.PUSH, false);
        presetMap.put(NewPostNotification.class, channelSettings);

        when(mockPreset.getPresetMap()).thenReturn(presetMap);
        when(courseNotificationRegistryService.getNotificationIdentifier(any())).thenReturn(notificationTypeId);

        courseNotificationSettingService.applyPreset(customPresetId, userId, courseId);

        verify(userCourseNotificationSettingPresetRepository).save(any(UserCourseNotificationSettingPreset.class));
        verify(userCourseNotificationSettingSpecificationRepository).saveAll(any());
        verify(courseNotificationCacheService).invalidateCourseNotificationSettingSpecificationCacheForUser(userId, courseId);
    }

    @Test
    void shouldDeleteExistingSpecificationsWhenNonCustomPresetIsSelected() {
        UserCourseNotificationSettingPreset existingPreset = new UserCourseNotificationSettingPreset();
        existingPreset.setSettingPreset(customPresetId);

        List<UserCourseNotificationSettingSpecification> existingSpecs = new ArrayList<>();
        existingSpecs.add(new UserCourseNotificationSettingSpecification());

        when(userCourseNotificationSettingPresetRepository.findUserCourseNotificationSettingPresetByUserIdAndCourseId(userId, courseId)).thenReturn(existingPreset);
        when(userCourseNotificationSettingSpecificationRepository.findAllByUserIdAndCourseId(userId, courseId)).thenReturn(existingSpecs);
        when(courseNotificationSettingPresetRegistryService.getPresetById(anyShort())).thenReturn(mockPreset);

        courseNotificationSettingService.applyPreset((short) 2, userId, courseId);

        verify(userCourseNotificationSettingPresetRepository).save(any(UserCourseNotificationSettingPreset.class));
        verify(userCourseNotificationSettingSpecificationRepository).deleteAll(existingSpecs);
        verify(courseNotificationCacheService).invalidateCourseNotificationSettingSpecificationCacheForUser(userId, courseId);
    }

    @Test
    void shouldApplyCustomPresetFirstWhenApplyingSpecification() {
        Map<Short, Map<NotificationChannelOption, Boolean>> notificationTypeChannels = new HashMap<>();
        Map<NotificationChannelOption, Boolean> channelSettings = new HashMap<>();
        channelSettings.put(NotificationChannelOption.EMAIL, true);
        channelSettings.put(NotificationChannelOption.WEBAPP, false);
        channelSettings.put(NotificationChannelOption.PUSH, true);
        notificationTypeChannels.put(notificationTypeId, channelSettings);

        when(userCourseNotificationSettingSpecificationRepository.findAllByUserIdAndCourseIdAndCourseNotificationTypeIn(eq(userId), eq(courseId), any()))
                .thenReturn(new ArrayList<>());

        courseNotificationSettingService.applySpecification(notificationTypeChannels, userId, courseId);

        verify(userCourseNotificationSettingPresetRepository).findUserCourseNotificationSettingPresetByUserIdAndCourseId(userId, courseId);
        verify(userCourseNotificationSettingSpecificationRepository).saveAll(any());
        verify(courseNotificationCacheService).invalidateCourseNotificationSettingSpecificationCacheForUser(userId, courseId);
    }

    @Test
    void shouldUpdateExistingSpecificationsWhenApplyingSpecification() {
        Map<Short, Map<NotificationChannelOption, Boolean>> notificationTypeChannels = new HashMap<>();
        Map<NotificationChannelOption, Boolean> channelSettings = new HashMap<>();
        channelSettings.put(NotificationChannelOption.EMAIL, true);
        channelSettings.put(NotificationChannelOption.WEBAPP, false);
        channelSettings.put(NotificationChannelOption.PUSH, true);
        notificationTypeChannels.put(notificationTypeId, channelSettings);

        UserCourseNotificationSettingSpecification existingSpec = new UserCourseNotificationSettingSpecification();
        existingSpec.setCourseNotificationType(notificationTypeId);
        existingSpec.setEmail(false);
        existingSpec.setWebapp(true);
        existingSpec.setPush(false);

        List<UserCourseNotificationSettingSpecification> existingSpecs = new ArrayList<>();
        existingSpecs.add(existingSpec);

        when(userCourseNotificationSettingSpecificationRepository.findAllByUserIdAndCourseIdAndCourseNotificationTypeIn(eq(userId), eq(courseId), any())).thenReturn(existingSpecs);

        UserCourseNotificationSettingPreset existingPreset = new UserCourseNotificationSettingPreset();
        existingPreset.setSettingPreset(customPresetId);
        when(userCourseNotificationSettingPresetRepository.findUserCourseNotificationSettingPresetByUserIdAndCourseId(userId, courseId)).thenReturn(existingPreset);

        courseNotificationSettingService.applySpecification(notificationTypeChannels, userId, courseId);

        verify(userCourseNotificationSettingSpecificationRepository).saveAll(any());
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

        List<User> filteredRecipients = courseNotificationSettingService.filterRecipientsBy(notification, recipients, NotificationChannelOption.WEBAPP);

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

        when(courseNotificationSettingPresetRegistryService.isPresetSettingEnabled(eq(1), any(), eq(NotificationChannelOption.PUSH))).thenReturn(true);
        when(courseNotificationSettingPresetRegistryService.isPresetSettingEnabled(eq(2), any(), eq(NotificationChannelOption.PUSH))).thenReturn(false);

        List<User> filteredRecipients = courseNotificationSettingService.filterRecipientsBy(notification, recipients, NotificationChannelOption.PUSH);

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

        List<User> filteredRecipients = courseNotificationSettingService.filterRecipientsBy(notification, recipients, NotificationChannelOption.EMAIL);

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

        List<User> filteredRecipients = courseNotificationSettingService.filterRecipientsBy(notification, recipients, NotificationChannelOption.WEBAPP);

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
            super(1L, courseId, "Test Course", "image.url", ZonedDateTime.now());
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
        public List<NotificationChannelOption> getSupportedChannels() {
            return List.of(NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH, NotificationChannelOption.EMAIL);
        }

        @Override
        public Map<String, Object> getParameters() {
            return Map.of("key", "value");
        }

        @Override
        public Duration getCleanupDuration() {
            return Duration.ofDays(30);
        }
    }
}
