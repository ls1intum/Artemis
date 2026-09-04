package de.tum.cit.aet.artemis.admin.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.admin.dto.FeatureUsageDigestDTO;
import de.tum.cit.aet.artemis.core.config.FeatureUsageProperties;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.notifications.MailService;

/**
 * Sends the weekly feature usage summary to the administrators.
 * <p>
 * Modelled on the weekly vulnerability report: a cron job on the scheduling node only, skipped in development and on test
 * servers, and only actually sent when an administrator address is configured. The point of the mail is not to replace the
 * admin page but to make someone open it, so it carries a per-module roll-up and a link.
 * <p>
 * Single execution across the cluster comes from the scheduling profile, which is how every other cron job in Artemis
 * avoids duplicates. The mail can also be triggered manually from any node, which is how an administrator checks that
 * delivery works without waiting a week.
 */
@Lazy
@Service
@Profile(PROFILE_CORE)
public class FeatureUsageDigestScheduleService {

    private static final Logger log = LoggerFactory.getLogger(FeatureUsageDigestScheduleService.class);

    /** Emails are only written in English elsewhere in the admin flows, and administrators are a known small audience. */
    private static final String RECIPIENT_LANGUAGE = "en";

    @Value("${info.contact:}")
    private String adminEmail;

    @Value("${info.testServer:false}")
    private boolean isTestServer;

    private final FeatureUsageDigestService featureUsageDigestService;

    private final FeatureUsageProperties properties;

    private final MailService mailService;

    private final ProfileService profileService;

    /**
     * The weekly send is handed to this rather than performed on the scheduler thread.
     * <p>
     * The digest sends synchronously so the manual trigger on the admin page can report whether the mail actually went
     * out. Every {@code @Scheduled} job in Artemis shares a pool of a few threads, so doing the same on the weekly path
     * would let a slow or hung mail server hold one of them for the duration of the send and delay unrelated jobs.
     */
    private final Executor mailTaskExecutor;

    public FeatureUsageDigestScheduleService(FeatureUsageDigestService featureUsageDigestService, FeatureUsageProperties properties, MailService mailService,
            ProfileService profileService, @Qualifier("mailTaskExecutor") Executor mailTaskExecutor) {
        this.featureUsageDigestService = featureUsageDigestService;
        this.properties = properties;
        this.mailService = mailService;
        this.profileService = profileService;
        this.mailTaskExecutor = mailTaskExecutor;
    }

    /**
     * Sends the weekly digest, by default every Monday at 9:00, an hour after the vulnerability report so the two do not
     * arrive at once. Configurable through {@code artemis.scheduling.feature-usage-digest-time}.
     * <p>
     * Skipped on nodes without the scheduling profile (otherwise every node would send its own copy), in development, and
     * on test servers.
     */
    @Scheduled(cron = "${artemis.scheduling.feature-usage-digest-time:0 0 9 * * MON}")
    public void sendWeeklyDigest() {
        if (!profileService.isSchedulingActive()) {
            return;
        }
        if (profileService.isDevActive()) {
            // NOTE: if you want to test this locally, trigger it through the admin page instead of changing this
            return;
        }
        if (isTestServer) {
            log.debug("Skipping the feature usage digest email on a test server");
            return;
        }
        // Not sent on the scheduler thread; see the field.
        mailTaskExecutor.execute(this::sendDigestEmail);
    }

    /**
     * Builds and sends the digest to every configured recipient. Also used by the manual trigger on the admin page.
     *
     * @return true if the mail was sent to at least one recipient, false if it is switched off, has no recipients, or
     *         building or sending failed
     */
    public boolean sendDigestEmail() {
        if (!properties.enabled()) {
            log.info("Skipping the feature usage digest email: usage tracking is disabled, so there would be nothing to report");
            return false;
        }
        if (!properties.digest().enabled()) {
            log.info("Skipping the feature usage digest email: the digest is disabled");
            return false;
        }
        List<String> recipients = resolveRecipients();
        if (recipients.isEmpty()) {
            log.warn("Cannot send the feature usage digest email: neither artemis.feature-usage.digest.recipients nor info.contact is configured");
            return false;
        }

        try {
            FeatureUsageDigestDTO digest = featureUsageDigestService.buildWeeklyDigest();
            boolean everyRecipientReached = true;
            for (String recipient : recipients) {
                boolean sent = mailService.sendFeatureUsageDigestEmail(
                        new MailRecipientDTO(recipient, RECIPIENT_LANGUAGE, "feature-usage-digest-recipient", "Administrator", null, null, null), digest);
                if (!sent) {
                    log.warn("The feature usage digest could not be sent to {}", recipient);
                    everyRecipientReached = false;
                }
            }
            if (!everyRecipientReached) {
                // The manual trigger exists so an administrator can find out whether the weekly mail will arrive.
                // Reporting success when a send did not reach the transport answers the opposite of that question.
                return false;
            }
            log.info("Feature usage digest for {} to {} sent to {} recipients: {} calls across {} active modules, {} features offered but unused", digest.from(), digest.to(),
                    recipients.size(), digest.totalCalls(), digest.activeModules().size(), digest.unusedFeatures());
            return true;
        }
        catch (Exception e) {
            log.error("Failed to build or send the feature usage digest email", e);
            return false;
        }
    }

    /**
     * Explicit recipients if configured, otherwise the general administrator contact, which is where the other
     * administrative mails already go.
     */
    private List<String> resolveRecipients() {
        List<String> configured = properties.digest().recipients().stream().filter(StringUtils::hasText).map(String::trim).toList();
        if (!configured.isEmpty()) {
            return configured;
        }
        return StringUtils.hasText(adminEmail) ? List.of(adminEmail.trim()) : List.of();
    }
}
