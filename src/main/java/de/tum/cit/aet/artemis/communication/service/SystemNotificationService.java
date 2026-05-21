package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.notification.SystemNotification;
import de.tum.cit.aet.artemis.communication.repository.MaintenanceEmailRecipientRepository;
import de.tum.cit.aet.artemis.communication.repository.SystemNotificationRepository;
import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class SystemNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SystemNotificationService.class);

    private static final String ENTITY_NAME = "systemNotification";

    private final WebsocketMessagingService websocketMessagingService;

    private final SystemNotificationRepository systemNotificationRepository;

    private final MaintenanceEmailRecipientRepository maintenanceEmailRecipientRepository;

    private final MailSendingService mailSendingService;

    public SystemNotificationService(WebsocketMessagingService websocketMessagingService, SystemNotificationRepository systemNotificationRepository,
            MaintenanceEmailRecipientRepository maintenanceEmailRecipientRepository, MailSendingService mailSendingService) {
        this.websocketMessagingService = websocketMessagingService;
        this.systemNotificationRepository = systemNotificationRepository;
        this.maintenanceEmailRecipientRepository = maintenanceEmailRecipientRepository;
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
        return maintenanceEmailRecipientRepository.countInstructorRecipientsForMaintenanceEmail(ZonedDateTime.now());
    }

    /**
     * Sends maintenance email notifications to all instructors of ongoing courses
     * who have not opted out of maintenance notifications.
     *
     * @param notification the system notification containing maintenance details
     */
    public void sendMaintenanceEmails(SystemNotification notification) {
        validateDatesElseThrow(notification);

        var recipients = maintenanceEmailRecipientRepository.findInstructorRecipientsForMaintenanceEmail(ZonedDateTime.now());
        log.info("Sending maintenance emails to {} instructor(s)", recipients.size());

        // Convert dates to server-local timezone so recipients see times relevant to the deployment location
        ZoneId serverZone = ZoneId.systemDefault();
        ZonedDateTime localStart = notification.getNotificationDate().withZoneSameInstant(serverZone);
        ZonedDateTime localEnd = notification.getExpireDate().withZoneSameInstant(serverZone);

        // Cache formatted dates per locale to avoid redundant DateTimeFormatter creation
        var formattedDatesByLocale = new HashMap<String, String[]>();

        for (var recipient : recipients) {
            try {
                String langKey = (recipient.langKey() != null && !recipient.langKey().isBlank()) ? recipient.langKey().strip() : "en";

                var user = new User();
                user.setId(recipient.id());
                user.setEmail(recipient.email());
                user.setLangKey(langKey);
                user.setFirstName(recipient.firstName());
                user.setLastName(recipient.lastName());

                String[] formattedDates = formattedDatesByLocale.computeIfAbsent(langKey, lk -> {
                    Locale locale = Locale.forLanguageTag(lk);
                    DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale);
                    DateTimeFormatter zoneFormatter = DateTimeFormatter.ofPattern("z").withLocale(locale);
                    return new String[] { localStart.format(formatter) + " " + localStart.format(zoneFormatter),
                            localEnd.format(formatter) + " " + localEnd.format(zoneFormatter) };
                });

                var mutableVars = new HashMap<String, Object>();
                if (notification.getTitle() != null) {
                    mutableVars.put("notificationTitle", notification.getTitle());
                }
                if (notification.getText() != null) {
                    mutableVars.put("notificationText", notification.getText());
                }
                mutableVars.put("formattedStart", formattedDates[0]);
                mutableVars.put("formattedEnd", formattedDates[1]);

                mailSendingService.buildAndSendAsync(user, "email.notification.maintenance.title", "mail/notification/maintenanceEmail", Map.copyOf(mutableVars));
            }
            catch (Exception e) {
                log.error("Failed to queue maintenance email for user {}: {}", recipient.id(), e.getMessage());
            }
        }
    }
}
