package de.tum.cit.aet.artemis.iris.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.InternetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.communication.service.notifications.MailService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.iris.config.IrisDashboardProperties;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardAlertDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardDigestDTO;

/**
 * Service responsible for sending Iris dashboard digest and alert emails to configured admin recipients.
 */
@Service
@Lazy
@Profile(PROFILE_CORE)
@Conditional(IrisEnabled.class)
public class IrisDashboardEmailService {

    private static final Logger log = LoggerFactory.getLogger(IrisDashboardEmailService.class);

    private final MailService mailService;

    private final MailSendingService mailSendingService;

    private final IrisDashboardProperties properties;

    private List<String> resolvedDigestRecipients = List.of();

    private List<String> resolvedAlertRecipients = List.of();

    private final AtomicBoolean digestWarnLogged = new AtomicBoolean(false);

    private final AtomicBoolean alertWarnLogged = new AtomicBoolean(false);

    /**
     * Creates a new IrisDashboardEmailService with the given dependencies.
     *
     * @param mailService        the mail service used to send emails
     * @param mailSendingService the mail sending service used to check mail configuration
     * @param properties         the Iris dashboard properties containing recipient configuration
     */
    public IrisDashboardEmailService(MailService mailService, MailSendingService mailSendingService, IrisDashboardProperties properties) {
        this.mailService = mailService;
        this.mailSendingService = mailSendingService;
        this.properties = properties;
    }

    /**
     * Initializes the service by validating and deduplicating configured email recipients.
     */
    @PostConstruct
    public void init() {
        resolvedDigestRecipients = validateAndDedup(properties.getDigest().getRecipients());
        resolvedAlertRecipients = validateAndDedup(properties.getAlert().getRecipients());
    }

    /**
     * Returns whether digest emails can currently be sent (mail configured and at least one valid recipient).
     *
     * @return true if digest sending is possible, false otherwise
     */
    public boolean canSendDigest() {
        if (!mailSendingService.isMailConfigured()) {
            if (digestWarnLogged.compareAndSet(false, true)) {
                log.warn("Iris digest: mail not configured");
            }
            return false;
        }
        if (resolvedDigestRecipients.isEmpty()) {
            if (digestWarnLogged.compareAndSet(false, true)) {
                log.warn("Iris digest: no valid recipients configured");
            }
            return false;
        }
        return true;
    }

    /**
     * Returns whether alert emails can currently be sent (mail configured and at least one valid recipient, falling back to digest recipients).
     *
     * @return true if alert sending is possible, false otherwise
     */
    public boolean canSendAlert() {
        if (!mailSendingService.isMailConfigured()) {
            if (alertWarnLogged.compareAndSet(false, true)) {
                log.warn("Iris alert: mail not configured");
            }
            return false;
        }
        var effective = effectiveAlertRecipients();
        if (effective.isEmpty()) {
            if (alertWarnLogged.compareAndSet(false, true)) {
                log.warn("Iris alert: no valid recipients configured (including digest fallback)");
            }
            return false;
        }
        return true;
    }

    /**
     * Sends the given digest to all resolved digest recipients and returns the number of emails queued for async delivery.
     *
     * @param digest the digest data to send
     * @return the number of emails queued for async delivery
     */
    public int sendDigest(IrisDashboardDigestDTO digest) {
        int sent = 0;
        for (String email : resolvedDigestRecipients) {
            try {
                mailService.sendIrisDashboardDigestEmail(createSyntheticUser(email), digest);
                sent++;
            }
            catch (Exception e) {
                log.error("Failed to send Iris digest to {}", email, e);
            }
        }
        return sent;
    }

    /**
     * Sends the given alert to all resolved alert recipients (falling back to digest recipients) and returns the number of emails queued for async delivery.
     *
     * @param alert the alert data to send
     * @return the number of emails queued for async delivery
     */
    public int sendAlert(IrisDashboardAlertDTO alert) {
        int sent = 0;
        for (String email : effectiveAlertRecipients()) {
            try {
                mailService.sendIrisDashboardAlertEmail(createSyntheticUser(email), alert);
                sent++;
            }
            catch (Exception e) {
                log.error("Failed to send Iris alert to {}", email, e);
            }
        }
        return sent;
    }

    private List<String> effectiveAlertRecipients() {
        return resolvedAlertRecipients.isEmpty() ? resolvedDigestRecipients : resolvedAlertRecipients;
    }

    private List<String> validateAndDedup(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        var seen = new LinkedHashMap<String, String>();
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                var parsed = new InternetAddress(trimmed, true);
                if (parsed.getPersonal() != null || !trimmed.equals(parsed.getAddress())) {
                    log.warn("Iris email recipient rejected (display-name form): {}", trimmed);
                    continue;
                }
                seen.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
            }
            catch (Exception e) {
                log.warn("Iris email recipient rejected (invalid address): {}", trimmed);
            }
        }
        return new ArrayList<>(seen.values());
    }

    private User createSyntheticUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setLangKey("en");
        user.setLogin("iris-dashboard-recipient");
        user.setFirstName("Administrator");
        return user;
    }
}
