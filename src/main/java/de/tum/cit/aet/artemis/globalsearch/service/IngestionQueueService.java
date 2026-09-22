package de.tum.cit.aet.artemis.globalsearch.service;

import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOrigin;
import de.tum.cit.aet.artemis.globalsearch.dto.IngestionWorkerDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.LectureIngestionQueueDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.OutboxEntryDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.QueueOverviewDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.QueuedIngestionDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.ReconcilePassStatusDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.RunningIngestionDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.WeaviateOutboxQueueDTO;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntityReconcileStateRepository;
import de.tum.cit.aet.artemis.globalsearch.repository.WeaviateOutboxRepository;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitProcessingStateRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;

/**
 * Assembles the admin queue view: every queue this feature owns, and what each is doing right now.
 * <p>
 * The two queues it reports are unrelated in mechanism — the lecture ingestion queue is a table workers
 * pull from, the Weaviate outbox is a durable write queue drained on one node — and are reported together
 * because an operator asking "why is nothing being indexed" has to rule out both.
 * <p>
 * Every read here is a point-in-time snapshot taken per request. There is no websocket push: the admin
 * page polls, which keeps this off the hot path entirely and costs nothing when nobody is looking.
 */
@Conditional(WeaviateEnabled.class)
@Lazy
@Service
public class IngestionQueueService {

    /**
     * How many waiting units and outbox rows the queue head shows. Enough to see what is coming without
     * turning the view into a full listing of a backlog that can run to thousands.
     */
    private static final int QUEUE_HEAD_SIZE = 20;

    private final Optional<LectureUnitProcessingStateRepositoryApi> lectureUnitProcessingStateRepositoryApi;

    private final WeaviateOutboxRepository weaviateOutboxRepository;

    private final SearchableEntityReconcileStateRepository searchableEntityReconcileStateRepository;

    public IngestionQueueService(Optional<LectureUnitProcessingStateRepositoryApi> lectureUnitProcessingStateRepositoryApi, WeaviateOutboxRepository weaviateOutboxRepository,
            SearchableEntityReconcileStateRepository searchableEntityReconcileStateRepository) {
        this.lectureUnitProcessingStateRepositoryApi = lectureUnitProcessingStateRepositoryApi;
        this.weaviateOutboxRepository = weaviateOutboxRepository;
        this.searchableEntityReconcileStateRepository = searchableEntityReconcileStateRepository;
    }

    /**
     * Takes one snapshot of every queue.
     *
     * @return what each queue currently holds and is working on
     */
    public QueueOverviewDTO getQueueOverview() {
        return new QueueOverviewDTO(readLectureIngestionQueue(), readOutboxQueue(), readReconcilePasses(), readWorkers());
    }

    /**
     * Reads the lecture ingestion queue. Empty when the lecture module runs without Iris, in which case
     * nothing pulls from that table at all and its API bean is absent.
     */
    private LectureIngestionQueueDTO readLectureIngestionQueue() {
        if (lectureUnitProcessingStateRepositoryApi.isEmpty()) {
            return new LectureIngestionQueueDTO(Map.of(), List.of(), List.of(), 0);
        }
        var api = lectureUnitProcessingStateRepositoryApi.get();

        Map<ProcessingPhase, Long> countsByPhase = new EnumMap<>(ProcessingPhase.class);
        for (Object[] row : api.countGroupedByPhase()) {
            countsByPhase.put((ProcessingPhase) row[0], (Long) row[1]);
        }

        List<RunningIngestionDTO> running = api.findActiveRuns().stream().map(this::toRunning).toList();
        List<QueuedIngestionDTO> nextUp = api.findQueueHead(QUEUE_HEAD_SIZE).stream().map(this::toQueued).toList();

        return new LectureIngestionQueueDTO(countsByPhase, running, nextUp, api.countWaitingForRetry());
    }

    private RunningIngestionDTO toRunning(LectureUnitProcessingState state) {
        LectureUnit unit = state.getLectureUnit();
        Course course = courseOf(unit);
        return new RunningIngestionDTO(unit.getId(), unit.getName(), course == null ? null : course.getId(), course == null ? null : course.getTitle(), state.getPhase(),
                state.getCurrentStage(), state.getStageProgress(), state.getStageTotal(), state.getStartedAt(), state.getLastProgressAt(), state.getLastHeartbeatAt(),
                state.getLockedBy(), state.getRetryCount());
    }

    private QueuedIngestionDTO toQueued(LectureUnitProcessingState state) {
        LectureUnit unit = state.getLectureUnit();
        Course course = courseOf(unit);
        return new QueuedIngestionDTO(unit.getId(), unit.getName(), course == null ? null : course.getId(), course == null ? null : course.getTitle(), state.getDispatchPriority(),
                state.getRetryCount(), state.getRetryEligibleAt(), state.isForceReingest());
    }

    /**
     * The course a unit belongs to. Both queue reads fetch the lecture and course eagerly, so this never
     * triggers a lazy load; it is null-tolerant only because a unit can outlive its lecture in test data.
     */
    @Nullable
    private Course courseOf(LectureUnit unit) {
        return unit.getLecture() == null ? null : unit.getLecture().getCourse();
    }

    /**
     * Reads the Weaviate outbox backlog. Rows are deleted on success, so this is always the backlog and
     * never a history: a healthy queue is near-empty, and a deep one whose {@code maxAttempts} is climbing
     * is the signal that writes are failing rather than that work is merely arriving faster than it drains.
     */
    private WeaviateOutboxQueueDTO readOutboxQueue() {
        ZonedDateTime now = ZonedDateTime.now();

        Map<WeaviateOutboxOrigin, Long> countsByOrigin = new EnumMap<>(WeaviateOutboxOrigin.class);
        for (Object[] row : weaviateOutboxRepository.countGroupedByOrigin()) {
            countsByOrigin.put((WeaviateOutboxOrigin) row[0], (Long) row[1]);
        }

        List<OutboxEntryDTO> head = weaviateOutboxRepository.findDueForDispatch(now, QUEUE_HEAD_SIZE).stream().map(OutboxEntryDTO::of).toList();
        Integer maxAttempts = weaviateOutboxRepository.findMaxAttempts();

        return new WeaviateOutboxQueueDTO(weaviateOutboxRepository.count(), weaviateOutboxRepository.countByNextAttemptAtLessThanEqual(now), countsByOrigin,
                weaviateOutboxRepository.findOldestEnqueuedAt(), maxAttempts == null ? 0 : maxAttempts, head);
    }

    private List<ReconcilePassStatusDTO> readReconcilePasses() {
        return searchableEntityReconcileStateRepository.findAll().stream().map(ReconcilePassStatusDTO::of).toList();
    }

    private List<IngestionWorkerDTO> readWorkers() {
        if (lectureUnitProcessingStateRepositoryApi.isEmpty()) {
            return List.of();
        }
        return lectureUnitProcessingStateRepositoryApi.get().summariseActiveWorkers().stream()
                .map(row -> new IngestionWorkerDTO((String) row[0], (Long) row[1], (ZonedDateTime) row[2])).toList();
    }
}
