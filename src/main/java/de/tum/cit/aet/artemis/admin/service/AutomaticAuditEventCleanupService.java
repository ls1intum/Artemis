package de.tum.cit.aet.artemis.admin.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.admin.config.AuditEventRetentionProperties;
import de.tum.cit.aet.artemis.admin.repository.ApplicationAuditEventRepository;
import de.tum.cit.aet.artemis.admin.repository.PersistenceAuditEventRepository;
import de.tum.cit.aet.artemis.admin.repository.SecurityAuditEventRepository;

/**
 * Prunes the three audit logs, each on its own retention period.
 * <p>
 * Until this service existed, nothing pruned the audit log at all. The {@code jhipster.audit-events.retention-period}
 * property reads as though it covered audit events, but no bean ever read it: the only similarly named property is
 * {@code artemis.audit-events.retention-period} in {@code AutomaticVcsAccessLogCleanupService}, which applies to
 * {@code vcs_access_log} and lives in a different namespace. So audit events accumulated indefinitely.
 * <p>
 * <b>Why three retention periods.</b> The logs differ by orders of magnitude in both volume and how long an individual
 * record stays interesting:
 * <ul>
 * <li><b>General</b> (authentication) - one row per login, the bulk of the data, and an individual login is rarely of
 * interest after a while. Short retention (one year by default).</li>
 * <li><b>Security</b> (credential and identity changes) - rare, and needed to prove how an account reached its current
 * state, which may be asked years later (e.g. an exam dispute). Long retention (five years by default).</li>
 * <li><b>Application</b> (domain actions such as deleting an exercise or resetting an exam) - also needed to reconstruct
 * what happened to graded artefacts long after the fact. Long retention (five years by default).</li>
 * </ul>
 * Splitting the tables is what makes this possible: a single table would force one retention period for all three.
 * <p>
 * The periods are bound through {@link AuditEventRetentionProperties}, which validates them as positive, so a value that
 * would move the cutoff into the future and delete records written minutes ago fails startup rather than being applied.
 * <p>
 * Deletion goes through the entities rather than a bulk {@code DELETE}, because each log has an {@code @ElementCollection}
 * child table; the general log's foreign key is additionally {@code ON DELETE RESTRICT}, so a bulk delete of parent rows
 * would be rejected outright. That makes deletion comparatively expensive, which is why it is batched.
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
    private static final int MAX_BATCHES_PER_RUN = 200;

    private final PersistenceAuditEventRepository persistenceAuditEventRepository;

    private final SecurityAuditEventRepository securityAuditEventRepository;

    private final ApplicationAuditEventRepository applicationAuditEventRepository;

    /**
     * The three retention periods, validated as positive at binding time, so a value that would delete records written
     * minutes ago fails startup instead of being applied.
     */
    private final AuditEventRetentionProperties retentionProperties;

    public AutomaticAuditEventCleanupService(PersistenceAuditEventRepository persistenceAuditEventRepository, SecurityAuditEventRepository securityAuditEventRepository,
            ApplicationAuditEventRepository applicationAuditEventRepository, AuditEventRetentionProperties retentionProperties) {
        this.persistenceAuditEventRepository = persistenceAuditEventRepository;
        this.securityAuditEventRepository = securityAuditEventRepository;
        this.applicationAuditEventRepository = applicationAuditEventRepository;
        this.retentionProperties = retentionProperties;
    }

    /**
     * Deletes expired audit events from each of the three logs, applying that log's retention period.
     */
    // execute this every night at 3:10:00 am, offset from the other nightly cleanups so they do not contend
    @Scheduled(cron = "0 10 3 * * *")
    public void cleanup() {
        int generalRetentionInDays = retentionProperties.generalRetentionPeriod();
        int securityRetentionInDays = retentionProperties.securityRetentionPeriod();
        int applicationRetentionInDays = retentionProperties.applicationRetentionPeriod();

        int deletedGeneral = pruneIsolated("general", generalRetentionInDays, persistenceAuditEventRepository::findExpiredIds, persistenceAuditEventRepository::deleteAllById);
        int deletedSecurity = pruneIsolated("security", securityRetentionInDays, securityAuditEventRepository::findExpiredIds, securityAuditEventRepository::deleteAllById);
        int deletedApplication = pruneIsolated("application", applicationRetentionInDays, applicationAuditEventRepository::findExpiredIds,
                applicationAuditEventRepository::deleteAllById);

        if (deletedGeneral > 0 || deletedSecurity > 0 || deletedApplication > 0) {
            log.info(
                    "Scheduled deletion of expired audit events: removed {} general (older than {} days), {} security (older than {} days) and {} application "
                            + "(older than {} days) events",
                    deletedGeneral, generalRetentionInDays, deletedSecurity, securityRetentionInDays, deletedApplication, applicationRetentionInDays);
        }
    }

    /**
     * Runs one log's retention schedule, keeping its failures to itself.
     * <p>
     * The three schedules are independent and share one nightly trigger. Without this boundary, a persistent problem with
     * one of them - a lock timeout on a particular old row, say - would also stop the others from ever running, so a fault
     * in pruning the login record could let the other two logs grow unbounded indefinitely.
     *
     * @param logName         human-readable name of the log, for logging
     * @param retentionInDays how long events in this log are kept
     * @param expiredIdFinder supplies the next batch of expired ids for this log
     * @param deleteByIds     deletes the given ids from this log
     * @return how many events were deleted, or 0 if this log's schedule failed
     */
    private int pruneIsolated(String logName, int retentionInDays, ExpiredIdFinder expiredIdFinder, Consumer<List<Long>> deleteByIds) {
        try {
            return prune(logName, retentionInDays, expiredIdFinder, deleteByIds);
        }
        catch (Exception e) {
            log.error("Failed to prune the {} audit log (retention {} days); the other retention schedules still run", logName, retentionInDays, e);
            return 0;
        }
    }

    /**
     * Repeatedly fetches and deletes a batch of expired ids from one log until nothing expired is left or the per-run
     * batch cap is reached. Anything left over is picked up by the next run.
     *
     * @param logName         human-readable name of the log, for logging
     * @param retentionInDays how long events in this log are kept
     * @param expiredIdFinder supplies the next batch of expired ids for this log
     * @param deleteByIds     deletes the given ids from this log
     * @return how many events were deleted
     */
    private int prune(String logName, int retentionInDays, ExpiredIdFinder expiredIdFinder, Consumer<List<Long>> deleteByIds) {
        Instant cutoff = Instant.now().minus(retentionInDays, ChronoUnit.DAYS);
        int totalDeleted = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            List<Long> expiredIds = expiredIdFinder.find(cutoff, PageRequest.of(0, BATCH_SIZE));
            if (expiredIds.isEmpty()) {
                return totalDeleted;
            }
            deleteByIds.accept(expiredIds);
            totalDeleted += expiredIds.size();
        }
        log.info("Reached the per-run batch limit while pruning the {} audit log after {} rows; the remainder is pruned on the next run", logName, totalDeleted);
        return totalDeleted;
    }

    @FunctionalInterface
    private interface ExpiredIdFinder {

        List<Long> find(Instant before, Pageable pageable);
    }
}
