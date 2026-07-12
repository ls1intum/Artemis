package de.tum.cit.aet.artemis.lecture.service;

import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.LectureUnitCombinedStatusDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;

/**
 * Handles lightweight recovery of lecture processing states after infrastructure-level Iris restarts.
 * <p>
 * This service intentionally does not depend on the dispatch pipeline or Iris APIs. It is used by the
 * Pyris health indicator during startup checks and must stay lightweight.
 */
@Conditional(LectureWithIrisEnabled.class)
@Service
@Lazy
public class ProcessingStateRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(ProcessingStateRecoveryService.class);

    private static final String PROCESSING_STATE_TOPIC = "/topic/lectures/%d/unit-processing-state";

    private final LectureUnitProcessingStateRepository processingStateRepository;

    private final LectureTranscriptionRepository transcriptionRepository;

    private final WebsocketMessagingService websocketMessagingService;

    public ProcessingStateRecoveryService(LectureUnitProcessingStateRepository processingStateRepository, LectureTranscriptionRepository transcriptionRepository,
            WebsocketMessagingService websocketMessagingService) {
        this.processingStateRepository = processingStateRepository;
        this.transcriptionRepository = transcriptionRepository;
        this.websocketMessagingService = websocketMessagingService;
    }

    /**
     * Handle an Iris restart notification.
     * <p>
     * When Iris starts up, all previous in-flight jobs are lost. This method
     * resets all TRANSCRIBING/INGESTING states to IDLE so they
     * get re-dispatched to the now-fresh Iris instance.
     *
     * @return the number of jobs that were reset
     */
    @Transactional
    public int handleIrisReset() {
        List<LectureUnitProcessingState> activeStates = processingStateRepository.findByPhaseIn(List.of(ProcessingPhase.TRANSCRIBING, ProcessingPhase.INGESTING));

        if (activeStates.isEmpty()) {
            log.info("Iris reset: no active processing jobs to recover");
            return 0;
        }

        log.warn("Iris reset: recovering {} in-flight jobs", activeStates.size());

        for (LectureUnitProcessingState state : activeStates) {
            // Do NOT treat an Iris restart as content-processing failure. The job was lost
            // by infrastructure, so retry budget must be preserved across rollouts/restarts.
            resetToIdleForRecovery(state);
        }

        return activeStates.size();
    }

    /**
     * Reset a stuck processing state directly to IDLE without touching the retry budget.
     *
     * @param state the stuck processing state to reset
     */
    void resetToIdleForRecovery(LectureUnitProcessingState state) {
        log.info("Recovering interrupted unit {} (was {}) - resetting to IDLE, retry budget preserved", state.getLectureUnit().getId(), state.getPhase());
        state.setPhase(ProcessingPhase.IDLE);
        state.setIngestionJobToken(null);
        state.setStartedAt(null);
        state.setRetryEligibleAt(null);
        state.setLastUpdated(ZonedDateTime.now());
        processingStateRepository.save(state);

        TranscriptionStatus txStatus = transcriptionRepository.findByLectureUnit_Id(state.getLectureUnit().getId()).map(LectureTranscription::getTranscriptionStatus).orElse(null);
        notifyProcessingStateChange(state, txStatus);
    }

    private void notifyProcessingStateChange(LectureUnitProcessingState state, TranscriptionStatus transcriptionStatus) {
        LectureUnit unit = state.getLectureUnit();
        if (unit == null || unit.getLecture() == null) {
            return;
        }
        long lectureId = unit.getLecture().getId();
        var dto = LectureUnitCombinedStatusDTO.of(unit.getId(), state, transcriptionStatus);
        String topic = PROCESSING_STATE_TOPIC.formatted(lectureId);
        websocketMessagingService.sendMessage(topic, dto);
        log.debug("Sent processing state WebSocket update for unit {} on topic {}", unit.getId(), topic);
    }
}
