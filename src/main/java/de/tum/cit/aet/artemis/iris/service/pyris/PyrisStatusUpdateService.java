package de.tum.cit.aet.artemis.iris.service.pyris;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.dto.IrisCourseMemoryStatusDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisGlobalSearchAnswerWebsocketDTO;
import de.tum.cit.aet.artemis.iris.service.AutonomousTutorService;
import de.tum.cit.aet.artemis.iris.service.CourseMemoryIngestionService;
import de.tum.cit.aet.artemis.iris.service.IrisCompetencyGenerationService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.TutorSuggestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.autonomoustutor.PyrisAutonomousTutorPipelineStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.competency.PyrisCompetencyStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook.PyrisCourseMemoryIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisFaqIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisGlobalSearchAnswerStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.job.AutonomousTutorJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.CompetencyExtractionJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.CourseMemoryIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.FaqIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.GlobalSearchAnswerJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TrackedSessionBasedPyrisJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TutorSuggestionJob;
import de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisTutorSuggestionSessionService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisWebsocketService;
import de.tum.cit.aet.artemis.lecture.api.ProcessingStateCallbackApi;

@Lazy
@Service
@Conditional(IrisEnabled.class)
public class PyrisStatusUpdateService {

    private static final Logger log = LoggerFactory.getLogger(PyrisStatusUpdateService.class);

    private static final String GLOBAL_SEARCH_ANSWER_WEBSOCKET_TOPIC = "global-search-answer";

    private final PyrisJobService pyrisJobService;

    private final IrisChatSessionService irisChatSessionService;

    private final IrisCompetencyGenerationService competencyGenerationService;

    private final IrisTutorSuggestionSessionService irisTutorSuggestionSessionService;

    private final AutonomousTutorService autonomousTutorService;

    private final Optional<ProcessingStateCallbackApi> processingStateCallbackApi;

    private final IrisWebsocketService irisWebsocketService;

    public PyrisStatusUpdateService(PyrisJobService pyrisJobService, IrisChatSessionService irisChatSessionService, IrisCompetencyGenerationService competencyGenerationService,
            IrisTutorSuggestionSessionService irisTutorSuggestionSessionService, AutonomousTutorService autonomousTutorService,
            Optional<ProcessingStateCallbackApi> processingStateCallbackApi, IrisWebsocketService irisWebsocketService) {
        this.pyrisJobService = pyrisJobService;
        this.irisChatSessionService = irisChatSessionService;
        this.competencyGenerationService = competencyGenerationService;
        this.irisTutorSuggestionSessionService = irisTutorSuggestionSessionService;
        this.autonomousTutorService = autonomousTutorService;
        this.processingStateCallbackApi = processingStateCallbackApi;
        this.irisWebsocketService = irisWebsocketService;
    }

    /**
     * Handles the status update of a exercise chat job and forwards it to
     * {@link IrisChatSessionService#handleStatusUpdate(TrackedSessionBasedPyrisJob, PyrisChatStatusUpdateDTO)}
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(ChatJob job, PyrisChatStatusUpdateDTO statusUpdate) {
        var runState = resolveRunState(statusUpdate.runState(), job);
        var normalizedStatusUpdate = withRunState(statusUpdate, runState);
        if (statusUpdate.partialResult() != null && runState == PyrisRunState.RUNNING) {
            irisChatSessionService.handlePartialStatusUpdate(job, statusUpdate);
            return;
        }
        if (statusUpdate.partialResult() != null) {
            removeJobIfTerminatedElseUpdate(runState, job);
            return;
        }

        var updatedJob = irisChatSessionService.handleStatusUpdate(job, normalizedStatusUpdate);

        removeJobIfTerminatedElseUpdate(runState, updatedJob);
    }

    /**
     * Handles the status update of a competency extraction job and forwards it to
     * {@link IrisCompetencyGenerationService#handleStatusUpdate(CompetencyExtractionJob, PyrisCompetencyStatusUpdateDTO)}
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(CompetencyExtractionJob job, PyrisCompetencyStatusUpdateDTO statusUpdate) {
        var runState = resolveRunState(statusUpdate.runState(), job);
        var updatedJob = competencyGenerationService.handleStatusUpdate(job, withRunState(statusUpdate, runState));

        removeJobIfTerminatedElseUpdate(runState, updatedJob);
    }

    /**
     * Handles a webhook status update for a global search Iris answer job.
     * <p>
     * Logic (matching the webhook contract):
     * <ul>
     * <li>Thinking callback ({@code runState == RUNNING}): sends {@code isThinking=true} to the user via WebSocket.</li>
     * <li>Result callback (terminal {@code runState}): sends {@code isThinking=false} with the final answer (or null) via WebSocket, then removes the job.</li>
     * </ul>
     *
     * @param job          the global search answer job
     * @param statusUpdate the status update payload from Pyris
     */
    public void handleStatusUpdate(GlobalSearchAnswerJob job, PyrisGlobalSearchAnswerStatusUpdateDTO statusUpdate) {
        var runState = resolveRunState(statusUpdate.runState(), job);
        boolean isTerminal = runState.isTerminal();
        boolean isThinking = runState == PyrisRunState.RUNNING;

        if (isThinking) {
            irisWebsocketService.send(job.userLogin(), GLOBAL_SEARCH_ANSWER_WEBSOCKET_TOPIC, new IrisGlobalSearchAnswerWebsocketDTO(job.jobId(), true, null, null));
            pyrisJobService.updateJob(job);
        }
        else if (isTerminal) {
            irisWebsocketService.send(job.userLogin(), GLOBAL_SEARCH_ANSWER_WEBSOCKET_TOPIC,
                    new IrisGlobalSearchAnswerWebsocketDTO(job.jobId(), false, statusUpdate.answer(), statusUpdate.sources()));
            pyrisJobService.removeJob(job);
        }
        else {
            pyrisJobService.updateJob(job);
        }
    }

    /**
     * Removes the job from the job service if the status update indicates that the job is terminated; updates it to distribute changes otherwise.
     * A job is terminated if the Pyris run state is terminal.
     * <p>
     *
     * @see PyrisRunState#isTerminal()
     *
     * @param runState the run state of the status update
     * @param job      the job to remove or to update
     */
    private void removeJobIfTerminatedElseUpdate(PyrisRunState runState, PyrisJob job) {
        if (runState.isTerminal()) {
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
        var runState = resolveRunState(statusUpdate.runState(), job);

        // Process checkpoint data on every callback (transcription results, heartbeats, etc.)
        if (statusUpdate.result() != null && !statusUpdate.result().isBlank()) {
            processingStateCallbackApi.ifPresent(api -> api.handleCheckpointData(job.lectureUnitId(), job.jobId(), statusUpdate.result()));
        }

        var isDone = runState.isTerminal();

        if (isDone) {
            boolean success = runState == PyrisRunState.FINISHED;
            String rawCode = statusUpdate.error() != null ? statusUpdate.error().code() : null;
            String errorCode = success ? null : (rawCode != null && !rawCode.isBlank() ? rawCode : null);
            List<Integer> displayPageNumbers = success ? statusUpdate.displayPageNumbers() : null;
            log.info("[Ingestion] Terminal callback for unitId={}, success={}, errorCode={}", job.lectureUnitId(), success, errorCode);
            processingStateCallbackApi.ifPresent(api -> api.handleIngestionComplete(job.lectureUnitId(), job.jobId(), success, errorCode, displayPageNumbers));
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
     * Handles the status update of a FAQ ingestion job.
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(FaqIngestionWebhookJob job, PyrisFaqIngestionStatusUpdateDTO statusUpdate) {
        removeJobIfTerminatedElseUpdate(resolveRunState(statusUpdate.runState(), job), job);
    }

    /**
     * Handles the status update of a Course Memory ingestion or deletion job. The entry is stored on
     * Pyris regardless of the callback, so beyond the job lifecycle Artemis only reports the outcome
     * back to whoever triggered the run.
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(CourseMemoryIngestionWebhookJob job, PyrisCourseMemoryIngestionStatusUpdateDTO statusUpdate) {
        var runState = resolveRunState(statusUpdate.runState(), job);
        // Only terminal states surface to the user: Pyris emits several RUNNING updates per run and
        // each would raise its own toast.
        if (runState.isTerminal() && job.userLogin() != null) {
            var status = runState == PyrisRunState.FINISHED ? IrisCourseMemoryStatusDTO.completed(job.operation(), job.courseId(), job.postId())
                    : IrisCourseMemoryStatusDTO.failed(job.operation(), job.courseId(), job.postId(), statusUpdate.error() != null ? statusUpdate.error().message() : null);
            irisWebsocketService.send(job.userLogin(), CourseMemoryIngestionService.COURSE_MEMORY_TOPIC_PREFIX + job.courseId(), status);
        }
        removeJobIfTerminatedElseUpdate(runState, job);
    }

    /**
     * Handles the status update of a tutor suggestion job.
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update received
     */
    public void handleStatusUpdate(TutorSuggestionJob job, TutorSuggestionStatusUpdateDTO statusUpdate) {
        var runState = resolveRunState(statusUpdate.runState(), job);
        var updatedJob = irisTutorSuggestionSessionService.handleStatusUpdate(job, withRunState(statusUpdate, runState));

        removeJobIfTerminatedElseUpdate(runState, updatedJob);
    }

    /**
     * Handles the status update of an autonomous tutor job.
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update received
     */
    public void handleStatusUpdate(AutonomousTutorJob job, PyrisAutonomousTutorPipelineStatusUpdateDTO statusUpdate) {
        var runState = resolveRunState(statusUpdate.runState(), job);
        autonomousTutorService.handleStatusUpdate(job, withRunState(statusUpdate, runState));

        removeJobIfTerminatedElseUpdate(runState, job);
    }

    private PyrisRunState resolveRunState(PyrisRunState runState, PyrisJob job) {
        if (runState != null) {
            return runState;
        }
        log.warn("Received Pyris status update without runState for job {} of type {}; treating it as terminal failure", job.jobId(), job.getClass().getSimpleName());
        return PyrisRunState.FAILED;
    }

    private PyrisChatStatusUpdateDTO withRunState(PyrisChatStatusUpdateDTO statusUpdate, PyrisRunState runState) {
        if (statusUpdate.runState() == runState) {
            return statusUpdate;
        }
        return new PyrisChatStatusUpdateDTO(statusUpdate.result(), runState, statusUpdate.error(), statusUpdate.sessionTitle(), statusUpdate.suggestions(), statusUpdate.tokens(),
                statusUpdate.accessedMemories(), statusUpdate.createdMemories(), statusUpdate.partialResult(), statusUpdate.partialSeq(), statusUpdate.activities(),
                statusUpdate.activitySeq(), statusUpdate.finalResult());
    }

    private PyrisCompetencyStatusUpdateDTO withRunState(PyrisCompetencyStatusUpdateDTO statusUpdate, PyrisRunState runState) {
        if (statusUpdate.runState() == runState) {
            return statusUpdate;
        }
        return new PyrisCompetencyStatusUpdateDTO(runState, statusUpdate.error(), statusUpdate.result(), statusUpdate.tokens());
    }

    private TutorSuggestionStatusUpdateDTO withRunState(TutorSuggestionStatusUpdateDTO statusUpdate, PyrisRunState runState) {
        if (statusUpdate.runState() == runState) {
            return statusUpdate;
        }
        return new TutorSuggestionStatusUpdateDTO(statusUpdate.artifact(), statusUpdate.result(), runState, statusUpdate.error(), statusUpdate.tokens());
    }

    private PyrisAutonomousTutorPipelineStatusUpdateDTO withRunState(PyrisAutonomousTutorPipelineStatusUpdateDTO statusUpdate, PyrisRunState runState) {
        if (statusUpdate.runState() == runState) {
            return statusUpdate;
        }
        return new PyrisAutonomousTutorPipelineStatusUpdateDTO(statusUpdate.result(), statusUpdate.shouldPostDirectly(), statusUpdate.confidence(), runState, statusUpdate.error(),
                statusUpdate.tokens());
    }

}
