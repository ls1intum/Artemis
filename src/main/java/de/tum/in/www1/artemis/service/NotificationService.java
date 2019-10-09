package de.tum.in.www1.artemis.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.GroupNotificationRepository;
import de.tum.in.www1.artemis.repository.NotificationRepository;

@Service
@Transactional
public class NotificationService {

    private final SimpMessageSendingOperations messagingTemplate;

    private NotificationRepository notificationRepository;

    private GroupNotificationRepository groupNotificationRepository;

    private SingleUserNotificationService singleUserNotificationService;

    private GroupNotificationService groupNotificationService;

    public NotificationService(SimpMessageSendingOperations messagingTemplate, NotificationRepository notificationRepository,
            GroupNotificationRepository groupNotificationRepository, SingleUserNotificationService singleUserNotificationService,
            GroupNotificationService groupNotificationService) {
        this.messagingTemplate = messagingTemplate;
        this.notificationRepository = notificationRepository;
        this.groupNotificationRepository = groupNotificationRepository;
        this.singleUserNotificationService = singleUserNotificationService;
        this.groupNotificationService = groupNotificationService;
    }

    @Transactional(readOnly = true)
    public Page<Notification> findAllExceptSystem(User currentUser, Pageable pageable) {
        return notificationRepository.findAllNotificationsForCurrentUser(currentUser.getGroups(), pageable);
    }

    @Transactional(readOnly = true)
    public List<Notification> findAllRecentExceptSystem(User currentUser) {
        List<Notification> groupNotifications = groupNotificationService.findAllRecentNewNotificationsForCurrentUser(currentUser);
        List<Notification> userNotifications = singleUserNotificationService.findAllRecentNewNotificationsForCurrentUser();
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
