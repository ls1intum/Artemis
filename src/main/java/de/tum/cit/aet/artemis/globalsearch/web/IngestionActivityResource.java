package de.tum.cit.aet.artemis.globalsearch.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.domain.IngestionEventKind;
import de.tum.cit.aet.artemis.globalsearch.dto.IngestionActivityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IngestionEventDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.QueueOverviewDTO;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionEventLogService;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionQueueService;

/**
 * Admin endpoints behind the ingestion dashboard's activity feed and queue view.
 * <p>
 * Sits beside {@link IngestionCoverageResource}, which answers "is this content indexed"; these two
 * endpoints answer the other two questions an operator has: what did the pipelines just do, and what are
 * they doing now.
 */
@Profile(PROFILE_CORE)
@Conditional(WeaviateEnabled.class)
@EnforceAdmin
@Lazy
@RestController
@RequestMapping("api/global-search/admin/")
@FeatureUsage("monitoring/ingestion-dashboard")
public class IngestionActivityResource {

    /**
     * Default number of events in one feed page when the caller names none.
     */
    private static final int DEFAULT_FEED_SIZE = 100;

    /**
     * Trailing window the feed's summary tiles count over.
     */
    private static final int SUMMARY_WINDOW_HOURS = 24;

    private final IngestionEventLogService ingestionEventLogService;

    private final IngestionQueueService ingestionQueueService;

    public IngestionActivityResource(IngestionEventLogService ingestionEventLogService, IngestionQueueService ingestionQueueService) {
        this.ingestionEventLogService = ingestionEventLogService;
        this.ingestionQueueService = ingestionQueueService;
    }

    /**
     * GET global-search/admin/activity : the recent pipeline activity feed, newest first.
     *
     * @param kind     optional event kind to narrow the feed to
     * @param courseId optional course to narrow the feed to
     * @param limit    how many events to return; capped by the service
     * @return the events plus the rolling per-kind totals shown above them
     */
    @GetMapping("activity")
    public ResponseEntity<IngestionActivityDTO> getActivity(@RequestParam(required = false) @Nullable IngestionEventKind kind,
            @RequestParam(required = false) @Nullable Long courseId, @RequestParam(defaultValue = "" + DEFAULT_FEED_SIZE) int limit) {
        List<IngestionEventDTO> events = ingestionEventLogService.findRecent(kind, courseId, limit).stream().map(IngestionEventDTO::of).toList();

        Map<IngestionEventKind, Long> countsByKind = new EnumMap<>(IngestionEventKind.class);
        for (IngestionEventKind eventKind : IngestionEventKind.values()) {
            countsByKind.put(eventKind, ingestionEventLogService.countSince(eventKind, SUMMARY_WINDOW_HOURS));
        }

        return ResponseEntity.ok(new IngestionActivityDTO(events, countsByKind, SUMMARY_WINDOW_HOURS));
    }

    /**
     * GET global-search/admin/queues : a point-in-time snapshot of every ingestion queue.
     *
     * @return what each queue holds and is currently working on
     */
    @GetMapping("queues")
    public ResponseEntity<QueueOverviewDTO> getQueues() {
        return ResponseEntity.ok(ingestionQueueService.getQueueOverview());
    }
}
