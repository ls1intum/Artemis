package de.tum.cit.aet.artemis.course_notification.domain.setting_presets;

import java.util.Map;

import de.tum.cit.aet.artemis.course_notification.annotations.CourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.course_notification.domain.NotificationSettingOption;
import de.tum.cit.aet.artemis.course_notification.domain.notifications.NewPostNotification;

@CourseNotificationSettingPreset(3)
public class IgnoreUserCourseNotificationSettingPreset extends UserCourseNotificationSettingPreset {

    public IgnoreUserCourseNotificationSettingPreset() {
        presetMap = Map.of(NewPostNotification.class,
                Map.of(NotificationSettingOption.EMAIL, false, NotificationSettingOption.WEBAPP, false, NotificationSettingOption.PUSH, false));
    }
}
