package de.tum.cit.aet.artemis.iris.service.pyris;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.IngestionHistoryEntry;
import de.tum.cit.aet.artemis.iris.dto.ActiveIngestionDTO;
import de.tum.cit.aet.artemis.iris.dto.RecentIngestionDTO;
import de.tum.cit.aet.artemis.iris.repository.IngestionHistoryRepository;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureIngestionWebhookJob;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;

/**
 * Registry of lecture ingestions for the admin ingestion dashboard, fed by the Pyris status callbacks.
 * <p>
 * The ingestions currently in flight are kept in memory (with each run's live activity snapshot and last-update time, so
 * a stalled run can be detected). Finished and failed runs are persisted to {@link IngestionHistoryEntry} so the recent
 * history survives an Artemis restart; old entries are pruned by a scheduled retention job. Observability only, not an
 * authority.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IngestionProgressService {

    private static final Logger log = LoggerFactory.getLogger(IngestionProgressService.class);

    private static final int MAX_RECENT = 100;

    private static final int MAX_ERROR_LENGTH = 2000;

    private record ActiveEntry(LectureIngestionWebhookJob job, PyrisLectureIngestionStatusUpdateDTO status, String lastUpdatedAt) {
    }

    private final Map<String, ActiveEntry> activeByJobId = new ConcurrentHashMap<>();

    // Names change rarely and are looked up across a module boundary, so they are cached by id.
    private final Map<Long, String> unitNameCache = new ConcurrentHashMap<>();

    private final Map<Long, String> lectureNameCache = new ConcurrentHashMap<>();

    private final IngestionHistoryRepository ingestionHistoryRepository;

    private final Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi;

    private final Optional<LectureRepositoryApi> lectureRepositoryApi;

    public IngestionProgressService(IngestionHistoryRepository ingestionHistoryRepository, Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi,
            Optional<LectureRepositoryApi> lectureRepositoryApi) {
        this.ingestionHistoryRepository = ingestionHistoryRepository;
        this.lectureUnitRepositoryApi = lectureUnitRepositoryApi;
        this.lectureRepositoryApi = lectureRepositoryApi;
    }

    /**
     * Records the latest status of a lecture ingestion job. A terminal update persists the run into the recent history
     * (finished or failed) and drops it from the active view; any other update stores its current activity snapshot.
     *
     * @param job          the lecture ingestion job
     * @param statusUpdate the status update received from Pyris
     * @param terminal     whether the run has reached a terminal state (finished or failed)
     */
    public void record(LectureIngestionWebhookJob job, PyrisLectureIngestionStatusUpdateDTO statusUpdate, boolean terminal) {
        if (terminal) {
            ActiveEntry previous = activeByJobId.remove(job.jobId());
            boolean failed = statusUpdate.runState() != null && "FAILED".equals(statusUpdate.runState().name());
            String startedAt = statusUpdate.startedAt() != null ? statusUpdate.startedAt() : (previous != null ? previous.status().startedAt() : null);
            List<PyrisActivityDTO> activities = statusUpdate.activities() != null ? statusUpdate.activities() : (previous != null ? previous.status().activities() : null);
            String errorMessage = failed && statusUpdate.error() != null ? statusUpdate.error().message() : null;
            saveHistory(job, failed, startedAt, activities, errorMessage);
            return;
        }
        activeByJobId.put(job.jobId(), new ActiveEntry(job, statusUpdate, Instant.now().toString()));
    }

    /**
     * Fails every in-flight ingestion and persists it to the recent history with the given reason. Called when Iris is
     * detected to have restarted: any run that was in flight across the restart is definitively dead, so this is a
     * deterministic signal rather than a silence timeout.
     *
     * @param reason the failure reason recorded on each failed run
     */
    public void failActive(String reason) {
        for (String jobId : List.copyOf(activeByJobId.keySet())) {
            ActiveEntry entry = activeByJobId.remove(jobId);
            if (entry == null) {
                continue;
            }
            saveHistory(entry.job(), true, entry.status().startedAt(), entry.status().activities(), reason);
        }
    }

    private void saveHistory(LectureIngestionWebhookJob job, boolean failed, String startedAtIso, List<PyrisActivityDTO> activities, String errorMessage) {
        ZonedDateTime finishedAt = ZonedDateTime.now();
        ZonedDateTime startedAt = parseInstant(startedAtIso);
        IngestionHistoryEntry entry = new IngestionHistoryEntry();
        entry.setJobId(job.jobId());
        entry.setCourseId(job.courseId());
        entry.setLectureId(job.lectureId());
        entry.setLectureUnitId(job.lectureUnitId());
        entry.setLectureUnitName(resolveUnitName(job.lectureUnitId()));
        entry.setLectureName(resolveLectureName(job.lectureId()));
        entry.setOutcome(failed ? "FAILED" : "FINISHED");
        entry.setStartedAt(startedAt);
        entry.setFinishedAt(finishedAt);
        entry.setTotalMillis(startedAt != null ? Math.max(0, Duration.between(startedAt, finishedAt).toMillis()) : null);
        entry.setFailedStepName(failed ? failedStepName(activities) : null);
        entry.setErrorMessage(truncate(errorMessage));
        entry.setActivities(activities);
        try {
            ingestionHistoryRepository.save(entry);
        }
        catch (Exception e) {
            log.warn("Could not persist ingestion history for job {}: {}", job.jobId(), e.getMessage());
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
     * @return the most recently finished or failed lecture ingestions, newest first, read from the persisted history
     */
    public List<RecentIngestionDTO> getRecentIngestions() {
        return ingestionHistoryRepository.findRecent(PageRequest.of(0, MAX_RECENT)).stream().map(IngestionProgressService::toDto).toList();
    }

    /**
     * Deletes persisted history entries that finished before the given cutoff (the retention boundary).
     *
     * @param cutoff entries finished before this time are removed
     * @return the number of deleted entries
     */
    public int pruneHistoryBefore(ZonedDateTime cutoff) {
        return ingestionHistoryRepository.deleteByFinishedAtBefore(cutoff);
    }

    private static RecentIngestionDTO toDto(IngestionHistoryEntry entry) {
        return new RecentIngestionDTO(entry.getJobId(), entry.getCourseId(), entry.getLectureId(), entry.getLectureUnitId(), entry.getLectureUnitName(), entry.getLectureName(),
                entry.getOutcome(), isoOrNull(entry.getStartedAt()), isoOrNull(entry.getFinishedAt()), entry.getTotalMillis(), entry.getActivities(), entry.getFailedStepName(),
                entry.getErrorMessage());
    }

    private static String isoOrNull(ZonedDateTime dateTime) {
        return dateTime != null ? dateTime.toInstant().toString() : null;
    }

    private static ZonedDateTime parseInstant(String iso) {
        if (iso == null) {
            return null;
        }
        try {
            return ZonedDateTime.ofInstant(Instant.parse(iso), ZoneOffset.UTC);
        }
        catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
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
