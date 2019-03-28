package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Notification;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.GroupNotificationRepository;
import de.tum.in.www1.artemis.repository.SingleUserNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class GroupNotificationService {
    private GroupNotificationRepository groupNotificationRepository;

    public GroupNotificationService(GroupNotificationRepository groupNotificationRepository) {
        this.groupNotificationRepository = groupNotificationRepository;
    }

    public List<Notification> findAllNewNotificationsForCurrentUser(User currentUser) {
        return this.groupNotificationRepository.findAllNewNotificationsForCurrentUser(currentUser.getGroups(), currentUser.getLastNotificationRead());
    }

}
