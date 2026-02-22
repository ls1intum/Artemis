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
import de.tum.cit.aet.artemis.iris.service.IrisCompetencyGenerationService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.TutorSuggestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.textexercise.PyrisTextExerciseChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.competency.PyrisCompetencyStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisFaqIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.transcription.PyrisTranscriptionResultDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.transcription.PyrisTranscriptionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.CompetencyExtractionJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.CourseChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ExerciseChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.FaqIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TextExerciseChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TrackedSessionBasedPyrisJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TranscriptionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TutorSuggestionJob;
import de.tum.cit.aet.artemis.iris.service.session.IrisCourseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisExerciseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisLectureChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisTextExerciseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisTutorSuggestionSessionService;
import de.tum.cit.aet.artemis.lecture.api.LectureTranscriptionsRepositoryApi;
import de.tum.cit.aet.artemis.lecture.api.ProcessingStateCallbackApi;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;

@Lazy
@Service
@Conditional(IrisEnabled.class)
public class PyrisStatusUpdateService {

    private static final Logger log = LoggerFactory.getLogger(PyrisStatusUpdateService.class);

    private final PyrisJobService pyrisJobService;

    private final ObjectMapper objectMapper;

    private final IrisExerciseChatSessionService irisExerciseChatSessionService;

    private final IrisTextExerciseChatSessionService irisTextExerciseChatSessionService;

    private final IrisCourseChatSessionService courseChatSessionService;

    private final IrisCompetencyGenerationService competencyGenerationService;

    private final IrisLectureChatSessionService irisLectureChatSessionService;

    private final IrisTutorSuggestionSessionService irisTutorSuggestionSessionService;

    private final Optional<ProcessingStateCallbackApi> processingStateCallbackApi;

    private final Optional<LectureTranscriptionsRepositoryApi> lectureTranscriptionsRepositoryApi;

    private final WebsocketMessagingService websocketMessagingService;

    public PyrisStatusUpdateService(PyrisJobService pyrisJobService, IrisExerciseChatSessionService irisExerciseChatSessionService,
            IrisTextExerciseChatSessionService irisTextExerciseChatSessionService, IrisCourseChatSessionService courseChatSessionService,
            IrisCompetencyGenerationService competencyGenerationService, IrisLectureChatSessionService irisLectureChatSessionService,
            IrisTutorSuggestionSessionService irisTutorSuggestionSessionService, Optional<ProcessingStateCallbackApi> processingStateCallbackApi,
            Optional<LectureTranscriptionsRepositoryApi> lectureTranscriptionsRepositoryApi, ObjectMapper objectMapper, WebsocketMessagingService websocketMessagingService) {
        this.pyrisJobService = pyrisJobService;
        this.irisExerciseChatSessionService = irisExerciseChatSessionService;
        this.irisTextExerciseChatSessionService = irisTextExerciseChatSessionService;
        this.courseChatSessionService = courseChatSessionService;
        this.competencyGenerationService = competencyGenerationService;
        this.irisLectureChatSessionService = irisLectureChatSessionService;
        this.irisTutorSuggestionSessionService = irisTutorSuggestionSessionService;
        this.processingStateCallbackApi = processingStateCallbackApi;
        this.lectureTranscriptionsRepositoryApi = lectureTranscriptionsRepositoryApi;
        this.objectMapper = objectMapper;
        this.websocketMessagingService = websocketMessagingService;
    }

    /**
     * Handles the status update of a exercise chat job and forwards it to
     * {@link IrisExerciseChatSessionService#handleStatusUpdate(TrackedSessionBasedPyrisJob, PyrisChatStatusUpdateDTO)}
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(ExerciseChatJob job, PyrisChatStatusUpdateDTO statusUpdate) {
        var updatedJob = irisExerciseChatSessionService.handleStatusUpdate(job, statusUpdate);

        removeJobIfTerminatedElseUpdate(statusUpdate.stages(), updatedJob);
    }

    /**
     * Handles the status update of an exercise chat job and forwards it to
     * {@link IrisTextExerciseChatSessionService#handleStatusUpdate(TextExerciseChatJob, PyrisTextExerciseChatStatusUpdateDTO)}
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(TextExerciseChatJob job, PyrisTextExerciseChatStatusUpdateDTO statusUpdate) {
        var updatedJob = irisTextExerciseChatSessionService.handleStatusUpdate(job, statusUpdate);

        removeJobIfTerminatedElseUpdate(statusUpdate.stages(), updatedJob);
    }

    /**
     * Handles the status update of a course chat job and forwards it to
     * {@link de.tum.cit.aet.artemis.iris.service.session.IrisCourseChatSessionService#handleStatusUpdate(TrackedSessionBasedPyrisJob, PyrisChatStatusUpdateDTO)}
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(CourseChatJob job, PyrisChatStatusUpdateDTO statusUpdate) {
        var updatedJob = courseChatSessionService.handleStatusUpdate(job, statusUpdate);

        removeJobIfTerminatedElseUpdate(statusUpdate.stages(), updatedJob);
    }

    /**
     * Handles the status update of a competency extraction job and forwards it to
     * {@link IrisCompetencyGenerationService#handleStatusUpdate(CompetencyExtractionJob, PyrisCompetencyStatusUpdateDTO)}
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(CompetencyExtractionJob job, PyrisCompetencyStatusUpdateDTO statusUpdate) {
        var updatedJob = competencyGenerationService.handleStatusUpdate(job, statusUpdate);

        removeJobIfTerminatedElseUpdate(statusUpdate.stages(), updatedJob);
    }

    /**
     * Removes the job from the job service if the status update indicates that the job is terminated; updates it to distribute changes otherwise.
     * A job is terminated if all stages are in a terminal state.
     * <p>
     *
     * @see PyrisStageState#isTerminal()
     *
     * @param stages the stages of the status update
     * @param job    the job to remove or to update
     */
    private void removeJobIfTerminatedElseUpdate(List<PyrisStageDTO> stages, PyrisJob job) {
        var isDone = stages.stream().map(PyrisStageDTO::state).allMatch(PyrisStageState::isTerminal);
        if (isDone) {
            pyrisJobService.removeJob(job);
        }
        else {
            pyrisJobService.updateJob(job);
        }
    }

    /**
     * Handles the status update of a lecture ingestion job.
     * Also notifies the lecture content processing service when ingestion completes.
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(LectureIngestionWebhookJob job, PyrisLectureIngestionStatusUpdateDTO statusUpdate) {
        var isDone = statusUpdate.stages().stream().map(PyrisStageDTO::state).allMatch(PyrisStageState::isTerminal);

        if (isDone) {
            pyrisJobService.removeJob(job);

            // Notify the lecture content processing service with token for validation
            boolean success = statusUpdate.stages().stream().map(PyrisStageDTO::state).noneMatch(state -> state == PyrisStageState.ERROR);
            processingStateCallbackApi.ifPresent(api -> api.handleIngestionComplete(job.lectureUnitId(), job.jobId(), success));
        }
        else {
            pyrisJobService.updateJob(job);
        }

        // Notify the frontend so it can refresh the processing status without a manual page reload
        websocketMessagingService.sendMessage("/topic/lectures/" + job.lectureId() + "/ingestion-status", Map.of("lectureUnitId", job.lectureUnitId()));
    }

    /**
     * Handles the status update of a Lecture Chat job.
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(LectureChatJob job, PyrisChatStatusUpdateDTO statusUpdate) {
        var updatedJob = irisLectureChatSessionService.handleStatusUpdate(job, statusUpdate);

        removeJobIfTerminatedElseUpdate(statusUpdate.stages(), updatedJob);
    }

    /**
     * Handles the status update of a FAQ ingestion job.
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(FaqIngestionWebhookJob job, PyrisFaqIngestionStatusUpdateDTO statusUpdate) {
        removeJobIfTerminatedElseUpdate(statusUpdate.stages(), job);
    }

    /**
     * Handles the status update of a tutor suggestion job.
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update received
     */
    public void handleStatusUpdate(TutorSuggestionJob job, TutorSuggestionStatusUpdateDTO statusUpdate) {
        var updatedJob = irisTutorSuggestionSessionService.handleStatusUpdate(job, statusUpdate);

        removeJobIfTerminatedElseUpdate(statusUpdate.stages(), updatedJob);
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
