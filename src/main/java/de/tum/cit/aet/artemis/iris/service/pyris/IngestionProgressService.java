package de.tum.cit.aet.artemis.iris.service.pyris;

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
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureIngestionWebhookJob;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;

/**
 * In-memory registry of the lecture ingestions currently in flight, fed by the Pyris status callbacks. Each running
 * job's latest activity snapshot (the named pipeline steps with their state and duration) is kept here so the admin
 * ingestion dashboard can show live per-step progress; the entry is removed when the run reaches a terminal state.
 * <p>
 * This is a transient view for observability only, not an authority: it lives in memory on the node that receives the
 * callbacks and is intentionally not persisted.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IngestionProgressService {

    private static final Logger log = LoggerFactory.getLogger(IngestionProgressService.class);

    private final Map<String, ActiveIngestionDTO> activeByJobId = new ConcurrentHashMap<>();

    // Lecture unit names change rarely and are looked up across a module boundary, so they are cached by unit id.
    private final Map<Long, String> unitNameCache = new ConcurrentHashMap<>();

    private final Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi;

    public IngestionProgressService(Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi) {
        this.lectureUnitRepositoryApi = lectureUnitRepositoryApi;
    }

    /**
     * Records the latest status of a lecture ingestion job. A terminal update removes the job from the active set; any
     * other update stores its current activity snapshot.
     *
     * @param job          the lecture ingestion job
     * @param statusUpdate the status update received from Pyris
     * @param terminal     whether the run has reached a terminal state (finished or failed)
     */
    public void record(LectureIngestionWebhookJob job, PyrisLectureIngestionStatusUpdateDTO statusUpdate, boolean terminal) {
        if (terminal) {
            activeByJobId.remove(job.jobId());
            return;
        }
        String runState = statusUpdate.runState() != null ? statusUpdate.runState().name() : null;
        activeByJobId.put(job.jobId(),
                new ActiveIngestionDTO(job.jobId(), job.courseId(), job.lectureId(), job.lectureUnitId(), null, runState, statusUpdate.startedAt(), statusUpdate.activities()));
    }

    /**
     * @return a snapshot of all lecture ingestions currently in flight, enriched with the lecture unit name
     */
    public List<ActiveIngestionDTO> getActiveIngestions() {
        return activeByJobId.values().stream().map(ingestion -> ingestion.withLectureUnitName(resolveUnitName(ingestion.lectureUnitId()))).toList();
    }

    /**
     * Resolves the lecture unit name for display, caching successful lookups. Returns null when the name cannot be read.
     */
    private String resolveUnitName(long lectureUnitId) {
        String cached = unitNameCache.get(lectureUnitId);
        if (cached != null) {
            return cached;
        }
        if (lectureUnitRepositoryApi.isEmpty()) {
            return null;
        }
        try {
            String name = lectureUnitRepositoryApi.get().findByIdElseThrow(lectureUnitId).getName();
            if (name != null) {
                unitNameCache.put(lectureUnitId, name);
            }
            return name;
        }
        catch (Exception e) {
            log.debug("Could not resolve lecture unit name for {}: {}", lectureUnitId, e.getMessage());
            return null;
        }
    }
}
