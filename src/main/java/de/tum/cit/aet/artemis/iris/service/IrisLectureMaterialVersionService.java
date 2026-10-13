package de.tum.cit.aet.artemis.iris.service;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
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
 * Keeps the ingested material versions from before a chat run or a global search answer starts. Versions increase monotonically, so an update
 * during retrieval can make an answer conservatively outdated, but cannot relabel old coordinates as current.
 * Pyris must finish replacing its indexed material before ingestion is marked DONE.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisLectureMaterialVersionService {

    private static final Logger log = LoggerFactory.getLogger(IrisLectureMaterialVersionService.class);

    /**
     * How long the versions of a global search answer's citations are kept once the answer is finished. The answer only exists while the search palette is open, so
     * continuing it in the chat happens well within this time.
     */
    private static final Duration GLOBAL_SEARCH_CITED_VERSIONS_TTL = Duration.ofHours(1);

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

    private DistributedMap<String, String> globalSearchSnapshots() {
        return distributedDataProvider.getExpiringMap("pyris-global-search-material-version-map", Duration.ofSeconds(jobTimeout));
    }

    /** A global search snapshot belongs to the user who asked, so a run id alone never reads another user's snapshot. */
    private static String globalSearchKey(String runId, String userLogin) {
        return userLogin + ":" + runId;
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

    /**
     * Captures the material of every course a global search answer may cite, before its request is dispatched to Pyris, like {@link #capture} does for a chat run.
     *
     * @param runId     the global search answer run
     * @param userLogin the user who asked
     * @param courseIds the courses the answer is scoped to
     */
    public void captureForGlobalSearch(String runId, String userLogin, Collection<Long> courseIds) {
        try {
            globalSearchSnapshots().put(globalSearchKey(runId, userLogin), objectMapper.writeValueAsString(currentVersions(courseIds)));
        }
        catch (RuntimeException e) {
            log.warn("Could not capture material versions for global search run {}", runId, e);
        }
    }

    /**
     * Keeps only the versions of the units a finished global search answer cites, for as long as the answer can be continued in the chat. An answer without a
     * snapshot from before its retrieval, an unscoped search that had no bounded set of courses to capture, is pinned to the versions its cited courses have now.
     *
     * @param runId          the global search answer run
     * @param userLogin      the user who asked
     * @param citedUnitIds   the lecture units the answer cites
     * @param citedCourseIds the courses of those units
     */
    public void keepCitedForGlobalSearch(String runId, String userLogin, Collection<Long> citedUnitIds, Collection<Long> citedCourseIds) {
        var key = globalSearchKey(runId, userLogin);
        try {
            if (citedUnitIds.isEmpty()) {
                globalSearchSnapshots().remove(key);
                return;
            }
            String captured = globalSearchSnapshots().get(key);
            var versions = captured != null ? readVersions(captured) : currentVersions(citedCourseIds);
            var cited = new HashSet<>(citedUnitIds);
            var citedVersions = versions.stream().filter(version -> cited.contains(version.lectureUnitId())).toList();
            globalSearchSnapshots().put(key, objectMapper.writeValueAsString(citedVersions), GLOBAL_SEARCH_CITED_VERSIONS_TTL);
        }
        catch (RuntimeException e) {
            log.warn("Could not keep the cited material versions for global search run {}", runId, e);
        }
    }

    /**
     * Removes the snapshot of a global search answer that ended without an answer to continue.
     *
     * @param runId     the global search answer run
     * @param userLogin the user who asked
     */
    public void removeGlobalSearch(String runId, String userLogin) {
        globalSearchSnapshots().remove(globalSearchKey(runId, userLogin));
    }

    /**
     * Missing, expired or unreadable snapshots yield no verified version, never the latest database revision.
     *
     * @param runId     the global search answer run
     * @param userLogin the user who asked
     * @return the versions of the answer's cited units, or an empty map when unavailable
     */
    public Map<Long, LectureUnitIngestedVersionsDTO> getGlobalSearchSnapshot(String runId, String userLogin) {
        try {
            String snapshot = globalSearchSnapshots().get(globalSearchKey(runId, userLogin));
            return snapshot == null ? Map.of() : toMap(readVersions(snapshot));
        }
        catch (RuntimeException e) {
            log.warn("Could not read material versions for global search run {}", runId, e);
            return Map.of();
        }
    }

    private List<LectureUnitIngestedVersionsDTO> currentVersions(Collection<Long> courseIds) {
        return lectureUnitRepositoryApi.map(api -> courseIds.stream().distinct().flatMap(courseId -> api.findIngestedVersionsByCourseId(courseId).stream()).toList())
                .orElse(List.of());
    }

    private List<LectureUnitIngestedVersionsDTO> readVersions(String snapshot) {
        return objectMapper.readValue(snapshot, new TypeReference<>() {
        });
    }

    private static Map<Long, LectureUnitIngestedVersionsDTO> toMap(List<LectureUnitIngestedVersionsDTO> versions) {
        return versions.stream().collect(Collectors.toUnmodifiableMap(LectureUnitIngestedVersionsDTO::lectureUnitId, Function.identity(), (first, second) -> first));
    }
}
