package de.tum.cit.aet.artemis.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.domain.UserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.notification.domain.UserCourseNotificationSettingSpecification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.CourseNotification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.CourseNotificationCategory;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.NewPostNotification;
import de.tum.cit.aet.artemis.notification.domain.setting_presets.DefaultUserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.notification.dto.UserCourseNotificationSettingPresetEntryDTO;
import de.tum.cit.aet.artemis.notification.dto.UserCourseNotificationSettingSpecificationEntryDTO;
import de.tum.cit.aet.artemis.notification.dto.payload.CourseNotificationPayloadDTO;
import de.tum.cit.aet.artemis.notification.dto.payload.ExerciseOpenForPracticePayloadDTO;
import de.tum.cit.aet.artemis.notification.test_repository.UserCourseNotificationSettingPresetTestRepository;
import de.tum.cit.aet.artemis.notification.test_repository.UserCourseNotificationSettingSpecificationTestRepository;

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
    private de.tum.cit.aet.artemis.notification.domain.setting_presets.UserCourseNotificationSettingPreset mockPreset;

    private final Long userId = 1L;

    private final Long courseId = 2L;

    private final Short customPresetId = 0;

    private final Short notificationTypeId = 1;

    @BeforeEach
    void setUp() {
        courseNotificationSettingService = new CourseNotificationSettingService(courseNotificationRegistryService, userCourseNotificationSettingSpecificationRepository,
                userCourseNotificationSettingPresetRepository, courseNotificationSettingPresetRegistryService);
    }

    @Test
    void shouldCreateNewPresetWhenNoneExists() {
        var defaultPresetId = 1;
        when(courseNotificationSettingPresetRegistryService.getPresetId(DefaultUserCourseNotificationSettingPreset.class)).thenReturn(defaultPresetId);
        when(userCourseNotificationSettingPresetRepository.findUserCourseNotificationSettingPresetByUserIdAndCourseId(userId, courseId)).thenReturn(null);

        courseNotificationSettingService.applyPreset((short) 2, userId, courseId);

        verify(userCourseNotificationSettingPresetRepository).save(any(UserCourseNotificationSettingPreset.class));
    }

    @Test
    void shouldDoNothingWhenSamePresetIsSelected() {
        UserCourseNotificationSettingPreset existingPreset = new UserCourseNotificationSettingPreset();
        existingPreset.setSettingPreset((short) 2);

        when(userCourseNotificationSettingPresetRepository.findUserCourseNotificationSettingPresetByUserIdAndCourseId(userId, courseId)).thenReturn(existingPreset);

        courseNotificationSettingService.applyPreset((short) 2, userId, courseId);

        verify(userCourseNotificationSettingPresetRepository, never()).save(any(UserCourseNotificationSettingPreset.class));
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
    }

    @Test
    void shouldDeleteExistingSpecificationsWhenNonCustomPresetIsSelected() {
        UserCourseNotificationSettingPreset existingPreset = new UserCourseNotificationSettingPreset();
        existingPreset.setSettingPreset(customPresetId);

        List<UserCourseNotificationSettingSpecification> existingSpecs = new ArrayList<>();
        existingSpecs.add(new UserCourseNotificationSettingSpecification());

        when(userCourseNotificationSettingPresetRepository.findUserCourseNotificationSettingPresetByUserIdAndCourseId(userId, courseId)).thenReturn(existingPreset);
        when(userCourseNotificationSettingSpecificationRepository.findAllEntitiesByUserIdAndCourseId(userId, courseId)).thenReturn(existingSpecs);
        when(courseNotificationSettingPresetRegistryService.getPresetById(anyShort())).thenReturn(mockPreset);

        courseNotificationSettingService.applyPreset((short) 2, userId, courseId);

        verify(userCourseNotificationSettingPresetRepository).save(any(UserCourseNotificationSettingPreset.class));
        verify(userCourseNotificationSettingSpecificationRepository).deleteAll(existingSpecs);
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

        when(userCourseNotificationSettingPresetRepository.findSettingPresetsByUserIdsAndCourseId(anySet(), eq(123L))).thenReturn(List.of(customPreset(1L), customPreset(2L)));

        Short notificationTypeId = 1;
        when(courseNotificationRegistryService.getNotificationIdentifier(notification.getClass())).thenReturn(notificationTypeId);

        when(userCourseNotificationSettingSpecificationRepository.findAllByUserIdsAndCourseId(anySet(), eq(123L)))
                .thenReturn(List.of(new UserCourseNotificationSettingSpecificationEntryDTO(1L, notificationTypeId, false, false, true),
                        new UserCourseNotificationSettingSpecificationEntryDTO(2L, notificationTypeId, true, true, false)));

        List<User> filteredRecipients = filterWithLoadedSettings(notification, recipients, NotificationChannelOption.WEBAPP);

        assertThat(filteredRecipients).hasSize(1);
        assertThat(filteredRecipients).containsExactly(user1);
    }

    @Test
    void shouldFilterRecipientsWhenUsingPresetSettings() {
        TestNotification notification = new TestNotification(123L);
        User user1 = createTestUser(1L);
        User user2 = createTestUser(2L);
        List<User> recipients = List.of(user1, user2);

        when(userCourseNotificationSettingPresetRepository.findSettingPresetsByUserIdsAndCourseId(anySet(), eq(123L)))
                .thenReturn(List.of(new UserCourseNotificationSettingPresetEntryDTO(1L, (short) 1), new UserCourseNotificationSettingPresetEntryDTO(2L, (short) 2)));

        when(courseNotificationSettingPresetRegistryService.isPresetSettingEnabled(eq(1), any(), eq(NotificationChannelOption.PUSH))).thenReturn(true);
        when(courseNotificationSettingPresetRegistryService.isPresetSettingEnabled(eq(2), any(), eq(NotificationChannelOption.PUSH))).thenReturn(false);

        List<User> filteredRecipients = filterWithLoadedSettings(notification, recipients, NotificationChannelOption.PUSH);

        assertThat(filteredRecipients).hasSize(1);
        assertThat(filteredRecipients).containsExactly(user1);
    }

    @Test
    void shouldReturnEmptyListWhenNoRecipientsMatchFilter() {
        TestNotification notification = new TestNotification(123L);
        User user1 = createTestUser(1L);
        User user2 = createTestUser(2L);
        List<User> recipients = List.of(user1, user2);

        when(userCourseNotificationSettingPresetRepository.findSettingPresetsByUserIdsAndCourseId(anySet(), eq(123L))).thenReturn(List.of(customPreset(1L), customPreset(2L)));

        Short notificationTypeId = 1;
        when(courseNotificationRegistryService.getNotificationIdentifier(notification.getClass())).thenReturn(notificationTypeId);

        when(userCourseNotificationSettingSpecificationRepository.findAllByUserIdsAndCourseId(anySet(), eq(123L)))
                .thenReturn(List.of(new UserCourseNotificationSettingSpecificationEntryDTO(1L, notificationTypeId, false, true, true),
                        new UserCourseNotificationSettingSpecificationEntryDTO(2L, notificationTypeId, false, true, true)));

        List<User> filteredRecipients = filterWithLoadedSettings(notification, recipients, NotificationChannelOption.EMAIL);

        assertThat(filteredRecipients).isEmpty();
    }

    @Test
    void shouldFallBackToDefaultPresetWhenCustomSpecificationMissingAndDefaultDisabled() {
        TestNotification notification = new TestNotification(123L);
        User user = createTestUser(1L);
        List<User> recipients = List.of(user);

        when(userCourseNotificationSettingPresetRepository.findSettingPresetsByUserIdsAndCourseId(anySet(), eq(123L))).thenReturn(List.of(customPreset(1L)));

        Short notificationTypeId = 1;
        when(courseNotificationRegistryService.getNotificationIdentifier(notification.getClass())).thenReturn(notificationTypeId);

        // The type differs from the notificationTypeId, so this user has no row for the notification being sent.
        when(userCourseNotificationSettingSpecificationRepository.findAllByUserIdsAndCourseId(anySet(), eq(123L)))
                .thenReturn(List.of(new UserCourseNotificationSettingSpecificationEntryDTO(1L, (short) 2, false, false, true)));

        // A custom preset with no specification row for this type must fall back to the default preset (id 1) value.
        when(courseNotificationSettingPresetRegistryService.isPresetSettingEnabled(eq(1), any(), eq(NotificationChannelOption.WEBAPP))).thenReturn(false);

        List<User> filteredRecipients = filterWithLoadedSettings(notification, recipients, NotificationChannelOption.WEBAPP);

        assertThat(filteredRecipients).isEmpty();
    }

    @Test
    void shouldFallBackToDefaultPresetWhenCustomSpecificationMissingAndDefaultEnabled() {
        // Regression test: tutors on a custom preset created before a notification type existed have no
        // specification row for it. The missing row must fall back to the default preset value rather than
        // silently dropping the recipient (which previously hid Iris review notifications).
        TestNotification notification = new TestNotification(123L);
        User user = createTestUser(1L);
        List<User> recipients = List.of(user);

        when(userCourseNotificationSettingPresetRepository.findSettingPresetsByUserIdsAndCourseId(anySet(), eq(123L))).thenReturn(List.of(customPreset(1L)));

        // No specification row exists for this notification type (empty list), so the type identifier is never consulted.
        when(userCourseNotificationSettingSpecificationRepository.findAllByUserIdsAndCourseId(anySet(), eq(123L))).thenReturn(List.of());

        // The default preset enables this notification for WEBAPP, so the recipient must be kept.
        when(courseNotificationSettingPresetRegistryService.isPresetSettingEnabled(eq(1), any(), eq(NotificationChannelOption.WEBAPP))).thenReturn(true);

        List<User> filteredRecipients = filterWithLoadedSettings(notification, recipients, NotificationChannelOption.WEBAPP);

        assertThat(filteredRecipients).containsExactly(user);
    }

    /**
     * The reason the per-user reads were replaced: filtering runs once per delivery channel, so asking per recipient
     * cost a lookup per recipient per channel. This pins the bound at one query for the presets and one for the
     * specifications, however many recipients and channels there are.
     */
    @Test
    void shouldReadSettingsOnceRegardlessOfRecipientCountAndChannels() {
        TestNotification notification = new TestNotification(123L);
        List<User> recipients = LongStream.rangeClosed(1, 250).mapToObj(this::createTestUser).toList();

        when(userCourseNotificationSettingPresetRepository.findSettingPresetsByUserIdsAndCourseId(anySet(), eq(123L)))
                .thenReturn(recipients.stream().map(recipient -> customPreset(recipient.getId())).toList());
        when(userCourseNotificationSettingSpecificationRepository.findAllByUserIdsAndCourseId(anySet(), eq(123L))).thenReturn(List.of());
        when(courseNotificationSettingPresetRegistryService.isPresetSettingEnabled(eq(1), any(), any())).thenReturn(true);

        var settings = courseNotificationSettingService.loadSettingsFor(notification.courseId, recipients);
        for (NotificationChannelOption channel : NotificationChannelOption.values()) {
            assertThat(courseNotificationSettingService.filterRecipientsBy(notification, recipients, channel, settings)).hasSize(250);
        }

        verify(userCourseNotificationSettingPresetRepository, times(1)).findSettingPresetsByUserIdsAndCourseId(anySet(), eq(123L));
        verify(userCourseNotificationSettingSpecificationRepository, times(1)).findAllByUserIdsAndCourseId(anySet(), eq(123L));
        verify(userCourseNotificationSettingPresetRepository, never()).findSettingPresetByUserIdAndCourseId(anyLong(), anyLong());
        verify(userCourseNotificationSettingSpecificationRepository, never()).findAllByUserIdAndCourseId(anyLong(), anyLong());
    }

    /**
     * Nobody on a custom preset means no specification rows are worth reading at all.
     */
    @Test
    void shouldNotReadSpecificationsWhenNobodyUsesACustomPreset() {
        TestNotification notification = new TestNotification(123L);
        List<User> recipients = List.of(createTestUser(1L), createTestUser(2L));

        when(userCourseNotificationSettingPresetRepository.findSettingPresetsByUserIdsAndCourseId(anySet(), eq(123L)))
                .thenReturn(List.of(new UserCourseNotificationSettingPresetEntryDTO(1L, (short) 1), new UserCourseNotificationSettingPresetEntryDTO(2L, (short) 2)));

        courseNotificationSettingService.loadSettingsFor(notification.courseId, recipients);

        verify(userCourseNotificationSettingSpecificationRepository, never()).findAllByUserIdsAndCourseId(anySet(), anyLong());
    }

    /**
     * Reads the recipients' settings the way the send path does, then filters with them.
     */
    private List<User> filterWithLoadedSettings(TestNotification notification, List<User> recipients, NotificationChannelOption channel) {
        var settings = courseNotificationSettingService.loadSettingsFor(notification.courseId, recipients);
        return courseNotificationSettingService.filterRecipientsBy(notification, recipients, channel, settings);
    }

    private static UserCourseNotificationSettingPresetEntryDTO customPreset(long userId) {
        return new UserCourseNotificationSettingPresetEntryDTO(userId, (short) 0);
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
        public String getRelativeWebAppUrl() {
            return "/";
        }

        @Override
        public Map<String, Object> getParameters() {
            return Map.of("key", "value");
        }

        @Override
        public Duration getCleanupDuration() {
            return Duration.ofDays(30);
        }

        @Override
        public CourseNotificationPayloadDTO payload() {
            return new ExerciseOpenForPracticePayloadDTO(1L, "Test Exercise");
        }
    }
}
