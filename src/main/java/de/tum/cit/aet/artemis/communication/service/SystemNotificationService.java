package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.notification.SystemNotification;
import de.tum.cit.aet.artemis.communication.repository.SystemNotificationRepository;
import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class SystemNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SystemNotificationService.class);

    private static final String ENTITY_NAME = "systemNotification";

    private final WebsocketMessagingService websocketMessagingService;

    private final SystemNotificationRepository systemNotificationRepository;

    private final UserRepository userRepository;

    private final MailSendingService mailSendingService;

    public SystemNotificationService(WebsocketMessagingService websocketMessagingService, SystemNotificationRepository systemNotificationRepository, UserRepository userRepository,
            MailSendingService mailSendingService) {
        this.websocketMessagingService = websocketMessagingService;
        this.systemNotificationRepository = systemNotificationRepository;
        this.userRepository = userRepository;
        this.mailSendingService = mailSendingService;
    }

    /**
     * Finds all system notifications that have an expiry date in the future or no expiry date.
     *
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
        websocketMessagingService.sendMessage("/topic/system-notification", findAllActiveAndFutureSystemNotifications());
    }

    /**
     * Validates the dates of a system notification and throws an exception if the dates are invalid.
     *
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

    /**
     * Returns the number of instructors who would receive a maintenance email for ongoing courses.
     *
     * @return the count of eligible recipients
     */
    public long countMaintenanceEmailRecipients() {
        return userRepository.countInstructorRecipientsForMaintenanceEmail(ZonedDateTime.now());
    }

    /**
     * Sends maintenance email notifications to all instructors of ongoing courses
     * who have not opted out of maintenance notifications.
     *
     * @param notification the system notification containing maintenance details
     */
    public void sendMaintenanceEmails(SystemNotification notification) {
        var recipients = userRepository.findInstructorRecipientsForMaintenanceEmail(ZonedDateTime.now());
        log.info("Sending maintenance emails to {} instructor(s)", recipients.size());

        var templateVars = new HashMap<String, Object>();
        templateVars.put("notificationTitle", notification.getTitle());
        templateVars.put("notificationDate", notification.getNotificationDate());
        templateVars.put("expireDate", notification.getExpireDate());
        if (notification.getText() != null) {
            templateVars.put("notificationText", notification.getText());
        }

        for (var recipient : recipients) {
            try {
                var user = new User();
                user.setId(recipient.id());
                user.setEmail(recipient.email());
                user.setLangKey(recipient.langKey());
                user.setFirstName(recipient.firstName());
                user.setLastName(recipient.lastName());

                mailSendingService.buildAndSendAsync(user, "email.notification.maintenance.title", "mail/notification/maintenanceEmail", templateVars);
            }
            catch (Exception e) {
                log.error("Failed to queue maintenance email for user {}: {}", recipient.id(), e.getMessage());
            }
        }
    }
}
