package de.tum.cit.aet.artemis.coursenotification.domain.setting_presets;

import java.util.Map;

import de.tum.cit.aet.artemis.coursenotification.annotations.CourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.coursenotification.domain.NotificationSettingOption;
import de.tum.cit.aet.artemis.coursenotification.domain.notifications.NewPostNotification;

@CourseNotificationSettingPreset(3)
public class IgnoreUserCourseNotificationSettingPreset extends UserCourseNotificationSettingPreset {

    public IgnoreUserCourseNotificationSettingPreset() {
        presetMap = Map.of(NewPostNotification.class,
                Map.of(NotificationSettingOption.EMAIL, false, NotificationSettingOption.WEBAPP, false, NotificationSettingOption.PUSH, false));
    }
}
