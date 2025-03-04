package de.tum.cit.aet.artemis.course_notification.domain.setting_presets;

import java.util.Map;

import de.tum.cit.aet.artemis.course_notification.annotations.CourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.course_notification.domain.NotificationSettingOption;
import de.tum.cit.aet.artemis.course_notification.domain.notifications.NewPostNotification;

@CourseNotificationSettingPreset(2)
public class AllActivityUserCourseNotificationSettingPreset extends UserCourseNotificationSettingPreset {

    public AllActivityUserCourseNotificationSettingPreset() {
        presetMap = Map.of(NewPostNotification.class, Map.of(NotificationSettingOption.EMAIL, false, NotificationSettingOption.WEBAPP, true, NotificationSettingOption.PUSH, true));
    }
}
