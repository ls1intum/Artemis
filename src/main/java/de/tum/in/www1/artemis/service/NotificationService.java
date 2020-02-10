package de.tum.in.www1.artemis.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.GroupNotificationRepository;
import de.tum.in.www1.artemis.repository.NotificationRepository;

@Service
public class NotificationService {

    private NotificationRepository notificationRepository;

    private GroupNotificationRepository groupNotificationRepository;

    private SingleUserNotificationService singleUserNotificationService;

    private GroupNotificationService groupNotificationService;

    public NotificationService(NotificationRepository notificationRepository, GroupNotificationRepository groupNotificationRepository,
            SingleUserNotificationService singleUserNotificationService, GroupNotificationService groupNotificationService) {
        this.notificationRepository = notificationRepository;
        this.groupNotificationRepository = groupNotificationRepository;
        this.singleUserNotificationService = singleUserNotificationService;
        this.groupNotificationService = groupNotificationService;
    }

    public Page<Notification> findAllExceptSystem(User currentUser, Pageable pageable) {
        return notificationRepository.findAllNotificationsForRecipientWithLogin(currentUser.getGroups(), pageable, currentUser.getLogin());
    }

    public List<Notification> findAllRecentExceptSystem(User currentUser) {
        List<Notification> groupNotifications = groupNotificationService.findAllRecentNewNotificationsForCurrentUser(currentUser);
        List<Notification> userNotifications = singleUserNotificationService.findAllRecentNewNotificationsForRecipientWithLogin(currentUser.getLogin());
        groupNotifications.addAll(userNotifications);
        return groupNotifications;
    }

    public List<GroupNotification> findAllNotificationsForCourse(Course course) {
        return groupNotificationRepository.findAllByCourseId(course.getId());
    }

    public void deleteNotification(GroupNotification notification) {
        notificationRepository.delete(notification);
    }
}
