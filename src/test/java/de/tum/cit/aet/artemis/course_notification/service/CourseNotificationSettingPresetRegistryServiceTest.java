package de.tum.cit.aet.artemis.course_notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.course_notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.course_notification.domain.NotificationSettingOption;
import de.tum.cit.aet.artemis.course_notification.domain.notifications.CourseNotification;
import de.tum.cit.aet.artemis.course_notification.domain.notifications.CourseNotificationCategory;
import de.tum.cit.aet.artemis.course_notification.domain.setting_presets.UserCourseNotificationSettingPreset;

@ExtendWith(MockitoExtension.class)
class CourseNotificationSettingPresetRegistryServiceTest {

    private CourseNotificationSettingPresetRegistryService settingPresetRegistry;

    @BeforeEach
    void setUp() {
        settingPresetRegistry = new CourseNotificationSettingPresetRegistryService();
    }

    @Test
    void shouldReturnFalseWhenPresetIdDoesNotExist() {
        int nonExistentPresetId = 999;
        Class<? extends CourseNotification> notificationType = TestNotification.class;
        NotificationSettingOption option = NotificationSettingOption.WEBAPP;

        boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(settingPresetRegistry, "isPresetSettingEnabled", nonExistentPresetId, notificationType, option));

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnTrueWhenPresetExistsAndSettingIsEnabled() {
        int presetId = 1;
        Class<? extends CourseNotification> notificationType = TestNotification.class;
        NotificationSettingOption option = NotificationSettingOption.WEBAPP;

        UserCourseNotificationSettingPreset mockPreset = mock(UserCourseNotificationSettingPreset.class);
        when(mockPreset.isEnabled(any(), any())).thenReturn(true);

        Map<Integer, UserCourseNotificationSettingPreset> presetsMap = Map.of(presetId, mockPreset);
        ReflectionTestUtils.setField(settingPresetRegistry, "presets", presetsMap);

        boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(settingPresetRegistry, "isPresetSettingEnabled", presetId, notificationType, option));

        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenPresetExistsButSettingIsDisabled() {
        int presetId = 1;
        Class<? extends CourseNotification> notificationType = TestNotification.class;
        NotificationSettingOption option = NotificationSettingOption.WEBAPP;

        UserCourseNotificationSettingPreset mockPreset = mock(UserCourseNotificationSettingPreset.class);
        when(mockPreset.isEnabled(any(), any())).thenReturn(false);

        Map<Integer, UserCourseNotificationSettingPreset> presetsMap = Map.of(presetId, mockPreset);
        ReflectionTestUtils.setField(settingPresetRegistry, "presets", presetsMap);

        boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(settingPresetRegistry, "isPresetSettingEnabled", presetId, notificationType, option));

        assertThat(result).isFalse();
    }

    @Test
    void shouldPassCorrectParametersToPresetWhenCheckingIfEnabled() {
        int presetId = 1;
        Class<? extends CourseNotification> notificationType = TestNotification.class;
        NotificationSettingOption option = NotificationSettingOption.PUSH;

        UserCourseNotificationSettingPreset mockPreset = mock(UserCourseNotificationSettingPreset.class);
        when(mockPreset.isEnabled(notificationType, option)).thenReturn(true);

        Map<Integer, UserCourseNotificationSettingPreset> presetsMap = Map.of(presetId, mockPreset);
        ReflectionTestUtils.setField(settingPresetRegistry, "presets", presetsMap);

        boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(settingPresetRegistry, "isPresetSettingEnabled", presetId, notificationType, option));

        assertThat(result).isTrue();
    }

    @CourseNotificationType(1)
    static class TestNotification extends CourseNotification {

        public TestNotification(Long courseId, ZonedDateTime creationDate) {
            super(courseId, creationDate);
        }

        @Override
        public CourseNotificationCategory getCourseNotificationCategory() {
            return CourseNotificationCategory.COMMUNICATION;
        }

        @Override
        public Duration getCleanupDuration() {
            return Duration.ofDays(1);
        }

        @Override
        public List<NotificationSettingOption> getSupportedChannels() {
            return List.of(NotificationSettingOption.EMAIL, NotificationSettingOption.WEBAPP, NotificationSettingOption.PUSH);
        }
    }
}
