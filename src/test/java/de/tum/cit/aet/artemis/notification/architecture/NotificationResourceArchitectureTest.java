package de.tum.cit.aet.artemis.notification.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.notification.web.CourseNotificationResource;
import de.tum.cit.aet.artemis.notification.web.GlobalNotificationSettingResource;
import de.tum.cit.aet.artemis.notification.web.PushNotificationResource;
import de.tum.cit.aet.artemis.notification.web.SystemNotificationResource;
import de.tum.cit.aet.artemis.notification.web.UserCourseNotificationSettingResource;
import de.tum.cit.aet.artemis.notification.web.UserCourseNotificationStatusResource;
import de.tum.cit.aet.artemis.notification.web.admin.AdminSystemNotificationResource;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleResourceArchitectureTest;

class NotificationResourceArchitectureTest extends AbstractModuleResourceArchitectureTest {

    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".notification";
    }

    // TODO: The notification REST resources still expose their endpoints under "api/communication/..."
    // for backwards compatibility with existing clients. Once the URLs are migrated to
    // "api/notification/..." these exemptions should be removed.
    @Override
    protected Set<Class<?>> getIgnoredModulePathPrefixResources() {
        return Set.of(CourseNotificationResource.class, GlobalNotificationSettingResource.class, PushNotificationResource.class, SystemNotificationResource.class,
                UserCourseNotificationSettingResource.class, UserCourseNotificationStatusResource.class, AdminSystemNotificationResource.class);
    }
}
