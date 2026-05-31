package de.tum.cit.aet.artemis.iris.service.pyris;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
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
import de.tum.cit.aet.artemis.iris.service.pyris.job.AutonomousTutorJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.CompetencyExtractionJob;
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

    private static final com.fasterxml.jackson.databind.ObjectMapper objectMapper = JsonObjectMapper.get();

    private static final TypeReference<List<Integer>> INTEGER_LIST_TYPE = new TypeReference<>() {
    };

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
     * On non-terminal callbacks: passes the {@code result} field to the checkpoint handler if present.
     * This allows Artemis to save transcription data mid-pipeline and transition TRANSCRIBING → INGESTING.
     * <p>
     * On terminal callback: extracts slide page numbers from {@code result} and notifies the processing service
     * that the job completed or failed. The {@code result} field is NOT treated as checkpoint data on terminal
     * callbacks to avoid duplicate processing.
     *
     * @param job          the job that is updated
     * @param statusUpdate the status update
     */
    public void handleStatusUpdate(LectureIngestionWebhookJob job, PyrisLectureIngestionStatusUpdateDTO statusUpdate) {
        log.debug("[Ingestion] Status update for unitId={}, hasResult={}", job.lectureUnitId(), statusUpdate.result() != null && !statusUpdate.result().isBlank());

        var isDone = statusUpdate.stages().stream().map(PyrisStageDTO::state).allMatch(PyrisStageState::isTerminal);

        // Only treat result as checkpoint data on non-terminal callbacks
        // On terminal callbacks, result contains slide page numbers extracted below
        if (!isDone && statusUpdate.result() != null && !statusUpdate.result().isBlank()) {
            processingStateCallbackApi.ifPresent(api -> api.handleCheckpointData(job.lectureUnitId(), job.jobId(), statusUpdate.result()));
        }

        if (isDone) {
            boolean success = statusUpdate.stages().stream().map(PyrisStageDTO::state).noneMatch(state -> state == PyrisStageState.ERROR);
            String rawCode = statusUpdate.errorCode();
            String errorCode = success ? null : (rawCode != null && !rawCode.isBlank() ? rawCode : null);
            List<Integer> slidePageNumbers = success ? extractSlidePageNumbers(statusUpdate.result()) : null;

            log.info("[Ingestion] Terminal callback for unitId={}, success={}, errorCode={}", job.lectureUnitId(), success, errorCode);
            processingStateCallbackApi.ifPresent(api -> api.handleIngestionComplete(job.lectureUnitId(), job.jobId(), success, errorCode, slidePageNumbers));
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

    /**
     * Extracts the slide page numbers list from the PyRIS result JSON.
     *
     * @param resultJson the result JSON from PyRIS containing a "slidePageNumbers" array
     * @return the list of page numbers as provided by PyRIS, or null if not present or malformed
     */
    private List<Integer> extractSlidePageNumbers(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return null;
        }

        try {
            var jsonNode = objectMapper.readTree(resultJson);
            var listNode = jsonNode.get("slidePageNumbers");
            if (listNode == null || !listNode.isArray()) {
                return null;
            }
            List<Integer> extracted = objectMapper.convertValue(listNode, INTEGER_LIST_TYPE);
            return extracted.isEmpty() ? null : extracted;
        }
        catch (Exception e) {
            log.debug("Failed to extract slidePageNumbers from result JSON", e);
            return null;
        }
    }

}
