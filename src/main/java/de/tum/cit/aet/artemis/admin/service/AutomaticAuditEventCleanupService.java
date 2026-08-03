package de.tum.cit.aet.artemis.admin.service;

import static de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants.GENERAL_EVENT_TYPES;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

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
 * Deletion goes through the entities rather than a bulk {@code DELETE}, because {@code jhi_persistent_audit_evt_data} is
 * an {@code @ElementCollection} whose foreign key is {@code ON DELETE RESTRICT}, so a bulk delete of the parent rows
 * would be rejected by the database. That makes deletion comparatively expensive, which is why it is batched and capped.
 */
@Lazy
@Service
@Profile("scheduling & core")
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

    /**
     * Retention for the login record: successful, failed and passkey logins, and logouts. Short, because this is the bulk
     * of the table and an individual login attempt is rarely of interest after a while. One year by default, so that
     * year-over-year usage statistics (which read {@code AUTHENTICATION_SUCCESS} rows) still have a full year to report on.
     */
    @Value("${artemis.audit-events.general-retention-period:365}")
    private int generalRetentionPeriodInDays;

    /**
     * Retention for everything else: deliberate actions on courses, exercises, exams and accounts. Five years by default,
     * because these are the records an investigation into how something reached its current state relies on, and such
     * questions - an exam dispute in particular - can be raised years later.
     */
    @Value("${artemis.audit-events.application-retention-period:1825}")
    private int applicationRetentionPeriodInDays;

    public AutomaticAuditEventCleanupService(PersistenceAuditEventRepository persistenceAuditEventRepository) {
        this.persistenceAuditEventRepository = persistenceAuditEventRepository;
    }

    /**
     * Deletes expired audit events, applying the short retention to the login record and the long one to everything else.
     */
    // execute this every night at 3:10:00 am, offset from the other nightly cleanups so they do not contend
    @Scheduled(cron = "0 10 3 * * *")
    public void cleanup() {
        Instant generalCutoff = Instant.now().minus(generalRetentionPeriodInDays, ChronoUnit.DAYS);
        Instant applicationCutoff = Instant.now().minus(applicationRetentionPeriodInDays, ChronoUnit.DAYS);

        int deletedGeneralEvents = prune("general", generalRetentionPeriodInDays,
                pageable -> persistenceAuditEventRepository.findExpiredIdsOfTypes(generalCutoff, GENERAL_EVENT_TYPES, pageable));
        int deletedApplicationEvents = prune("application", applicationRetentionPeriodInDays,
                pageable -> persistenceAuditEventRepository.findExpiredIdsExcludingTypes(applicationCutoff, GENERAL_EVENT_TYPES, pageable));

        if (deletedGeneralEvents > 0 || deletedApplicationEvents > 0) {
            log.info("Scheduled deletion of expired audit events: removed {} general/authentication events (older than {} days) and {} application events (older than {} days)",
                    deletedGeneralEvents, generalRetentionPeriodInDays, deletedApplicationEvents, applicationRetentionPeriodInDays);
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
