package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.lecture.web.LectureWebsocketTopics.UNIT_PROCESSING_STATE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.LectureUnitCombinedStatusDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;

/**
 * Pushes lecture unit processing state changes to the clients watching a lecture.
 * <p>
 * Separated from the state machine that produces those changes: every write path in
 * {@link ProcessingStateCallbackService} and {@link LectureContentProcessingScheduler} ends by telling the clients what
 * happened, and keeping the transport here means a broker failure surfaces in one place rather than at each of them.
 */
@Conditional(LectureWithIrisEnabled.class)
@Service
@Lazy
public class ProcessingStateNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ProcessingStateNotificationService.class);

    private final WebsocketMessagingService websocketMessagingService;

    private final LectureTranscriptionRepository transcriptionRepository;

    public ProcessingStateNotificationService(WebsocketMessagingService websocketMessagingService, LectureTranscriptionRepository transcriptionRepository) {
        this.websocketMessagingService = websocketMessagingService;
        this.transcriptionRepository = transcriptionRepository;
    }

    /**
     * Push a state change carrying the unit's current transcription status, so the badge never loses it on a
     * transition that does not itself touch the transcription.
     *
     * @param state the processing state to broadcast
     */
    public void notifyWithTranscriptionStatus(LectureUnitProcessingState state) {
        notifyProcessingStateChange(state,
                transcriptionRepository.findByLectureUnit_Id(state.getLectureUnit().getId()).map(LectureTranscription::getTranscriptionStatus).orElse(null));
    }

    /**
     * Broadcast a processing state change to all subscribers of the lecture's processing state topic.
     *
     * @param state               the updated processing state
     * @param transcriptionStatus the current transcription status (may be {@code null})
     */
    public void notifyProcessingStateChange(LectureUnitProcessingState state, TranscriptionStatus transcriptionStatus) {
        LectureUnit unit = state.getLectureUnit();
        if (unit == null || unit.getLecture() == null) {
            return;
        }
        long lectureId = unit.getLecture().getId();
        var dto = LectureUnitCombinedStatusDTO.of(unit.getId(), state, transcriptionStatus);
        var topic = UNIT_PROCESSING_STATE.at(lectureId);
        websocketMessagingService.sendMessage(topic, dto);
        log.debug("Sent processing state WebSocket update for unit {} on topic {}", unit.getId(), topic);
    }
}
