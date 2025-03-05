package de.tum.cit.aet.artemis.coursenotification.domain.setting_presets;

import java.util.Map;

import de.tum.cit.aet.artemis.coursenotification.annotations.CourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.coursenotification.domain.NotificationSettingOption;
import de.tum.cit.aet.artemis.coursenotification.domain.notifications.NewPostNotification;

@CourseNotificationSettingPreset(2)
public class AllActivityUserCourseNotificationSettingPreset extends UserCourseNotificationSettingPreset {

    public AllActivityUserCourseNotificationSettingPreset() {
        presetMap = Map.of(NewPostNotification.class, Map.of(NotificationSettingOption.EMAIL, false, NotificationSettingOption.WEBAPP, true, NotificationSettingOption.PUSH, true));
    }
}
