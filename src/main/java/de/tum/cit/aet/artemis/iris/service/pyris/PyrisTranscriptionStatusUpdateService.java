package de.tum.cit.aet.artemis.iris.service.pyris;

import java.util.List;
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
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;

/**
 * Handles status updates for video transcription webhook jobs.
 * Extracted from {@link PyrisStatusUpdateService} to keep that class within complexity bounds.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class PyrisTranscriptionStatusUpdateService {

    private static final Logger log = LoggerFactory.getLogger(PyrisTranscriptionStatusUpdateService.class);

    private final PyrisJobService pyrisJobService;

    private final Optional<LectureTranscriptionsRepositoryApi> lectureTranscriptionsRepositoryApi;

    private final Optional<ProcessingStateCallbackApi> processingStateCallbackApi;

    private final ObjectMapper objectMapper;

    private final WebsocketMessagingService websocketMessagingService;

    public PyrisTranscriptionStatusUpdateService(PyrisJobService pyrisJobService, Optional<LectureTranscriptionsRepositoryApi> lectureTranscriptionsRepositoryApi,
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
        if (statusUpdate.stages() == null || statusUpdate.stages().isEmpty()) {
            pyrisJobService.updateJob(job);
            websocketMessagingService.sendMessage("/topic/lectures/" + job.lectureId() + "/ingestion-status", Map.of("lectureUnitId", job.lectureUnitId()));
            return;
        }

        // Clear the queued flag on first callback so the UI shows "Transcribing" instead of "Awaiting Processing"
        processingStateCallbackApi.ifPresent(api -> api.acknowledgeTranscriptionJob(job.lectureUnitId()));

        var isDone = statusUpdate.stages().stream().map(PyrisStageDTO::state).allMatch(PyrisStageState::isTerminal);

        if (isDone) {
            boolean success = statusUpdate.stages().stream().map(PyrisStageDTO::state).noneMatch(state -> state == PyrisStageState.ERROR);
            saveTranscriptionResult(job.jobId(), job.lectureUnitId(), success, statusUpdate.result());
            pyrisJobService.removeJob(job);
        }
        else {
            pyrisJobService.updateJob(job);
        }

        // Notify the frontend so it can refresh the processing status without a manual page reload
        websocketMessagingService.sendMessage("/topic/lectures/" + job.lectureId() + "/ingestion-status", Map.of("lectureUnitId", job.lectureUnitId()));
    }

    private void saveTranscriptionResult(String jobId, Long lectureUnitId, boolean success, String resultJson) {
        lectureTranscriptionsRepositoryApi.ifPresent(api -> {
            LectureTranscription transcription = api.findByJobId(jobId).orElse(null);
            if (transcription == null) {
                log.error("No transcription row found for jobId: {} (lectureUnitId: {}) — triggering failure handling on processing state", jobId, lectureUnitId);
                processingStateCallbackApi.ifPresent(callback -> callback.handleTranscriptionFailureForUnit(lectureUnitId));
                return;
            }

            if (!success) {
                transcription.setTranscriptionStatus(TranscriptionStatus.FAILED);
            }
            else if (resultJson != null) {
                try {
                    PyrisTranscriptionResultDTO result = objectMapper.readValue(resultJson, PyrisTranscriptionResultDTO.class);
                    List<LectureTranscriptionSegment> segments = result.segments().stream()
                            .map(seg -> new LectureTranscriptionSegment(seg.startTime(), seg.endTime(), seg.text(), seg.slideNumber())).toList();
                    var course = transcription.getLectureUnit().getLecture().getCourse();
                    transcription.setLanguage(course.getLanguage() != null ? course.getLanguage().getShortName() : "en");
                    transcription.setSegments(segments);
                    transcription.setTranscriptionStatus(TranscriptionStatus.COMPLETED);
                }
                catch (JsonProcessingException e) {
                    log.error("Failed to parse transcription result for jobId: {}", jobId, e);
                    transcription.setTranscriptionStatus(TranscriptionStatus.FAILED);
                }
            }
            else {
                log.warn("Transcription job completed without a result for jobId: {}", jobId);
                transcription.setTranscriptionStatus(TranscriptionStatus.FAILED);
            }

            LectureTranscription saved = api.save(transcription);
            processingStateCallbackApi.ifPresent(callback -> callback.handleTranscriptionComplete(saved));
        });
    }
}
