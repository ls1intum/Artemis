package de.tum.cit.aet.artemis.course_notification.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.course_notification.domain.NotificationSettingOption;
import de.tum.cit.aet.artemis.course_notification.domain.notifications.CourseNotification;
import de.tum.cit.aet.artemis.course_notification.dto.CourseNotificationDTO;

/**
 * Service that handles all course notification logic. Whenever you want to create a new notification use this service
 * to send it to the users.
 */
@Profile(PROFILE_CORE)
@Service
public class CourseNotificationService {

    private final CourseNotificationSettingService courseNotificationSettingService;

    private final Map<NotificationSettingOption, CourseNotificationBroadcastService> serviceMap;

    public CourseNotificationService(CourseNotificationSettingService courseNotificationSettingService, CourseNotificationWebappService webappService,
            CourseNotificationPushService pushService, CourseNotificationEmailService emailService) {
        this.courseNotificationSettingService = courseNotificationSettingService;
        this.serviceMap = Map.of(NotificationSettingOption.WEBAPP, webappService, NotificationSettingOption.PUSH, pushService, NotificationSettingOption.EMAIL, emailService);
    }

    /**
     * Sends a notification on all channels the notification supports (websocket, push, email, ...) to the list of
     * recipients if they have the notification type enabled.
     *
     * @param courseNotification to send.
     * @param recipients         list of recipients. Will be filtered by user settings.
     */
    public void sendCourseNotification(CourseNotification courseNotification, List<User> recipients) {
        var supportedChannels = courseNotification.getSupportedChannels();

        for (var supportedChannel : supportedChannels) {
            var service = serviceMap.get(supportedChannel);
            if (service == null) {
                continue;
            }
            var filteredRecipients = courseNotificationSettingService.filterRecipientsBy(courseNotification, recipients, supportedChannel);
            service.sendCourseNotification(convertToCourseNotificationDTO(courseNotification), filteredRecipients);
        }
    }

    /**
     * Initializes a new {@link CourseNotificationDTO} and returns it. This can be sent to clients.
     *
     * @param notification to be made into a record
     *
     * @return Returns the notification as a DTO.
     */
    public CourseNotificationDTO convertToCourseNotificationDTO(CourseNotification notification) {
        return new CourseNotificationDTO(notification.getReadableNotificationType(), notification.courseId, notification.creationDate, notification.getCourseNotificationCategory(),
                notification.getParameters());
    }
}
