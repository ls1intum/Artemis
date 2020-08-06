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

    private final NotificationRepository notificationRepository;

    private final GroupNotificationRepository groupNotificationRepository;

    public NotificationService(NotificationRepository notificationRepository, GroupNotificationRepository groupNotificationRepository) {
        this.notificationRepository = notificationRepository;
        this.groupNotificationRepository = groupNotificationRepository;
    }

    /**
     * Get all notifications for current user by pages.
     * @param currentUser the current user with the groups he belongs to
     * @param pageable Pagination information for fetching the notifications
     * @return notification Page
     */
    public Page<Notification> findAllExceptSystem(User currentUser, Pageable pageable) {
        return notificationRepository.findAllNotificationsForRecipientWithLogin(currentUser.getGroups(), currentUser.getLogin(), pageable);
    }

    /**
     * Get all group notifications for a specific course.
     * @param course the course for which notifications should be retrieved
     * @return list of notifications
     */
    public List<GroupNotification> findAllGroupNotificationsForCourse(Course course) {
        return groupNotificationRepository.findAllByCourseId(course.getId());
    }

    /**
     * Delete the specified group notification.
     * @param notification group notification that should be deleted
     */
    public void deleteGroupNotification(GroupNotification notification) {
        notificationRepository.delete(notification);
    }
}
