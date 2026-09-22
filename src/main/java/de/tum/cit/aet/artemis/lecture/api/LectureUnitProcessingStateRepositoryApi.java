package de.tum.cit.aet.artemis.lecture.api;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.dto.IngestionJobIdentityDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;

/**
 * Read-only access to the lecture unit processing state ledger for other modules.
 * <p>
 * Deliberately backed by the repository alone: the iris module authenticates ingestion callbacks against
 * this ledger, and routing that lookup through {@link ProcessingStateCallbackApi} would close a bean cycle
 * (the callback service dispatches through the iris module, whose job service authenticates through the
 * callback service).
 */
@Conditional(LectureWithIrisEnabled.class)
@Controller
@Lazy
public class LectureUnitProcessingStateRepositoryApi extends AbstractLectureApi {

    private final LectureUnitProcessingStateRepository processingStateRepository;

    public LectureUnitProcessingStateRepositoryApi(LectureUnitProcessingStateRepository processingStateRepository) {
        this.processingStateRepository = processingStateRepository;
    }

    /**
     * Resolve the identity of the ingestion run that owns a job token.
     *
     * @param token the Pyris job token of an in-flight ingestion run
     * @return the course, lecture and unit the run belongs to, empty when no in-flight run owns the token
     */
    public Optional<IngestionJobIdentityDTO> findIngestionJobIdentityByToken(String token) {
        return processingStateRepository.findIngestionJobIdentityByToken(token);
    }

    /**
     * The phases in which a worker is actually holding a unit.
     */
    private static final List<ProcessingPhase> ACTIVE_PHASES = List.of(ProcessingPhase.TRANSCRIBING, ProcessingPhase.INGESTING);

    /**
     * Reads the ingestion runs a worker currently holds, with unit, lecture and course fetched.
     *
     * @return the active runs, oldest start first
     */
    public List<LectureUnitProcessingState> findActiveRuns() {
        return processingStateRepository.findActiveRunsWithUnit(ACTIVE_PHASES);
    }

    /**
     * Reads the units that would be claimed next, in dispatch order, with unit, lecture and course fetched.
     *
     * @param limit how many to read
     * @return the queue head, in dispatch order
     */
    public List<LectureUnitProcessingState> findQueueHead(int limit) {
        return processingStateRepository.findQueueHeadWithUnit(ZonedDateTime.now(), PageRequest.of(0, limit));
    }

    /**
     * Counts units per processing phase.
     *
     * @return one row per phase as {@code [phase, count]}
     */
    public List<Object[]> countGroupedByPhase() {
        return processingStateRepository.countGroupedByPhase();
    }

    /**
     * Counts failed units still waiting out their retry backoff.
     *
     * @return the number of units in backoff
     */
    public long countWaitingForRetry() {
        return processingStateRepository.countWaitingForRetry(ZonedDateTime.now());
    }

    /**
     * Summarises the workers currently holding leases.
     *
     * @return one row per worker as {@code [bootId, activeRuns, lastHeartbeatAt]}
     */
    public List<Object[]> summariseActiveWorkers() {
        return processingStateRepository.summariseActiveWorkers(ACTIVE_PHASES);
    }
}
