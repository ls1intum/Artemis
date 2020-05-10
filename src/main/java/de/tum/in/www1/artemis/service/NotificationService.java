package de.tum.in.www1.artemis.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.notification.GroupNotification;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.repository.GroupNotificationRepository;
import de.tum.in.www1.artemis.repository.NotificationRepository;

@Service
public class NotificationService {

    private NotificationRepository notificationRepository;

    private GroupNotificationRepository groupNotificationRepository;

    public NotificationService(NotificationRepository notificationRepository, GroupNotificationRepository groupNotificationRepository) {
        this.notificationRepository = notificationRepository;
        this.groupNotificationRepository = groupNotificationRepository;
    }

    public Page<Notification> findAllExceptSystem(User currentUser, Pageable pageable) {
        return notificationRepository.findAllNotificationsForRecipientWithLogin(currentUser.getGroups(), pageable, currentUser.getLogin());
    }

    public List<Notification> findAllRecentExceptSystem(User currentUser) {
        return notificationRepository.findAllRecentNotificationsForRecipientWithLogin(currentUser.getGroups(), currentUser.getLogin(), currentUser.getLastNotificationRead());
    }

    public List<GroupNotification> findAllNotificationsForCourse(Course course) {
        return groupNotificationRepository.findAllByCourseId(course.getId());
    }

    public void deleteNotification(GroupNotification notification) {
        notificationRepository.delete(notification);
    }
}
