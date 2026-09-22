package de.tum.cit.aet.artemis.globalsearch.service;

import java.time.ZonedDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.domain.IngestionEventKind;
import de.tum.cit.aet.artemis.globalsearch.domain.IngestionEventLogEntry;
import de.tum.cit.aet.artemis.globalsearch.repository.IngestionEventLogRepository;

/**
 * Records what the ingestion and searchable-entity pipelines did, for the admin activity feed.
 * <p>
 * Every write is best-effort: the log exists so an operator can see what happened, and no pipeline
 * decision reads it back, so a failed insert must never propagate into the work being described. All
 * recording methods therefore swallow their exceptions after logging them.
 */
@Conditional(WeaviateEnabled.class)
@Lazy
@Service
public class IngestionEventLogService {

    private static final Logger log = LoggerFactory.getLogger(IngestionEventLogService.class);

    /**
     * Hard ceiling on one feed page, so a caller cannot ask for the whole table.
     */
    private static final int MAX_FEED_SIZE = 500;

    /**
     * Longest a {@code detail} string is stored at; the column is 512 and the feed renders one line.
     */
    private static final int MAX_DETAIL_LENGTH = 512;

    private final IngestionEventLogRepository ingestionEventLogRepository;

    private final int retentionDays;

    public IngestionEventLogService(IngestionEventLogRepository ingestionEventLogRepository, @Value("${artemis.global-search.event-log.retention-days:14}") int retentionDays) {
        this.ingestionEventLogRepository = ingestionEventLogRepository;
        this.retentionDays = retentionDays;
    }

    /**
     * Appends one event. Never throws.
     *
     * @param kind       what happened
     * @param entityType the kind of thing it happened to, e.g. {@code LectureUnit} or {@code Exercise}
     * @param entityId   the id of that thing
     * @param courseId   the course it belongs to, or null when not known without an extra query
     * @param detail     short human-readable context, truncated to fit the column
     */
    public void record(IngestionEventKind kind, String entityType, Long entityId, @Nullable Long courseId, @Nullable String detail) {
        try {
            ingestionEventLogRepository.save(new IngestionEventLogEntry(kind, ZonedDateTime.now(), entityType, entityId, courseId, truncate(detail)));
        }
        catch (Exception exception) {
            // Observability must not break the pipeline it observes.
            log.warn("Could not record ingestion event {} for {} {}", kind, entityType, entityId, exception);
        }
    }

    /**
     * Reads the most recent events, newest first.
     *
     * @param kind     the event kind to filter by, or null for every kind
     * @param courseId the course to filter by, or null for every course
     * @param limit    how many events to return, capped at {@value #MAX_FEED_SIZE}
     * @return the matching events, newest first
     */
    public List<IngestionEventLogEntry> findRecent(@Nullable IngestionEventKind kind, @Nullable Long courseId, int limit) {
        return ingestionEventLogRepository.findRecent(kind, courseId, PageRequest.of(0, Math.clamp(limit, 1, MAX_FEED_SIZE)));
    }

    /**
     * Counts events of one kind within the trailing window the feed summarises.
     *
     * @param kind        the event kind to count
     * @param windowHours how far back to count
     * @return the number of matching events
     */
    public long countSince(IngestionEventKind kind, int windowHours) {
        return ingestionEventLogRepository.countByKindAndOccurredAtGreaterThanEqual(kind, ZonedDateTime.now().minusHours(windowHours));
    }

    /**
     * Drops events past the configured retention window. Runs once an hour; the log is observability data,
     * so pruning is a plain delete with no archival step.
     */
    @Scheduled(cron = "0 15 * * * *")
    public void pruneOldEvents() {
        try {
            int deleted = ingestionEventLogRepository.deleteOlderThan(ZonedDateTime.now().minusDays(retentionDays));
            if (deleted > 0) {
                log.info("Pruned {} ingestion event log rows older than {} days", deleted, retentionDays);
            }
        }
        catch (Exception exception) {
            log.warn("Could not prune the ingestion event log", exception);
        }
    }

    @Nullable
    private String truncate(@Nullable String detail) {
        if (detail == null || detail.length() <= MAX_DETAIL_LENGTH) {
            return detail;
        }
        return detail.substring(0, MAX_DETAIL_LENGTH - 1) + "…";
    }
}
