package de.tum.cit.aet.artemis.communication.notification.util;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.communication.domain.GroupNotificationType;
import de.tum.cit.aet.artemis.communication.domain.notification.GroupNotification;
import de.tum.cit.aet.artemis.communication.domain.notification.SingleUserNotification;
import de.tum.cit.aet.artemis.communication.domain.notification.SystemNotification;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * Factory for creating Notifications and related objects.
 */
public class NotificationFactory {

    /**
     * Generates a SingleUserNotification with the given arguments.
     *
     * @param notificationDate The notification date of the SingleUserNotification
     * @param recipient        The recipient of the SingleUserNotification
     * @return The generated SingleUserNotification
     */
    public static SingleUserNotification generateSingleUserNotification(ZonedDateTime notificationDate, User recipient) {
        SingleUserNotification singleUserNotification = new SingleUserNotification();
        singleUserNotification.setNotificationDate(notificationDate);
        singleUserNotification.setRecipient(recipient);
        return singleUserNotification;
    }

    /**
     * Generates a GroupNotification with the given arguments.
     *
     * @param notificationDate The notification date of the GroupNotification
     * @param course           The Course of the GroupNotification's recipients
     * @param type             The type of the GroupNotification
     * @return The generated GroupNotification
     */
    public static GroupNotification generateGroupNotification(ZonedDateTime notificationDate, Course course, GroupNotificationType type) {
        GroupNotification groupNotification = new GroupNotification();
        groupNotification.setNotificationDate(notificationDate);
        groupNotification.setCourse(course);
        groupNotification.setType(type);
        return groupNotification;
    }

    /**
     * Generates a SystemNotification with the given arguments.
     *
     * @param notificationDate The notification date of the SystemNotification
     * @param expiryDate       The expiry date of the SystemNotification
     * @return The generated SystemNotification
     */
    public static SystemNotification generateSystemNotification(ZonedDateTime notificationDate, ZonedDateTime expiryDate) {
        SystemNotification systemNotification = new SystemNotification();
        systemNotification.setNotificationDate(notificationDate);
        systemNotification.setExpireDate(expiryDate);
        return systemNotification;
    }
}
