package de.tum.cit.aet.artemis.admin.service;

import static de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants.ACCOUNT_SECURITY_EVENT_TYPES;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.admin.repository.PersistenceAuditEventRepository;

/**
 * Prunes {@code jhi_persistent_audit_event} on two separate retention schedules.
 * <p>
 * Until this service existed, nothing pruned the audit table at all. The
 * {@code artemis.audit-events.retention-period} property reads as though it covered audit events - the production
 * configuration comments it as "Number of days before audit events and VCS access logs are deleted" - but its only
 * consumer is {@code AutomaticVcsAccessLogCleanupService}, which applies it to {@code vcs_access_log}. So audit events
 * accumulated indefinitely.
 * <p>
 * <b>Why two retention periods rather than one.</b> The table mixes two kinds of record with opposite characteristics.
 * Spring Boot's {@code AuthenticationAuditListener} writes one row for every single login, which is what makes the table
 * large and individually tells you very little a few weeks later. The account lifecycle events
 * ({@link de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants#ACCOUNT_SECURITY_EVENT_TYPES}) are rare, and they
 * are the ones needed to answer how an account reached its current state - a question typically asked long after the
 * fact. A single retention period forces a choice between keeping the bulk records too long and discarding the
 * interesting ones too early, so the two are pruned independently.
 * <p>
 * Deletion goes through the entities rather than a bulk {@code DELETE}, because {@code jhi_persistent_audit_evt_data} is
 * an {@code @ElementCollection} whose foreign key is {@code ON DELETE RESTRICT}: a bulk delete of the parent rows would
 * be rejected by the database. That makes deletion comparatively expensive, which is why it is batched.
 */
@Lazy
@Service
@Profile("scheduling & core")
public class AutomaticAuditEventCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AutomaticAuditEventCleanupService.class);

    /**
     * Rows removed per batch. Bounded because the first run has to clear a backlog that may span years, and one
     * transaction over millions of rows would hold locks far too long.
     */
    private static final int BATCH_SIZE = 5_000;

    /** Batches per run, so a huge backlog is drained over several nights instead of in one very long transaction. */
    private static final int MAX_BATCHES_PER_RUN = 20;

    private final PersistenceAuditEventRepository persistenceAuditEventRepository;

    /**
     * Retention for account lifecycle events: credential and identity changes. Long by default, because these are the
     * records needed to explain how an account reached its current state, which is often asked long after the fact.
     */
    @Value("${artemis.audit-events.account-security-retention-period:730}")
    private int accountSecurityRetentionPeriodInDays;

    /**
     * Retention for the high-volume authentication events written on every login. Short by default: after a few weeks an
     * individual successful login is rarely of interest, and this is the bulk of the table.
     */
    @Value("${artemis.audit-events.authentication-retention-period:90}")
    private int authenticationRetentionPeriodInDays;

    public AutomaticAuditEventCleanupService(PersistenceAuditEventRepository persistenceAuditEventRepository) {
        this.persistenceAuditEventRepository = persistenceAuditEventRepository;
    }

    /**
     * Deletes expired audit events, applying the short retention to authentication events and the long retention to
     * account-security events.
     */
    // execute this every night at 3:10:00 am, offset from the other nightly cleanups so they do not contend
    @Scheduled(cron = "0 10 3 * * *")
    public void cleanup() {
        Instant authenticationCutoff = Instant.now().minus(authenticationRetentionPeriodInDays, ChronoUnit.DAYS);
        Instant accountSecurityCutoff = Instant.now().minus(accountSecurityRetentionPeriodInDays, ChronoUnit.DAYS);

        int deletedAuthenticationEvents = deleteInBatches("authentication and other",
                pageable -> persistenceAuditEventRepository.findExpiredIdsExcludingTypes(authenticationCutoff, ACCOUNT_SECURITY_EVENT_TYPES, pageable));
        int deletedAccountSecurityEvents = deleteInBatches("account-security",
                pageable -> persistenceAuditEventRepository.findExpiredIdsOfTypes(accountSecurityCutoff, ACCOUNT_SECURITY_EVENT_TYPES, pageable));

        if (deletedAuthenticationEvents > 0 || deletedAccountSecurityEvents > 0) {
            log.info("Scheduled deletion of expired audit events: removed {} authentication/other events (older than {} days) and {} account-security events (older than {} days)",
                    deletedAuthenticationEvents, authenticationRetentionPeriodInDays, deletedAccountSecurityEvents, accountSecurityRetentionPeriodInDays);
        }
    }

    /**
     * Repeatedly fetches and deletes a batch of ids until nothing expired is left or the per-run batch cap is reached.
     *
     * @param description human-readable label of what is being pruned, for logging
     * @param idFinder    supplies the next batch of expired ids
     * @return how many events were deleted
     */
    private int deleteInBatches(String description, ExpiredIdFinder idFinder) {
        int totalDeleted = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            List<Long> expiredIds = idFinder.find(PageRequest.of(0, BATCH_SIZE));
            if (expiredIds.isEmpty()) {
                return totalDeleted;
            }
            persistenceAuditEventRepository.deleteAllById(expiredIds);
            totalDeleted += expiredIds.size();
        }
        log.info("Reached the per-run batch limit while pruning {} audit events after {} rows; the remainder is pruned on the next run", description, totalDeleted);
        return totalDeleted;
    }

    @FunctionalInterface
    private interface ExpiredIdFinder {

        List<Long> find(PageRequest pageable);
    }

    /**
     * @return the audit event types treated as account-security events, exposed for tests and diagnostics
     */
    public static Set<String> accountSecurityEventTypes() {
        return ACCOUNT_SECURITY_EVENT_TYPES;
    }
}
