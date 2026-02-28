package de.tum.cit.aet.artemis.iris.service.pyris;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.transcription.PyrisTranscriptionResultDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.transcription.PyrisTranscriptionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TranscriptionWebhookJob;
import de.tum.cit.aet.artemis.lecture.api.LectureTranscriptionsRepositoryApi;
import de.tum.cit.aet.artemis.lecture.api.ProcessingStateCallbackApi;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;

/**
 * Handles status updates for video transcription webhook jobs.
 * Extracted from {@link PyrisStatusUpdateService} to keep that class within complexity bounds.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class PyrisTranscriptionStatusUpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(PyrisTranscriptionStatusUpdateHandler.class);

    private final PyrisJobService pyrisJobService;

    private final Optional<LectureTranscriptionsRepositoryApi> lectureTranscriptionsRepositoryApi;

    private final Optional<ProcessingStateCallbackApi> processingStateCallbackApi;

    private final ObjectMapper objectMapper;

    private final WebsocketMessagingService websocketMessagingService;

    public PyrisTranscriptionStatusUpdateHandler(PyrisJobService pyrisJobService, Optional<LectureTranscriptionsRepositoryApi> lectureTranscriptionsRepositoryApi,
            Optional<ProcessingStateCallbackApi> processingStateCallbackApi, ObjectMapper objectMapper, WebsocketMessagingService websocketMessagingService) {
        this.pyrisJobService = pyrisJobService;
        this.lectureTranscriptionsRepositoryApi = lectureTranscriptionsRepositoryApi;
        this.processingStateCallbackApi = processingStateCallbackApi;
        this.objectMapper = objectMapper;
        this.websocketMessagingService = websocketMessagingService;
    }

    /**
     * Handles the status update of a video transcription job.
     * When complete, saves the transcription result and notifies the callback service.
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(TranscriptionWebhookJob job, PyrisTranscriptionStatusUpdateDTO statusUpdate) {
        var isDone = statusUpdate.stages().stream().map(PyrisStageDTO::state).allMatch(PyrisStageState::isTerminal);

        if (isDone) {
            pyrisJobService.removeJob(job);
            boolean success = statusUpdate.stages().stream().map(PyrisStageDTO::state).noneMatch(state -> state == PyrisStageState.ERROR);
            saveTranscriptionResult(job.jobId(), success, statusUpdate.result());
        }
        else {
            pyrisJobService.updateJob(job);
        }

        // Notify the frontend so it can refresh the processing status without a manual page reload
        websocketMessagingService.sendMessage("/topic/lectures/" + job.lectureId() + "/ingestion-status", Map.of("lectureUnitId", job.lectureUnitId()));
    }

    private void saveTranscriptionResult(String jobId, boolean success, String resultJson) {
        lectureTranscriptionsRepositoryApi.ifPresent(api -> {
            LectureTranscription transcription = api.findByJobId(jobId).orElse(null);
            if (transcription == null) {
                log.error("No transcription found for jobId: {}", jobId);
                return;
            }

            if (!success) {
                transcription.setTranscriptionStatus(TranscriptionStatus.FAILED);
            }
            else if (resultJson != null) {
                try {
                    PyrisTranscriptionResultDTO result = objectMapper.readValue(resultJson, PyrisTranscriptionResultDTO.class);
                    transcription.setLanguage(result.language());
                    transcription.setSegments(result.segments());
                    transcription.setTranscriptionStatus(TranscriptionStatus.COMPLETED);
                }
                catch (JsonProcessingException e) {
                    log.error("Failed to parse transcription result for jobId: {}", jobId, e);
                    transcription.setTranscriptionStatus(TranscriptionStatus.FAILED);
                }
            }

            LectureTranscription saved = api.save(transcription);
            processingStateCallbackApi.ifPresent(callback -> callback.handleTranscriptionComplete(saved));
        });
    }
}
