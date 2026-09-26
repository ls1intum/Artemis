package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.domain.setting_presets.AllActivityUserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.notification.domain.setting_presets.DefaultUserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.notification.domain.setting_presets.IgnoreUserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.notification.domain.setting_presets.UserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.notification.dto.payload.AtlasCompetencyUpdatePayloadDTO;

class AtlasCompetencyUpdateNotificationTest {

    private static final long COURSE_ID = 5L;

    private static final AtlasCompetencyUpdatePayloadDTO PAYLOAD = new AtlasCompetencyUpdatePayloadDTO("COMPLETED", 2, 3, 1, 0, 0, 2, 0,
            "Created competency Sorting \\(APPLY\\)\\.\n*Covers the new exercise\\.*", 0);

    @Test
    void shouldOnlySupportEmailAndLinkToCompetencyManagement() {
        var notification = new AtlasCompetencyUpdateNotification(COURSE_ID, "Algorithms", "icon.png", PAYLOAD);

        assertThat(notification.getSupportedChannels()).containsExactly(NotificationChannelOption.EMAIL);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/course-management/5/competency-management");
        assertThat(notification.getCourseNotificationCategory()).isEqualTo(CourseNotificationCategory.GENERAL);
        assertThat(notification.getReadableNotificationType()).isEqualTo("atlasCompetencyUpdateNotification");
    }

    @Test
    void shouldRestorePayloadFromStoredParameters() {
        var created = new AtlasCompetencyUpdateNotification(COURSE_ID, "Algorithms", "icon.png", PAYLOAD);
        // The store keeps every parameter as a string, which is what the reading constructor receives.
        Map<String, String> stored = new HashMap<>();
        created.getParameters().forEach((key, value) -> stored.put(key, value == null ? null : value.toString()));

        var loaded = new AtlasCompetencyUpdateNotification(1L, COURSE_ID, ZonedDateTime.now(), stored);

        assertThat(loaded.payload()).isEqualTo(PAYLOAD);
        assertThat(loaded.courseTitle()).isEqualTo("Algorithms");
    }

    @Test
    void shouldKeepEveryParameterKeyWithinTheStoredKeyLength() {
        // course_notification_parameter.param_key is varchar(20), so a longer component name fails the insert.
        var notification = new AtlasCompetencyUpdateNotification(COURSE_ID, "Algorithms", "icon.png", PAYLOAD);

        assertThat(notification.getParameters().keySet()).allSatisfy(key -> assertThat(key).hasSizeLessThanOrEqualTo(20));
    }

    static Stream<UserCourseNotificationSettingPreset> presets() {
        return Stream.of(new DefaultUserCourseNotificationSettingPreset(), new AllActivityUserCourseNotificationSettingPreset(), new IgnoreUserCourseNotificationSettingPreset());
    }

    @ParameterizedTest
    @MethodSource("presets")
    void shouldBeSwitchedOffInEveryPreset(UserCourseNotificationSettingPreset preset) {
        // Every channel key must be present: switching to a custom preset copies all three into non-null booleans.
        assertThat(preset.getPresetMap().get(AtlasCompetencyUpdateNotification.class)).containsOnlyKeys(List.of(NotificationChannelOption.values()))
                .allSatisfy((channel, enabled) -> assertThat(enabled).isFalse());
    }
}
