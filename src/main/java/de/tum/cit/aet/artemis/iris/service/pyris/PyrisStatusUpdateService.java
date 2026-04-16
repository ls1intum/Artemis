package de.tum.cit.aet.artemis.iris.service.pyris;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.AutonomousTutorService;
import de.tum.cit.aet.artemis.iris.service.IrisCompetencyGenerationService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.TutorSuggestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.autonomoustutor.PyrisAutonomousTutorPipelineStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.textexercise.PyrisTextExerciseChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.competency.PyrisCompetencyStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisFaqIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState;
import de.tum.cit.aet.artemis.iris.service.pyris.job.AutonomousTutorJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.CompetencyExtractionJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.CourseChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ExerciseChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.FaqIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TextExerciseChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TrackedSessionBasedPyrisJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TutorSuggestionJob;
import de.tum.cit.aet.artemis.iris.service.session.IrisCourseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisExerciseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisLectureChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisTextExerciseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisTutorSuggestionSessionService;
import de.tum.cit.aet.artemis.lecture.api.ProcessingStateCallbackApi;

@Lazy
@Service
@Conditional(IrisEnabled.class)
public class PyrisStatusUpdateService {

    private static final Logger log = LoggerFactory.getLogger(PyrisStatusUpdateService.class);

    private final PyrisJobService pyrisJobService;

    private final IrisExerciseChatSessionService irisExerciseChatSessionService;

    private final IrisTextExerciseChatSessionService irisTextExerciseChatSessionService;

    private final IrisCourseChatSessionService courseChatSessionService;

    private final IrisCompetencyGenerationService competencyGenerationService;

    private final IrisLectureChatSessionService irisLectureChatSessionService;

    private final IrisTutorSuggestionSessionService irisTutorSuggestionSessionService;

    private final AutonomousTutorService autonomousTutorService;

    private final Optional<ProcessingStateCallbackApi> processingStateCallbackApi;

    public PyrisStatusUpdateService(PyrisJobService pyrisJobService, IrisExerciseChatSessionService irisExerciseChatSessionService,
            IrisTextExerciseChatSessionService irisTextExerciseChatSessionService, IrisCourseChatSessionService courseChatSessionService,
            IrisCompetencyGenerationService competencyGenerationService, IrisLectureChatSessionService irisLectureChatSessionService,
            IrisTutorSuggestionSessionService irisTutorSuggestionSessionService, AutonomousTutorService autonomousTutorService,
            Optional<ProcessingStateCallbackApi> processingStateCallbackApi) {
        this.pyrisJobService = pyrisJobService;
        this.irisExerciseChatSessionService = irisExerciseChatSessionService;
        this.irisTextExerciseChatSessionService = irisTextExerciseChatSessionService;
        this.courseChatSessionService = courseChatSessionService;
        this.competencyGenerationService = competencyGenerationService;
        this.irisLectureChatSessionService = irisLectureChatSessionService;
        this.irisTutorSuggestionSessionService = irisTutorSuggestionSessionService;
        this.autonomousTutorService = autonomousTutorService;
        this.processingStateCallbackApi = processingStateCallbackApi;
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
     * <p>
     * On EVERY callback (not just terminal): passes the {@code result} field to the checkpoint handler.
     * This allows Artemis to save transcription data mid-pipeline and transition TRANSCRIBING → INGESTING.
     * <p>
     * On terminal callback: notifies the processing service that the job completed or failed.
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(LectureIngestionWebhookJob job, PyrisLectureIngestionStatusUpdateDTO statusUpdate) {
        log.debug("[Ingestion] Status update for unitId={}, hasResult={}", job.lectureUnitId(), statusUpdate.result() != null && !statusUpdate.result().isBlank());

        // Process checkpoint data on every callback (transcription results, heartbeats, etc.)
        if (statusUpdate.result() != null && !statusUpdate.result().isBlank()) {
            processingStateCallbackApi.ifPresent(api -> api.handleCheckpointData(job.lectureUnitId(), job.jobId(), statusUpdate.result()));
        }

        var isDone = statusUpdate.stages().stream().map(PyrisStageDTO::state).allMatch(PyrisStageState::isTerminal);

        if (isDone) {
            boolean success = statusUpdate.stages().stream().map(PyrisStageDTO::state).noneMatch(state -> state == PyrisStageState.ERROR);
            String errorCode = success ? null : statusUpdate.errorCode();
            log.info("[Ingestion] Terminal callback for unitId={}, success={}, errorCode={}", job.lectureUnitId(), success, errorCode);
            processingStateCallbackApi.ifPresent(api -> api.handleIngestionComplete(job.lectureUnitId(), job.jobId(), success, errorCode));
            pyrisJobService.removeJob(job);
        }
        else {
            pyrisJobService.updateJob(job);
            // Update lastUpdated on every non-terminal callback so stuck detection
            // can use "time since last callback" instead of "time since phase started"
            processingStateCallbackApi.ifPresent(api -> api.handleHeartbeat(job.lectureUnitId(), job.jobId()));
        }
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
     * Handles the status update of an autonomous tutor job.
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update received
     */
    public void handleStatusUpdate(AutonomousTutorJob job, PyrisAutonomousTutorPipelineStatusUpdateDTO statusUpdate) {
        autonomousTutorService.handleStatusUpdate(job, statusUpdate);

        removeJobIfTerminatedElseUpdate(statusUpdate.stages(), job);
    }

}
