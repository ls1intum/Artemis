package de.tum.cit.aet.artemis.iris.service.pyris;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.dto.ActiveIngestionDTO;
import de.tum.cit.aet.artemis.iris.dto.RecentIngestionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureIngestionWebhookJob;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;

/**
 * In-memory registry of lecture ingestions for the admin ingestion dashboard, fed by the Pyris status callbacks.
 * <p>
 * It keeps two views: the ingestions currently in flight (with each run's live activity snapshot and last-update time,
 * so a stalled run can be detected), and a bounded history of the most recently finished or failed runs (with the full
 * per-step timeline and, for failures, the step it failed at and the error). Observability only, not an authority: the
 * state lives in memory on the node that receives the callbacks and is intentionally not persisted.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IngestionProgressService {

    private static final Logger log = LoggerFactory.getLogger(IngestionProgressService.class);

    private static final int MAX_RECENT = 50;

    private record ActiveEntry(LectureIngestionWebhookJob job, PyrisLectureIngestionStatusUpdateDTO status, String lastUpdatedAt) {
    }

    private final Map<String, ActiveEntry> activeByJobId = new ConcurrentHashMap<>();

    private final Deque<RecentIngestionDTO> recent = new ArrayDeque<>();

    // Names change rarely and are looked up across a module boundary, so they are cached by id.
    private final Map<Long, String> unitNameCache = new ConcurrentHashMap<>();

    private final Map<Long, String> lectureNameCache = new ConcurrentHashMap<>();

    private final Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi;

    private final Optional<LectureRepositoryApi> lectureRepositoryApi;

    public IngestionProgressService(Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi, Optional<LectureRepositoryApi> lectureRepositoryApi) {
        this.lectureUnitRepositoryApi = lectureUnitRepositoryApi;
        this.lectureRepositoryApi = lectureRepositoryApi;
    }

    /**
     * Records the latest status of a lecture ingestion job. A terminal update moves the run into the recent history
     * (finished or failed); any other update stores its current activity snapshot as active.
     *
     * @param job          the lecture ingestion job
     * @param statusUpdate the status update received from Pyris
     * @param terminal     whether the run has reached a terminal state (finished or failed)
     */
    public void record(LectureIngestionWebhookJob job, PyrisLectureIngestionStatusUpdateDTO statusUpdate, boolean terminal) {
        String now = Instant.now().toString();
        if (terminal) {
            ActiveEntry previous = activeByJobId.remove(job.jobId());
            addRecent(job, statusUpdate, previous, now);
            return;
        }
        activeByJobId.put(job.jobId(), new ActiveEntry(job, statusUpdate, now));
    }

    private void addRecent(LectureIngestionWebhookJob job, PyrisLectureIngestionStatusUpdateDTO statusUpdate, ActiveEntry previous, String finishedAt) {
        boolean failed = statusUpdate.runState() != null && "FAILED".equals(statusUpdate.runState().name());
        String startedAt = statusUpdate.startedAt() != null ? statusUpdate.startedAt() : (previous != null ? previous.status().startedAt() : null);
        List<PyrisActivityDTO> activities = statusUpdate.activities() != null ? statusUpdate.activities() : (previous != null ? previous.status().activities() : null);
        Long totalMillis = totalMillis(startedAt, finishedAt);
        String failedStepName = failed ? failedStepName(activities) : null;
        String errorMessage = failed && statusUpdate.error() != null ? statusUpdate.error().message() : null;

        RecentIngestionDTO entry = new RecentIngestionDTO(job.jobId(), job.courseId(), job.lectureId(), job.lectureUnitId(), resolveUnitName(job.lectureUnitId()),
                resolveLectureName(job.lectureId()), failed ? "FAILED" : "FINISHED", startedAt, finishedAt, totalMillis, activities, failedStepName, errorMessage);
        synchronized (recent) {
            recent.addFirst(entry);
            while (recent.size() > MAX_RECENT) {
                recent.removeLast();
            }
        }
    }

    /**
     * Fails every in-flight ingestion and moves it into the recent history with the given reason. Called when Iris is
     * detected to have restarted: any run that was in flight across the restart is definitively dead, so this is a
     * deterministic signal rather than a silence timeout.
     *
     * @param reason the failure reason recorded on each moved run
     */
    public void failActive(String reason) {
        String finishedAt = Instant.now().toString();
        for (String jobId : List.copyOf(activeByJobId.keySet())) {
            ActiveEntry entry = activeByJobId.remove(jobId);
            if (entry == null) {
                continue;
            }
            String startedAt = entry.status().startedAt();
            List<PyrisActivityDTO> activities = entry.status().activities();
            RecentIngestionDTO recentEntry = new RecentIngestionDTO(entry.job().jobId(), entry.job().courseId(), entry.job().lectureId(), entry.job().lectureUnitId(),
                    resolveUnitName(entry.job().lectureUnitId()), resolveLectureName(entry.job().lectureId()), "FAILED", startedAt, finishedAt, totalMillis(startedAt, finishedAt),
                    activities, failedStepName(activities), reason);
            synchronized (recent) {
                recent.addFirst(recentEntry);
                while (recent.size() > MAX_RECENT) {
                    recent.removeLast();
                }
            }
        }
    }

    /**
     * The name of the step a failed run failed at: the last activity that did not finish (the one it died on), or null.
     */
    private static String failedStepName(List<PyrisActivityDTO> activities) {
        if (activities == null || activities.isEmpty()) {
            return null;
        }
        for (int i = activities.size() - 1; i >= 0; i--) {
            PyrisActivityDTO activity = activities.get(i);
            if (activity.state() == null || !"FINISHED".equals(activity.state().name())) {
                return activity.name();
            }
        }
        return activities.getLast().name();
    }

    private static Long totalMillis(String startedAt, String finishedAt) {
        if (startedAt == null) {
            return null;
        }
        try {
            return Math.max(0, Instant.parse(finishedAt).toEpochMilli() - Instant.parse(startedAt).toEpochMilli());
        }
        catch (Exception e) {
            return null;
        }
    }

    /**
     * @return a snapshot of all lecture ingestions currently in flight, enriched with the lecture unit and lecture names
     */
    public List<ActiveIngestionDTO> getActiveIngestions() {
        return activeByJobId.values().stream()
                .map(entry -> new ActiveIngestionDTO(entry.job().jobId(), entry.job().courseId(), entry.job().lectureId(), entry.job().lectureUnitId(),
                        resolveUnitName(entry.job().lectureUnitId()), resolveLectureName(entry.job().lectureId()),
                        entry.status().runState() != null ? entry.status().runState().name() : null, entry.status().startedAt(), entry.lastUpdatedAt(),
                        entry.status().activities()))
                .toList();
    }

    /**
     * @return the most recently finished or failed lecture ingestions, newest first
     */
    public List<RecentIngestionDTO> getRecentIngestions() {
        synchronized (recent) {
            return List.copyOf(recent);
        }
    }

    private String resolveUnitName(long lectureUnitId) {
        return resolveName(unitNameCache, lectureUnitId, id -> lectureUnitRepositoryApi.map(api -> api.findByIdElseThrow(id).getName()).orElse(null));
    }

    private String resolveLectureName(long lectureId) {
        return resolveName(lectureNameCache, lectureId, id -> lectureRepositoryApi.map(api -> api.findByIdElseThrow(id).getTitle()).orElse(null));
    }

    private String resolveName(Map<Long, String> cache, long id, java.util.function.LongFunction<String> lookup) {
        String cached = cache.get(id);
        if (cached != null) {
            return cached;
        }
        try {
            String name = lookup.apply(id);
            if (name != null) {
                cache.put(id, name);
            }
            return name;
        }
        catch (Exception e) {
            log.debug("Could not resolve name for id {}: {}", id, e.getMessage());
            return null;
        }
    }
}
