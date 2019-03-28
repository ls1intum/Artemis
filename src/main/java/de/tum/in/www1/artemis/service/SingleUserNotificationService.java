package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Notification;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.SingleUserNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class SingleUserNotificationService {
    private SingleUserNotificationRepository singleUserNotificationRepository;

    public SingleUserNotificationService(SingleUserNotificationRepository singleUserNotificationRepository) {
        this.singleUserNotificationRepository = singleUserNotificationRepository;
    }

    public List<Notification> findAllNewNotificationsForCurrentUser() {
        return this.singleUserNotificationRepository.findAllNewNotificationsForCurrentUser();
    }

}
