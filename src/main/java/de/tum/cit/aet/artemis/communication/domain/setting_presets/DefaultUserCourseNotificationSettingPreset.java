package de.tum.cit.aet.artemis.communication.domain.setting_presets;

import java.util.Map;

import de.tum.cit.aet.artemis.communication.annotations.CourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.communication.domain.NotificationSettingOption;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.NewPostNotification;

@CourseNotificationSettingPreset(1)
public class DefaultUserCourseNotificationSettingPreset extends UserCourseNotificationSettingPreset {

    public DefaultUserCourseNotificationSettingPreset() {
        presetMap = Map.of(NewPostNotification.class, Map.of(NotificationSettingOption.EMAIL, false, NotificationSettingOption.WEBAPP, true, NotificationSettingOption.PUSH, true));
    }
}
