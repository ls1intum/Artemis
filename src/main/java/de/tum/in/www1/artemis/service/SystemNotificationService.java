package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.notification.SystemNotification;
import de.tum.in.www1.artemis.repository.SystemNotificationRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Service
public class SystemNotificationService {

    private static final String ENTITY_NAME = "systemNotification";

    private final SimpMessageSendingOperations messagingTemplate;

    private final SystemNotificationRepository systemNotificationRepository;

    public SystemNotificationService(SimpMessageSendingOperations messagingTemplate, SystemNotificationRepository systemNotificationRepository) {
        this.messagingTemplate = messagingTemplate;
        this.systemNotificationRepository = systemNotificationRepository;
    }

    /**
     * Finds all system notifications that have an expiry date in the future or no expiry date.
     * @return the list of notifications
     */
    public List<SystemNotification> findAllActiveAndFutureSystemNotifications() {
        // The 'user' does not need to be logged into Artemis, this leads to an issue when accessing custom repository methods. Therefore, a mock auth object has to be created.
        SecurityUtils.setAuthorizationObject();
        return systemNotificationRepository.findAllActiveAndFutureSystemNotifications(ZonedDateTime.now());
    }

    /**
     * Sends the current list of active and future system notifications to all connected clients.
     * Call this method after changing any system notification.
     */
    public void distributeActiveAndFutureNotificationsToClients() {
        messagingTemplate.convertAndSend("/topic/system-notification", findAllActiveAndFutureSystemNotifications());
    }

    /**
     * Validates the dates of a system notification and throws an exception if the dates are invalid.
     * @param systemNotification the system notification to validate
     */
    public void validateDatesElseThrow(SystemNotification systemNotification) {
        if (systemNotification.getNotificationDate() == null || systemNotification.getExpireDate() == null) {
            throw new BadRequestAlertException("System notification needs both a notification and expiration date.", ENTITY_NAME, "systemNotificationNeedsBothDates");
        }
        if (!systemNotification.getNotificationDate().isBefore(systemNotification.getExpireDate())) {
            throw new BadRequestAlertException("The notification date must be before the expiration date.", ENTITY_NAME, "systemNotificationNeedsNotificationBeforeExpiration");
        }
    }
}
