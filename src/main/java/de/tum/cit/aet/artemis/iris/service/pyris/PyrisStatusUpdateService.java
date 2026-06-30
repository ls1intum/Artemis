package de.tum.cit.aet.artemis.iris.service.pyris;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.dto.IrisGlobalSearchAnswerWebsocketDTO;
import de.tum.cit.aet.artemis.iris.service.AutonomousTutorService;
import de.tum.cit.aet.artemis.iris.service.IrisCompetencyGenerationService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.TutorSuggestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.autonomoustutor.PyrisAutonomousTutorPipelineStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.competency.PyrisCompetencyStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisFaqIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisGlobalSearchAnswerStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleInterventionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.AutonomousTutorJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.CompetencyExtractionJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.FaqIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.GlobalSearchAnswerJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.StruggleInterventionJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TrackedSessionBasedPyrisJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TutorSuggestionJob;
import de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisStruggleInterventionService;
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

    private final IrisStruggleInterventionService irisStruggleInterventionService;

    public PyrisStatusUpdateService(PyrisJobService pyrisJobService, IrisChatSessionService irisChatSessionService, IrisCompetencyGenerationService competencyGenerationService,
            IrisTutorSuggestionSessionService irisTutorSuggestionSessionService, AutonomousTutorService autonomousTutorService,
            Optional<ProcessingStateCallbackApi> processingStateCallbackApi, IrisWebsocketService irisWebsocketService,
            IrisStruggleInterventionService irisStruggleInterventionService) {
        this.pyrisJobService = pyrisJobService;
        this.irisChatSessionService = irisChatSessionService;
        this.competencyGenerationService = competencyGenerationService;
        this.irisTutorSuggestionSessionService = irisTutorSuggestionSessionService;
        this.autonomousTutorService = autonomousTutorService;
        this.processingStateCallbackApi = processingStateCallbackApi;
        this.irisWebsocketService = irisWebsocketService;
        this.irisStruggleInterventionService = irisStruggleInterventionService;
    }

    /**
     * Handle a struggle-intervention callback (spec §5.4, A11). Routes by the authoritative {@code job.intent()}.
     * Each mode commits on its OWN terminal frame, structurally mirroring how the {@code decide} path gates on
     * {@code action != null}: {@code confirm_close} commits when {@code resolved != null} and {@code stale_check}
     * when {@code ask != null} ({@code action} stays null on both these modes). A leading IN_PROGRESS frame must NOT
     * fire the handler early - doing so would remove the job, so the real terminal frame would then 403 and the
     * close / stale-check would be silently lost.
     *
     * <p>
     * On the terminal frame the job is removed FIRST (so the trailing duplicate 403s) and the in-flight marker is
     * released only AFTER the handler returns, so a concurrent second trigger cannot race in while the bubble is being
     * materialized + persisted + pushed (spec §11). A non-decision error frame (terminal stages, no terminal field)
     * releases the marker via {@code removeJobIfTerminatedElseUpdate}; an intermediate in-progress frame keeps the job
     * alive (marker held) until the terminal frame arrives.
     *
     * @param job          the struggle-intervention job that is updated
     * @param statusUpdate the status update received
     */
    public void handleStatusUpdate(StruggleInterventionJob job, PyrisStruggleInterventionStatusUpdateDTO statusUpdate) {
        String intent = job.intent();
        if ("confirm_close".equals(intent)) {
            // confirm_close: the terminal frame carries resolved != null (action stays null on this mode). Gate on it
            // exactly as the decide path gates on action != null.
            if (statusUpdate.resolved() != null) {
                pyrisJobService.removeJob(job);   // drop FIRST so trailing duplicate 403s
                try {
                    irisStruggleInterventionService.handleConfirmClose(job, statusUpdate);
                }
                finally {
                    pyrisJobService.releaseStruggleInFlightMarker(job.jobId(), job.userId(), job.exerciseId());
                }
            }
            else if (!statusUpdate.stages().isEmpty() && removeJobIfTerminatedElseUpdate(statusUpdate.stages(), job)) {
                // Error frame (terminal stages, no resolved field): the job left the map, so release the marker now.
                pyrisJobService.releaseStruggleInFlightMarker(job.jobId(), job.userId(), job.exerciseId());
            }
            // else: non-terminal intermediate frame -> job kept alive (updateJob), marker held for the terminal frame.
        }
        else if ("stale_check".equals(intent)) {
            // stale_check: the terminal frame carries ask != null (action stays null on this mode). Same gating as above.
            if (statusUpdate.ask() != null) {
                pyrisJobService.removeJob(job);   // drop FIRST so trailing duplicate 403s
                try {
                    irisStruggleInterventionService.handleStaleCheck(job, statusUpdate);
                }
                finally {
                    pyrisJobService.releaseStruggleInFlightMarker(job.jobId(), job.userId(), job.exerciseId());
                }
            }
            else if (!statusUpdate.stages().isEmpty() && removeJobIfTerminatedElseUpdate(statusUpdate.stages(), job)) {
                // Error frame (terminal stages, no ask field): the job left the map, so release the marker now.
                pyrisJobService.releaseStruggleInFlightMarker(job.jobId(), job.userId(), job.exerciseId());
            }
            // else: non-terminal intermediate frame -> job kept alive (updateJob), marker held for the terminal frame.
        }
        else if (statusUpdate.action() != null) {
            // decide (or legacy null intent): action != null signals the terminal decision callback.
            pyrisJobService.removeJob(job);   // drop the JOB-MAP entry FIRST so the trailing duplicate is rejected (403)...
            try {
                irisStruggleInterventionService.handleDecision(job, statusUpdate);
            }
            finally {
                // ...but free the (userId, exerciseId) in-flight marker only AFTER handleDecision returns —
                // releasing it earlier reopens the re-trigger race (duplicate session/bubble).
                pyrisJobService.releaseStruggleInFlightMarker(job.jobId(), job.userId(), job.exerciseId());
            }
        }
        else if (!statusUpdate.stages().isEmpty() && removeJobIfTerminatedElseUpdate(statusUpdate.stages(), job)) {
            // Non-decision terminal callback (e.g. a Pyris ERROR stage, no action): the job left the map, so
            // release the marker now (token-conditional) rather than waiting for the map-TTL self-heal.
            // The !isEmpty() guard is essential: an empty stages list is vacuously "all terminal", which would
            // otherwise drop the job before the real decision callback arrives and silently lose the intervention.
            pyrisJobService.releaseStruggleInFlightMarker(job.jobId(), job.userId(), job.exerciseId());
        }
    }

    /**
     * Handles the status update of a exercise chat job and forwards it to
     * {@link IrisChatSessionService#handleStatusUpdate(TrackedSessionBasedPyrisJob, PyrisChatStatusUpdateDTO)}
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(ChatJob job, PyrisChatStatusUpdateDTO statusUpdate) {
        var updatedJob = irisChatSessionService.handleStatusUpdate(job, statusUpdate);

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
     * Handles a webhook status update for a global search Iris answer job.
     * <p>
     * Logic (matching the webhook contract):
     * <ul>
     * <li>Thinking callback ({@code stages[0].state == IN_PROGRESS}): sends {@code isThinking=true} to the user via WebSocket.</li>
     * <li>Result callback (all stages terminal): sends {@code isThinking=false} with the final answer (or null) via WebSocket, then removes the job.</li>
     * </ul>
     *
     * @param job          the global search answer job
     * @param statusUpdate the status update payload from Pyris
     */
    public void handleStatusUpdate(GlobalSearchAnswerJob job, PyrisGlobalSearchAnswerStatusUpdateDTO statusUpdate) {
        var stages = statusUpdate.stages();
        boolean hasStages = stages != null && !stages.isEmpty();
        boolean isTerminal = hasStages && stages.stream().map(PyrisStageDTO::state).allMatch(PyrisStageState::isTerminal);
        boolean isThinking = hasStages && stages.getFirst().state() == PyrisStageState.IN_PROGRESS;

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
     * A job is terminated if all stages are in a terminal state.
     * <p>
     *
     * @see PyrisStageState#isTerminal()
     *
     * @param stages the stages of the status update
     * @param job    the job to remove or to update
     * @return {@code true} if the job was terminal and removed, {@code false} if it was kept alive and updated
     */
    private boolean removeJobIfTerminatedElseUpdate(List<PyrisStageDTO> stages, PyrisJob job) {
        var isDone = stages.stream().map(PyrisStageDTO::state).allMatch(PyrisStageState::isTerminal);
        if (isDone) {
            pyrisJobService.removeJob(job);
        }
        else {
            pyrisJobService.updateJob(job);
        }
        return isDone;   // lets the struggle overload release the in-flight marker on a terminal non-decision callback
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
            String rawCode = statusUpdate.errorCode();
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
