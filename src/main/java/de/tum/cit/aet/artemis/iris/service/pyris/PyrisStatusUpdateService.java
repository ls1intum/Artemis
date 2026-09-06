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
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
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
import de.tum.cit.aet.artemis.iris.service.session.IrisStruggleTriggerService;
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

    private final IrisStruggleTriggerService irisStruggleTriggerService;

    public PyrisStatusUpdateService(PyrisJobService pyrisJobService, IrisChatSessionService irisChatSessionService, IrisCompetencyGenerationService competencyGenerationService,
            IrisTutorSuggestionSessionService irisTutorSuggestionSessionService, AutonomousTutorService autonomousTutorService,
            Optional<ProcessingStateCallbackApi> processingStateCallbackApi, IrisWebsocketService irisWebsocketService,
            IrisStruggleInterventionService irisStruggleInterventionService, IrisStruggleTriggerService irisStruggleTriggerService) {
        this.pyrisJobService = pyrisJobService;
        this.irisChatSessionService = irisChatSessionService;
        this.competencyGenerationService = competencyGenerationService;
        this.irisTutorSuggestionSessionService = irisTutorSuggestionSessionService;
        this.autonomousTutorService = autonomousTutorService;
        this.processingStateCallbackApi = processingStateCallbackApi;
        this.irisWebsocketService = irisWebsocketService;
        this.irisStruggleInterventionService = irisStruggleInterventionService;
        this.irisStruggleTriggerService = irisStruggleTriggerService;
    }

    /**
     * Handle a struggle-intervention callback. Routes by the authoritative {@code job.intent()}.
     * Each mode commits on its OWN terminal frame, structurally mirroring how the {@code decide} path gates on
     * {@code action != null}: {@code confirm_close} commits when {@code resolved != null} ({@code action} stays
     * null on that mode). A leading IN_PROGRESS frame must NOT
     * fire the handler early - doing so would remove the job, so the real terminal frame would then 403 and the
     * close / stale-check would be silently lost.
     *
     * <p>
     * On the terminal frame the job is removed FIRST (so the trailing duplicate 403s) and the in-flight marker is
     * released only AFTER the handler returns, so a concurrent second trigger cannot race in while the bubble is being
     * materialized + persisted + pushed. A non-decision error frame (terminal stages, no terminal field)
     * releases the marker via {@code removeJobIfTerminatedElseUpdate}; an intermediate in-progress frame keeps the job
     * alive (marker held) until the terminal frame arrives.
     *
     * @param job          the struggle-intervention job that is updated
     * @param statusUpdate the status update received
     */
    public void handleStatusUpdate(StruggleInterventionJob job, PyrisStruggleInterventionStatusUpdateDTO statusUpdate) {
        // Serialize per job id and re-read the map entry under the lock. The resource authenticates the callback by
        // reading the job BEFORE this method, so two genuinely concurrent callbacks can both hold the same job object
        // and would otherwise both remove it and both run the handler, persisting and pushing the decision twice.
        // Re-reading here is what actually claims the callback; locking around the stale argument would not.
        pyrisJobService.runWithJobLock(job.jobId(), () -> {
            if (!(pyrisJobService.getJob(job.jobId()) instanceof StruggleInterventionJob claimed)) {
                // Another callback already claimed and removed this job (or it expired). Dropping is correct: the
                // winner owns the terminal side effects and the marker release.
                log.debug("Skipping struggle status update for job {} because the job is no longer in the map", job.jobId());
                return null;
            }
            handleClaimedStatusUpdate(claimed, statusUpdate);
            return null;
        });
    }

    /**
     * The body of {@link #handleStatusUpdate}, running under the job lock on a job re-read from the map.
     *
     * Records the callback's token usage first, so the pipeline's LLM spend is accounted for on every frame,
     * including the intermediate and error frames that never reach a decision handler.
     *
     * @param job          the struggle-intervention job, freshly read under the lock
     * @param statusUpdate the status update received
     */
    private void handleClaimedStatusUpdate(StruggleInterventionJob job, PyrisStruggleInterventionStatusUpdateDTO statusUpdate) {
        // Before routing, so a run that reports spend on an intermediate or failing frame is accounted for too, and
        // every frame is counted exactly once regardless of which branch below claims it.
        irisStruggleInterventionService.recordTokenUsage(job, statusUpdate);
        boolean close = "confirm_close".equals(job.intent());
        // Each intent recognises its terminal frame by the field its own contract fills: resolved for confirm_close
        // (action stays null there), action for decide and for a legacy null intent. Everything the terminal frame
        // then triggers - claim, handle, complete on failure, release - is the same for both, so it is written once.
        if (close ? statusUpdate.resolved() != null : statusUpdate.action() != null) {
            pyrisJobService.removeJob(job);   // drop the JOB-MAP entry FIRST so the trailing duplicate is rejected (403)...
            // The marker still carries whatever is left of the TTL its last keep-alive gave it, and everything below
            // - session materialization, the persist, the push - runs while it drains. A run that reaches its
            // terminal frame late enough would hand a second trigger the slot mid-handler, which is the duplicate
            // session and bubble this marker exists to prevent. Re-stamp it for the handler's own runtime.
            pyrisJobService.refreshStruggleInFlightMarker(job.jobId(), job.userId(), job.exerciseId());
            try {
                if (close) {
                    irisStruggleInterventionService.handleConfirmClose(job, statusUpdate);
                }
                else {
                    irisStruggleInterventionService.handleDecision(job, statusUpdate);
                }
            }
            catch (Exception e) {
                // Both handlers emit their own completion on every deliberate early return, but an unexpected failure
                // (e.g. a DataAccessException while persisting the closing message or recording the ambient offer)
                // would otherwise escape after the job was already removed, leaving the client's in-flight request to
                // hang until its own timeout. Complete it here, before releasing the marker.
                log.error("Handling the terminal {} frame failed for struggle job {} exercise {} user {}; emitting terminal completion", close ? "confirm_close" : "decide",
                        job.jobId(), job.exerciseId(), job.userId(), e);
                irisStruggleTriggerService.emitTerminalCompletion(job);
            }
            finally {
                // ...but free the (userId, exerciseId) in-flight marker only AFTER the handler returns —
                // releasing it earlier reopens the re-trigger race (duplicate session/bubble).
                pyrisJobService.releaseStruggleInFlightMarker(job.jobId(), job.userId(), job.exerciseId());
            }
        }
        else if (statusUpdate.runState() != null && removeJobIfTerminatedElseUpdate(statusUpdate.runState(), job)) {
            // Non-decision terminal callback (e.g. a Pyris FAILED run, no action and no resolved): the job left the
            // map, so release the marker now (token-conditional) rather than waiting for the map-TTL self-heal.
            // The null guard is essential and deliberately does NOT reuse resolveRunState (which maps a missing
            // run state to FAILED): a frame without a run state must not drop the job before the real decision
            // callback arrives, which would silently lose the intervention.
            // The run produced no decision, so complete the client's in-flight request here; every other drop path in
            // the handlers already emits its completion frame for exactly this reason.
            irisStruggleTriggerService.emitTerminalCompletion(job);
            pyrisJobService.releaseStruggleInFlightMarker(job.jobId(), job.userId(), job.exerciseId());
        }
        // else: non-terminal intermediate frame -> job kept alive (updateJob), marker held for the terminal frame.
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
     * @return {@code true} if the job was terminal and removed, {@code false} if it was kept alive and updated
     */
    private boolean removeJobIfTerminatedElseUpdate(PyrisRunState runState, PyrisJob job) {
        var isDone = runState.isTerminal();
        if (isDone) {
            pyrisJobService.removeJob(job);
        }
        else {
            pyrisJobService.updateJob(job);
            if (job instanceof StruggleInterventionJob struggleJob) {
                // The job entry just got a fresh TTL; the in-flight reservation would otherwise keep its original
                // one and expire under a long-running run, letting a second trigger reserve the same pair while
                // this one is still going. Keep the two lifetimes together.
                pyrisJobService.refreshStruggleInFlightMarker(struggleJob.jobId(), struggleJob.userId(), struggleJob.exerciseId());
            }
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
