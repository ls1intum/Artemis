package de.tum.cit.aet.artemis.admin.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;
import static de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants.GENERAL_EVENT_TYPES;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.admin.config.AuditEventRetentionProperties;
import de.tum.cit.aet.artemis.admin.repository.PersistenceAuditEventRepository;

/**
 * Prunes {@code jhi_persistent_audit_event} nightly, on two retention schedules.
 * <p>
 * Nothing pruned this table before. The {@code jhipster.audit-events.retention-period} property reads as though it
 * covered audit events - the production configuration even comments it as "Number of days before audit events and VCS
 * access logs are deleted" - but no bean ever read it: the only similarly named property is
 * {@code artemis.audit-events.retention-period} in {@code AutomaticVcsAccessLogCleanupService}, which applies to
 * {@code vcs_access_log} and lives in a different namespace. So audit events accumulated indefinitely.
 * <p>
 * <b>Why two retention periods rather than one.</b> The table mixes two kinds of record with opposite characteristics.
 * Spring Boot writes one row for every login attempt, which is what makes the table large and which tells you very little
 * individually a few weeks later. The other rows are deliberate actions - deleting an exercise, resetting an exam,
 * changing an account's credentials - which are rare and are exactly what has to be reconstructed when a question comes
 * up long after the fact, for instance an exam dispute. One retention period would force a choice between keeping the
 * bulk far too long and discarding the interesting records far too early.
 * <p>
 * Everything that is not an authentication event gets the long retention, including event types added later. That is the
 * safe direction: a new type is over-retained rather than silently dropped on the short schedule.
 * <p>
 * The two periods are bound through {@link AuditEventRetentionProperties}, which validates them as positive, so a
 * configuration typo fails startup instead of deleting recent records.
 * <p>
 * Deletion goes through the entities rather than a bulk {@code DELETE}, because {@code jhi_persistent_audit_evt_data} is
 * an {@code @ElementCollection} whose foreign key is {@code ON DELETE RESTRICT}, so a bulk delete of the parent rows
 * would be rejected by the database. That makes deletion comparatively expensive, which is why it is batched and capped.
 */
@Lazy
@Service
@Profile(PROFILE_CORE_AND_SCHEDULING)
public class AutomaticAuditEventCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AutomaticAuditEventCleanupService.class);

    /**
     * Rows removed per batch. Bounded because the first runs have to clear a backlog that may span years, and one
     * transaction over millions of rows would hold locks far too long.
     */
    private static final int BATCH_SIZE = 5_000;

    /**
     * Batches per run and per schedule, so a large backlog is drained over several nights rather than in one very long
     * job. At the default batch size this removes up to a million rows per schedule per night.
     */
    private static final int MAX_BATCHES_PER_RUN = 200;

    private final PersistenceAuditEventRepository persistenceAuditEventRepository;

    private final AuditEventRetentionProperties retentionProperties;

    public AutomaticAuditEventCleanupService(PersistenceAuditEventRepository persistenceAuditEventRepository, AuditEventRetentionProperties retentionProperties) {
        this.persistenceAuditEventRepository = persistenceAuditEventRepository;
        this.retentionProperties = retentionProperties;
    }

    /**
     * Deletes expired audit events, applying the short retention to the login record and the long one to everything else.
     */
    // execute this every night at 3:10:00 am, offset from the other nightly cleanups so they do not contend
    @Scheduled(cron = "0 10 3 * * *")
    public void cleanup() {
        int generalRetentionInDays = retentionProperties.generalRetentionPeriod();
        int applicationRetentionInDays = retentionProperties.applicationRetentionPeriod();
        Instant generalCutoff = Instant.now().minus(generalRetentionInDays, ChronoUnit.DAYS);
        Instant applicationCutoff = Instant.now().minus(applicationRetentionInDays, ChronoUnit.DAYS);

        int deletedGeneralEvents = pruneIsolated("general", generalRetentionInDays,
                pageable -> persistenceAuditEventRepository.findExpiredIdsOfTypes(generalCutoff, GENERAL_EVENT_TYPES, pageable));
        int deletedApplicationEvents = pruneIsolated("application", applicationRetentionInDays,
                pageable -> persistenceAuditEventRepository.findExpiredIdsExcludingTypes(applicationCutoff, GENERAL_EVENT_TYPES, pageable));

        if (deletedGeneralEvents > 0 || deletedApplicationEvents > 0) {
            log.info("Scheduled deletion of expired audit events: removed {} general/authentication events (older than {} days) and {} application events (older than {} days)",
                    deletedGeneralEvents, generalRetentionInDays, deletedApplicationEvents, applicationRetentionInDays);
        }
    }

    /**
     * Runs one retention schedule, keeping its failures to itself.
     * <p>
     * The two schedules are independent, and they share one nightly trigger. Without this boundary, a persistent problem
     * with one of them - a lock timeout on a particular old row, say - would also stop the other from ever running, so a
     * fault in pruning the login record could let the rest of the log grow unbounded indefinitely.
     *
     * @param logName         what is being pruned, for logging
     * @param retentionInDays the retention period being applied, for logging
     * @param expiredIdFinder supplies the next batch of expired ids
     * @return how many events were deleted, or 0 if this schedule failed
     */
    private int pruneIsolated(String logName, int retentionInDays, ExpiredIdFinder expiredIdFinder) {
        try {
            return prune(logName, retentionInDays, expiredIdFinder);
        }
        catch (Exception e) {
            log.error("Failed to prune {} audit events (retention {} days); the other retention schedule still runs", logName, retentionInDays, e);
            return 0;
        }
    }

    /**
     * Repeatedly fetches and deletes a batch of expired ids until nothing expired is left or the per-run cap is reached.
     *
     * @param logName         what is being pruned, for logging
     * @param retentionInDays the retention period being applied, for logging
     * @param expiredIdFinder supplies the next batch of expired ids
     * @return how many events were deleted
     */
    private int prune(String logName, int retentionInDays, ExpiredIdFinder expiredIdFinder) {
        int totalDeleted = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            List<Long> expiredIds = expiredIdFinder.find(PageRequest.of(0, BATCH_SIZE));
            if (expiredIds.isEmpty()) {
                return totalDeleted;
            }
            persistenceAuditEventRepository.deleteAllById(expiredIds);
            totalDeleted += expiredIds.size();
        }
        log.info("Reached the per-run batch limit while pruning {} audit events (retention {} days) after {} rows; the remainder is pruned on the next run", logName,
                retentionInDays, totalDeleted);
        return totalDeleted;
    }

    @FunctionalInterface
    private interface ExpiredIdFinder {

        List<Long> find(PageRequest pageable);
    }
}
