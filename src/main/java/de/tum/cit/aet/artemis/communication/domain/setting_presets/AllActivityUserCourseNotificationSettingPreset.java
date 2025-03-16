package de.tum.cit.aet.artemis.communication.domain.setting_presets;

import java.util.Map;

import de.tum.cit.aet.artemis.communication.annotations.CourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.communication.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.NewPostNotification;

@CourseNotificationSettingPreset(2)
public class AllActivityUserCourseNotificationSettingPreset extends UserCourseNotificationSettingPreset {

    public AllActivityUserCourseNotificationSettingPreset() {
        presetMap = Map.of(NewPostNotification.class, Map.of(NotificationChannelOption.EMAIL, false, NotificationChannelOption.WEBAPP, true, NotificationChannelOption.PUSH, true));
    }
}
