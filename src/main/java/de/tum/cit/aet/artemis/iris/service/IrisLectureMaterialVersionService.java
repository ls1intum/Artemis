package de.tum.cit.aet.artemis.iris.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;
import de.tum.cit.aet.artemis.lecture.dto.LectureUnitIngestedVersionsDTO;

/**
 * Keeps the ingested material versions from before a chat run starts. Versions increase monotonically, so an update
 * during retrieval can make an answer conservatively outdated, but cannot relabel old coordinates as current.
 * Pyris must finish replacing its indexed material before ingestion is marked DONE.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisLectureMaterialVersionService {

    private static final Logger log = LoggerFactory.getLogger(IrisLectureMaterialVersionService.class);

    private final Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi;

    private final DistributedDataProvider distributedDataProvider;

    private final JsonMapper objectMapper;

    @Value("${artemis.iris.jobs.timeout:300}")
    private int jobTimeout;

    public IrisLectureMaterialVersionService(Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi, DistributedDataProvider distributedDataProvider,
            JsonMapper objectMapper) {
        this.lectureUnitRepositoryApi = lectureUnitRepositoryApi;
        this.distributedDataProvider = distributedDataProvider;
        this.objectMapper = objectMapper;
    }

    private DistributedMap<String, String> snapshots() {
        // JSON keeps the existing distributed ChatJob record and its serialized shape unchanged.
        return distributedDataProvider.getExpiringMap("pyris-chat-material-version-map", Duration.ofSeconds(jobTimeout));
    }

    /**
     * Captures only material in this run's course, before its request is dispatched to Pyris.
     *
     * @param jobId    the chat run
     * @param courseId the course whose indexed material may be retrieved
     */
    public void capture(String jobId, long courseId) {
        try {
            var versions = lectureUnitRepositoryApi.map(api -> api.findIngestedVersionsByCourseId(courseId)).orElse(List.of());
            snapshots().put(jobId, objectMapper.writeValueAsString(versions));
        }
        catch (RuntimeException e) {
            log.warn("Could not capture material versions for Iris job {}", jobId, e);
        }
    }

    /**
     * Missing, expired or unreadable snapshots yield no verified version, never the latest database revision.
     *
     * @param jobId the chat run
     * @return the immutable launch versions, or an empty map when unavailable
     */
    public Map<Long, LectureUnitIngestedVersionsDTO> getSnapshot(String jobId) {
        try {
            String snapshot = snapshots().get(jobId);
            if (snapshot == null) {
                return Map.of();
            }
            List<LectureUnitIngestedVersionsDTO> versions = objectMapper.readValue(snapshot, new TypeReference<>() {
            });
            return versions.stream().collect(Collectors.toUnmodifiableMap(LectureUnitIngestedVersionsDTO::lectureUnitId, Function.identity()));
        }
        catch (RuntimeException e) {
            log.warn("Could not read material versions for Iris job {}", jobId, e);
            return Map.of();
        }
    }

    /**
     * Extends an existing snapshot with its job, without reading newer versions.
     *
     * @param jobId the chat run
     */
    public void refresh(String jobId) {
        String snapshot = snapshots().get(jobId);
        if (snapshot != null) {
            snapshots().put(jobId, snapshot, Duration.ofSeconds(jobTimeout));
        }
    }

    /**
     * Removes the snapshot together with its completed or cancelled job.
     *
     * @param jobId the chat run
     */
    public void remove(String jobId) {
        snapshots().remove(jobId);
    }
}
