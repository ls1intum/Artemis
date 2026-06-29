package de.tum.cit.aet.artemis.atlas.service;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.config.AtlasOrchestratorProperties;
import de.tum.cit.aet.artemis.atlas.dto.AppliedActionDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyIndexDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyIndexResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO;
import de.tum.cit.aet.artemis.atlas.dto.ExtractedContentDTO;
import de.tum.cit.aet.artemis.atlas.service.ContentChangeAccumulatorService.BatchClaim;
import de.tum.cit.aet.artemis.atlas.service.OrchestratorToolContextKeys.AppliedActionsBuffer;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.localci.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.localci.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * Entry point for autonomous competency management runs.
 * <p>
 * {@link #run(long)} drives a tool-calling LLM loop through the shared {@link AtlasAgentDelegationService}
 * harness: the model is given the exercise as anchor text and can call the orchestrator read/planning
 * tools ({@link OrchestratorReadToolsService}, {@link OrchestratorPlanningToolsService}) to inspect
 * course state and the five write tools ({@code createCompetency}, {@code editCompetency},
 * {@code assignExerciseToCompetency}, {@code unassignExerciseFromCompetency}, {@code deleteCompetency},
 * split across {@link CreatorToolsService} / {@link EditorToolsService} / {@link AssignerToolsService})
 * to mutate it. Every successful mutation is appended to a per-run applied-actions list held in the
 * Spring AI {@code ToolContext}.
 * <p>
 * Course context is injected via {@code ToolContext} (see {@link OrchestratorToolContextKeys}) so the
 * LLM cannot forge the course id through tool arguments.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Service
public class CompetencyOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(CompetencyOrchestrationService.class);

    private static final String EXECUTE_PROMPT_PATH = "prompts/atlas/orchestrator_execute_prompt.st";

    private static final String RUN_MAP_NAME = "atlas-orchestrator-runs";

    /** Token-usage pipeline id for one orchestrator LLM round. */
    private static final String ORCHESTRATION_PIPELINE_ID = "ATLAS_ORCHESTRATION";

    /** Stale-claim lease: a run claim older than this is reclaimed, so a crashed node can't wedge a course in IN_PROGRESS (Redis/Local maps have no TTL). */
    private static final Duration RUN_LEASE = Duration.ofMinutes(30);

    /** Length caps on instructor-controlled strings to bound prompt size and injection surface. */
    private static final int EXERCISE_TITLE_MAX = 200;

    private static final int PROBLEM_STATEMENT_MAX = 8_000;

    private static final int COMPETENCY_TITLE_MAX = 200;

    private static final int LECTURE_UNIT_NAME_MAX = 200;

    private static final int TYPE_LABEL_MAX = 50;

    private static final String TRUNCATION_MARKER = " …[truncated]";

    /** Fence delimiters for untrusted data in {@code orchestrator_execute_prompt.st}; literal occurrences in user content are neutralized in {@link #sanitizeForPrompt}. */
    private static final String USER_DATA_BEGIN = "<<<USER_DATA>>>";

    private static final String USER_DATA_END = "<<<END_USER_DATA>>>";

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ContentExtractionService contentExtractionService;

    private final OrchestratorPlanningToolsService orchestratorPlanningToolsService;

    private final AtlasPromptTemplateService templateService;

    private final AtlasAgentDelegationService delegationService;

    @Nullable
    private final ChatClient chatClient;

    private final ToolCallbackProvider orchestratorReadToolCallbackProvider;

    private final ToolCallbackProvider orchestratorPlanningToolCallbackProvider;

    private final ToolCallbackProvider creatorToolCallbackProvider;

    private final ToolCallbackProvider editorToolCallbackProvider;

    private final ToolCallbackProvider assignerToolCallbackProvider;

    private final String deploymentName;

    private final double temperature;

    private final String reasoningEffort;

    private final Optional<DistributedDataProvider> distributedDataProvider;

    private final ContentChangeAccumulatorService contentChangeAccumulatorService;

    private final LLMTokenUsageService llmTokenUsageService;

    private final UserRepository userRepository;

    private volatile DistributedMap<Long, RunInfo> runMap;

    public CompetencyOrchestrationService(ProgrammingExerciseRepository programmingExerciseRepository, ContentExtractionService contentExtractionService,
            OrchestratorPlanningToolsService orchestratorPlanningToolsService, AtlasPromptTemplateService templateService, AtlasAgentDelegationService delegationService,
            @Nullable ChatClient chatClient, @Qualifier("orchestratorReadToolCallbackProvider") ToolCallbackProvider orchestratorReadToolCallbackProvider,
            @Qualifier("orchestratorPlanningToolCallbackProvider") ToolCallbackProvider orchestratorPlanningToolCallbackProvider,
            @Qualifier("creatorToolCallbackProvider") ToolCallbackProvider creatorToolCallbackProvider,
            @Qualifier("editorToolCallbackProvider") ToolCallbackProvider editorToolCallbackProvider,
            @Qualifier("assignerToolCallbackProvider") ToolCallbackProvider assignerToolCallbackProvider, Optional<DistributedDataProvider> distributedDataProvider,
            AtlasOrchestratorProperties properties,
            ContentChangeAccumulatorService contentChangeAccumulatorService, LLMTokenUsageService llmTokenUsageService, UserRepository userRepository) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.contentExtractionService = contentExtractionService;
        this.orchestratorPlanningToolsService = orchestratorPlanningToolsService;
        this.templateService = templateService;
        this.delegationService = delegationService;
        this.chatClient = chatClient;
        this.orchestratorReadToolCallbackProvider = orchestratorReadToolCallbackProvider;
        this.orchestratorPlanningToolCallbackProvider = orchestratorPlanningToolCallbackProvider;
        this.creatorToolCallbackProvider = creatorToolCallbackProvider;
        this.editorToolCallbackProvider = editorToolCallbackProvider;
        this.assignerToolCallbackProvider = assignerToolCallbackProvider;
        this.deploymentName = properties.model();
        this.temperature = properties.temperature();
        this.reasoningEffort = properties.reasoningEffort();
        this.distributedDataProvider = distributedDataProvider;
        this.contentChangeAccumulatorService = contentChangeAccumulatorService;
        this.llmTokenUsageService = llmTokenUsageService;
        this.userRepository = userRepository;
    }

    /** Per-course IN_PROGRESS guard map, resolved lazily (see {@link #resolveRunMap}). */
    private DistributedMap<Long, RunInfo> runMap() {
        DistributedMap<Long, RunInfo> resolved = runMap;
        if (resolved == null) {
            synchronized (this) {
                resolved = runMap;
                if (resolved == null) {
                    resolved = resolveRunMap();
                    runMap = resolved;
                }
            }
        }
        return resolved;
    }

    private DistributedMap<Long, RunInfo> resolveRunMap() {
        return distributedDataProvider
                .orElseThrow(() -> new IllegalStateException("Atlas auto-orchestration requires a clustered DistributedDataProvider (localci/buildagent profile active)."))
                .getMap(RUN_MAP_NAME);
    }

    /** Claim the per-course run lock: returns {@code null} if acquired, else the active (non-stale) {@link RunInfo}. Reclaims {@link #RUN_LEASE}-stale entries. */
    @Nullable
    private RunInfo claimRun(long courseId, RunInfo claim) {
        DistributedMap<Long, RunInfo> currentMap = runMap();
        currentMap.lock(courseId);
        try {
            RunInfo existing = currentMap.get(courseId);
            if (existing != null && !isStale(existing, claim.startedAt())) {
                return existing;
            }
            currentMap.put(courseId, claim);
            return null;
        }
        finally {
            currentMap.unlock(courseId);
        }
    }

    /** Stale once {@code startedAt} is null or older than {@link #RUN_LEASE} before {@code now} (cross-node clocks; 30 min absorbs skew + run time). */
    private static boolean isStale(RunInfo existing, @Nullable Instant now) {
        if (existing.startedAt() == null) {
            return true;
        }
        return now != null && existing.startedAt().isBefore(now.minus(RUN_LEASE));
    }

    /**
     * Release the per-course run lock, but only if it still holds {@code claim} — a TTL-evicted entry
     * replaced by another claim is left untouched (compare-and-remove).
     * <p>
     * Best-effort: this runs in the callers' {@code finally} blocks after {@code orchestrateBatch} /
     * {@code orchestrateExercise} may already have committed competency mutations. A distributed-map
     * failure here (e.g. {@code HazelcastInstanceNotActiveException} during member shutdown or
     * partition migration) must not propagate, or it would clobber the already-computed result and —
     * for the scheduler — be mistaken for a safe pre-mutation failure that triggers a re-run of an
     * already-applied batch. A failed release just leaves the entry to expire via {@link #RUN_LEASE}.
     */
    private void releaseRun(long courseId, RunInfo claim) {
        try {
            DistributedMap<Long, RunInfo> currentMap = runMap();
            currentMap.lock(courseId);
            try {
                if (claim.equals(currentMap.get(courseId))) {
                    currentMap.remove(courseId);
                }
            }
            finally {
                currentMap.unlock(courseId);
            }
        }
        catch (Exception ex) {
            log.warn("Atlas orchestrator failed to release run lock for course {} (run {}); lease will self-expire: {}", courseId, claim.runId(), ex.getMessage(), ex);
        }
    }

    /**
     * Run one orchestration pass for the given programming exercise. The orchestrator plans
     * internally and executes its plan by calling write tools — each tool call mutates state
     * immediately and appends to the applied-actions list returned in the result.
     *
     * @param exerciseId the programming exercise to orchestrate competencies for
     * @return one of:
     *         <ul>
     *         <li>{@link CompetencyOrchestrationResultDTO.Status#SUCCESS} with the LLM's summary message and the applied actions;</li>
     *         <li>{@link CompetencyOrchestrationResultDTO.Status#PARTIAL} when the LLM threw after committing at least one action — the partial audit trail is included so the
     *         caller can review/revert;</li>
     *         <li>{@link CompetencyOrchestrationResultDTO.Status#FAILED} with {@link CompetencyOrchestrationResultDTO.FailureReason#LLM_ERROR} if the call failed before any action
     *         committed, or {@link CompetencyOrchestrationResultDTO.FailureReason#NO_CHAT_CLIENT} if no ChatClient is configured;</li>
     *         <li>{@link CompetencyOrchestrationResultDTO.Status#IN_PROGRESS} if a run is already active for the same course.</li>
     *         </ul>
     */
    public CompetencyOrchestrationResultDTO run(long exerciseId) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        CompetencyOrchestrationResultDTO precheck = precheckExercise(exercise);
        if (precheck != null) {
            return precheck;
        }
        long courseId = exercise.getCourseViaExerciseGroupOrCourseMember().getId();

        RunInfo claim = new RunInfo(UUID.randomUUID().toString(), exerciseId, Instant.now());
        RunInfo existing = claimRun(courseId, claim);
        if (existing != null) {
            log.info("Atlas orchestrator rejected for exercise {} (course {}): run {} already in progress for exercise {}", exerciseId, courseId, existing.runId(),
                    existing.exerciseId());
            return CompetencyOrchestrationResultDTO.inProgress("Another Atlas orchestrator run is already in progress for this course. Please wait for it to finish.");
        }
        try {
            return orchestrateExercise(exercise, courseId);
        }
        finally {
            releaseRun(courseId, claim);
        }
    }

    /**
     * Runs the automatic pipeline over a whole accumulated batch in a single orchestrator
     * invocation: all changed exercises are rendered into one EXERCISE CHANGE BATCH and reasoned
     * over in one LLM call, rather than one call per exercise. Exam and unknown exercises, plus any
     * whose owning course does not match {@code courseId}, are dropped silently. Holds the per-course
     * {@link #runMap} claim once for the whole batch — a concurrent manual run or scheduled tick
     * observes {@link CompetencyOrchestrationResultDTO.Status#IN_PROGRESS}.
     *
     * @param courseId    the course whose buffered batch is being drained
     * @param exerciseIds programming-exercise ids in the batch
     * @return the single batch result; {@code SUCCESS} when the run completed, {@code NO_OP} when no
     *         claimed exercise was applicable (so nothing was processed), {@code IN_PROGRESS} when
     *         another run holds the course lock
     */
    public CompetencyOrchestrationResultDTO runBatch(long courseId, Set<Long> exerciseIds) {
        if (chatClient == null) {
            return CompetencyOrchestrationResultDTO.failed("Atlas chat model is not configured.", CompetencyOrchestrationResultDTO.FailureReason.NO_CHAT_CLIENT);
        }
        List<ProgrammingExercise> exercises = resolveBatchExercises(courseId, exerciseIds);
        if (exercises.isEmpty()) {
            return CompetencyOrchestrationResultDTO.noOp("No applicable exercises in batch.");
        }

        RunInfo claim = new RunInfo(UUID.randomUUID().toString(), exercises.getFirst().getId(), Instant.now());
        RunInfo existing = claimRun(courseId, claim);
        if (existing != null) {
            log.info("Atlas orchestrator (batch) rejected for course {}: run {} already in progress for exercise {}", courseId, existing.runId(), existing.exerciseId());
            return CompetencyOrchestrationResultDTO.inProgress("Another Atlas orchestrator run is already in progress for this course. Please wait for it to finish.");
        }
        try {
            return orchestrateBatch(exercises, courseId);
        }
        finally {
            releaseRun(courseId, claim);
        }
    }

    /**
     * Runs the manual "suggest competencies" flow: force-drains the course's accumulator
     * (bypassing the debounce window) and merges the clicked exercise into the drained set, so any
     * pending changes plus the clicked exercise are reasoned over in a single batched LLM call. The
     * returned result therefore covers the whole batch, not just the clicked exercise. Holds the
     * per-course run lock for the whole batch — a concurrent manual press or a scheduled tick
     * observes IN_PROGRESS while we are running.
     *
     * @param exerciseId the manually triggered exercise (always processed, even when not queued)
     * @return the single batch result covering the clicked exercise and any queued changes
     */
    public CompetencyOrchestrationResultDTO runWithQueuedFlush(long exerciseId) {
        ProgrammingExercise clicked = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        CompetencyOrchestrationResultDTO precheck = precheckExercise(clicked);
        if (precheck != null) {
            return precheck;
        }
        long courseId = clicked.getCourseViaExerciseGroupOrCourseMember().getId();

        RunInfo claim = new RunInfo(UUID.randomUUID().toString(), exerciseId, Instant.now());
        RunInfo existing = claimRun(courseId, claim);
        if (existing != null) {
            log.info("Atlas orchestrator (manual flush) rejected for exercise {} (course {}): run {} already in progress for exercise {}", exerciseId, courseId, existing.runId(),
                    existing.exerciseId());
            return CompetencyOrchestrationResultDTO.inProgress("Another Atlas orchestrator run is already in progress for this course. Please wait for it to finish.");
        }
        try {
            Optional<BatchClaim> drained = contentChangeAccumulatorService.claimBatchNow(courseId);
            Set<Long> queuedExerciseIds = drained.map(BatchClaim::exerciseIds).orElseGet(Set::of);
            // Queued changes first, clicked exercise last; a LinkedHashSet dedupes the clicked id if
            // it was also queued so it is rendered (and run) only once.
            Set<Long> mergedExerciseIds = new LinkedHashSet<>(queuedExerciseIds);
            mergedExerciseIds.add(exerciseId);
            log.info("Atlas orchestrator (manual flush) course {} running batch of {} exercise(s) (including clicked exercise {})", courseId, mergedExerciseIds.size(), exerciseId);
            List<ProgrammingExercise> exercises = resolveBatchExercises(courseId, mergedExerciseIds);
            if (exercises.isEmpty()) {
                return CompetencyOrchestrationResultDTO.noOp("No applicable exercises in batch.");
            }
            CompetencyOrchestrationResultDTO result = orchestrateBatch(exercises, courseId);
            // claimBatchNow drained the bucket; on FAILED (nothing committed) requeue so the drained ids aren't lost.
            if (result.status() == CompetencyOrchestrationResultDTO.Status.FAILED) {
                contentChangeAccumulatorService.requeueAfterFailedRun(courseId, mergedExerciseIds);
            }
            return result;
        }
        finally {
            releaseRun(courseId, claim);
        }
    }

    /**
     * Resolves a set of exercise ids into the programming exercises eligible for orchestration,
     * dropping unknown and exam exercises and — as a defence against a stale/corrupt accumulator
     * entry — any whose owning course does not match {@code courseId} (mixing course content is
     * never correct). Order of {@code exerciseIds} is preserved.
     */
    private List<ProgrammingExercise> resolveBatchExercises(long courseId, Collection<Long> exerciseIds) {
        Map<Long, ProgrammingExercise> byId = new HashMap<>();
        for (ProgrammingExercise exercise : programmingExerciseRepository.findAllById(exerciseIds)) {
            byId.put(exercise.getId(), exercise);
        }
        List<ProgrammingExercise> exercises = new ArrayList<>();
        for (Long id : exerciseIds) {
            ProgrammingExercise exercise = byId.get(id);
            if (exercise == null) {
                log.info("Atlas orchestrator (batch) skipping exercise {}: not found", id);
                continue;
            }
            if (exercise.isExamExercise()) {
                log.info("Atlas orchestrator (batch) skipping exam exercise {}", id);
                continue;
            }
            var course = exercise.getCourseViaExerciseGroupOrCourseMember();
            if (course == null || course.getId() == null || course.getId() != courseId) {
                log.warn("Atlas orchestrator (batch) skipping exercise {}: course ownership mismatch (expected {}, got {})", id, courseId, course == null ? null : course.getId());
                continue;
            }
            exercises.add(exercise);
        }
        return exercises;
    }

    /**
     * Validates an exercise before orchestration. Returns a terminal failure result when the
     * exercise is unsupported (exam) or the chat client is missing; returns {@code null} when the
     * caller may proceed.
     */
    @Nullable
    private CompetencyOrchestrationResultDTO precheckExercise(ProgrammingExercise exercise) {
        if (exercise.isExamExercise()) {
            log.info("Atlas orchestrator rejected for exam exercise {}", exercise.getId());
            return CompetencyOrchestrationResultDTO.failed("Atlas orchestrator only operates on course exercises.",
                    CompetencyOrchestrationResultDTO.FailureReason.UNSUPPORTED_EXERCISE);
        }
        if (chatClient == null) {
            log.info("Atlas orchestrator requested for exercise {} but no ChatClient is available", exercise.getId());
            return CompetencyOrchestrationResultDTO.failed("Atlas chat model is not configured.", CompetencyOrchestrationResultDTO.FailureReason.NO_CHAT_CLIENT);
        }
        return null;
    }

    /**
     * Orchestrates a single exercise. Caller is responsible for holding the per-course
     * {@link #runMap} claim around all invocations within a logical run.
     */
    private CompetencyOrchestrationResultDTO orchestrateExercise(ProgrammingExercise exercise, long courseId) {
        long exerciseId = exercise.getId();
        String systemPrompt;
        try {
            ExtractedContentDTO extracted = contentExtractionService.extractContent(exercise);
            CompetencyIndexResponseDTO competencyIndex = orchestratorPlanningToolsService.listCompetencyIndex(courseId);
            String renderedIndex = renderCompetencyIndex(competencyIndex);
            String renderedChanges = renderExerciseChangeBatch(List.of(new ExerciseChange(exerciseId, extracted.title(), extracted.extractedLearningText())));
            // Map.of key order is irrelevant: the prompt template references both placeholders by
            // name, and the fence sanitization in renderExerciseChangeBatch / renderCompetencyIndex
            // guarantees neither user-supplied string can break out and reposition the other.
            systemPrompt = templateService.render(EXECUTE_PROMPT_PATH, Map.of("exerciseChanges", renderedChanges, "competencyIndex", renderedIndex));
        }
        catch (Exception ex) {
            log.warn("Atlas orchestrator preparation failed for exercise {}: {}", exerciseId, ex.getMessage(), ex);
            return CompetencyOrchestrationResultDTO.failed("Atlas orchestrator run failed.", CompetencyOrchestrationResultDTO.FailureReason.INTERNAL_ERROR);
        }
        // Synchronized list: Spring AI's roadmap supports parallel tool calls; the orchestrator's
        // write tools all go through OrchestratorToolHelpers.appendAction which only adds.
        List<AppliedActionDTO> appliedActions = Collections.synchronizedList(new ArrayList<>());
        String content;
        try {
            content = callChatClient(systemPrompt, courseId, exerciseId, appliedActions);
        }
        catch (Exception ex) {
            log.warn("Atlas orchestrator LLM call failed for exercise {} after applying {} action(s): {}", exerciseId, appliedActions.size(), ex.getMessage(), ex);
            if (appliedActions.isEmpty()) {
                return CompetencyOrchestrationResultDTO.failed("Atlas orchestrator run failed.", CompetencyOrchestrationResultDTO.FailureReason.LLM_ERROR);
            }
            return CompetencyOrchestrationResultDTO.partial("Atlas orchestrator run failed after applying " + appliedActions.size() + " action(s).", List.copyOf(appliedActions),
                    CompetencyOrchestrationResultDTO.FailureReason.LLM_ERROR);
        }
        log.info("Atlas orchestrator completed for exercise {} (course {}) with {} applied action(s)", exerciseId, courseId, appliedActions.size());
        String summary = content.isBlank() ? "Atlas orchestrator run completed." : content;
        return CompetencyOrchestrationResultDTO.success(summary, List.copyOf(appliedActions));
    }

    /**
     * Orchestrates a batch of exercises in one LLM call. All exercises are extracted and rendered
     * into a single numbered EXERCISE CHANGE BATCH; the prompt already reasons across multiple
     * entries. Caller is responsible for holding the per-course {@link #runMap} claim.
     */
    private CompetencyOrchestrationResultDTO orchestrateBatch(List<ProgrammingExercise> exercises, long courseId) {
        String systemPrompt;
        try {
            List<ExerciseChange> changes = new ArrayList<>();
            for (ProgrammingExercise exercise : exercises) {
                ExtractedContentDTO extracted = contentExtractionService.extractContent(exercise);
                changes.add(new ExerciseChange(exercise.getId(), extracted.title(), extracted.extractedLearningText()));
            }
            CompetencyIndexResponseDTO competencyIndex = orchestratorPlanningToolsService.listCompetencyIndex(courseId);
            String renderedIndex = renderCompetencyIndex(competencyIndex);
            String renderedChanges = renderExerciseChangeBatch(changes);
            systemPrompt = templateService.render(EXECUTE_PROMPT_PATH, Map.of("exerciseChanges", renderedChanges, "competencyIndex", renderedIndex));
        }
        catch (Exception ex) {
            log.warn("Atlas orchestrator (batch) preparation failed for course {}: {}", courseId, ex.getMessage(), ex);
            return CompetencyOrchestrationResultDTO.failed("Atlas orchestrator run failed.", CompetencyOrchestrationResultDTO.FailureReason.INTERNAL_ERROR);
        }
        List<AppliedActionDTO> appliedActions = Collections.synchronizedList(new ArrayList<>());
        String content;
        try {
            // Batch cost is attributed to the course; the first exercise stands in for the per-exercise field.
            content = callChatClient(systemPrompt, courseId, exercises.getFirst().getId(), appliedActions);
        }
        catch (Exception ex) {
            log.warn("Atlas orchestrator (batch) LLM call failed for course {} after applying {} action(s): {}", courseId, appliedActions.size(), ex.getMessage(), ex);
            if (appliedActions.isEmpty()) {
                return CompetencyOrchestrationResultDTO.failed("Atlas orchestrator run failed.", CompetencyOrchestrationResultDTO.FailureReason.LLM_ERROR);
            }
            return CompetencyOrchestrationResultDTO.partial("Atlas orchestrator run failed after applying " + appliedActions.size() + " action(s).", List.copyOf(appliedActions),
                    CompetencyOrchestrationResultDTO.FailureReason.LLM_ERROR);
        }
        log.info("Atlas orchestrator (batch) completed for course {} over {} exercise(s) with {} applied action(s)", courseId, exercises.size(), appliedActions.size());
        String summary = content.isBlank() ? "Atlas orchestrator run completed." : content;
        return CompetencyOrchestrationResultDTO.success(summary, List.copyOf(appliedActions));
    }

    /**
     * Drive the Spring AI tool-calling loop through the shared {@link AtlasAgentDelegationService}
     * harness. {@link #run(long)} / {@link #runBatch(long, Set)} guarantee {@link #chatClient} is
     * non-null before we get here, so the harness short-circuit never trips. Returns the (possibly
     * empty) final assistant message; the orchestrator's mutations have already been appended to
     * {@code appliedActions} via the typed {@link AppliedActionsBuffer} in the tool context (passed by
     * reference into the harness).
     * <p>
     * The orchestrator owns its model config: the orchestrator deployment / temperature / reasoning
     * effort from {@link AtlasOrchestratorProperties} are applied via {@link #buildChatOptions()} and
     * handed to the harness, which performs the call with chat memory OFF (each run is a fresh call).
     * The {@link ChatResponse} (rather than just its content) is returned so the round's token usage
     * is persisted via {@link LLMTokenUsageService}, feeding the existing per-course LLM cost views.
     * Tracking is best-effort: it never throws, and {@code userId} resolves to {@code null} when there
     * is no {@code SecurityContext} (e.g. a scheduler-driven run).
     */
    private String callChatClient(String systemPrompt, long courseId, long exerciseId, List<AppliedActionDTO> appliedActions) {
        OpenAiChatOptions.Builder options = buildChatOptions();
        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put(OrchestratorToolContextKeys.COURSE_ID_KEY, courseId);
        toolContext.put(OrchestratorToolContextKeys.APPLIED_ACTIONS_KEY, new AppliedActionsBuffer(appliedActions));
        ChatResponse chatResponse = delegationService.delegateOrchestratorRound(systemPrompt,
                "Plan and execute the competency-management actions required by the listed exercise change.", options, toolContext, orchestratorReadToolCallbackProvider,
                orchestratorPlanningToolCallbackProvider, creatorToolCallbackProvider, editorToolCallbackProvider, assignerToolCallbackProvider);
        Long userId = SecurityUtils.getCurrentUserLogin().flatMap(userRepository::findIdByLogin).orElse(null);
        llmTokenUsageService.trackChatResponseTokenUsage(chatResponse, LLMServiceType.ATLAS, ORCHESTRATION_PIPELINE_ID,
                builder -> builder.withCourse(courseId).withExercise(exerciseId).withUser(userId));
        String content = LLMTokenUsageService.extractResponseText(chatResponse);
        return Objects.requireNonNullElse(content, "");
    }

    /** GPT-5 reasoning models reject explicit temperature alongside reasoningEffort, so we omit one when the other is set. */
    private OpenAiChatOptions.Builder buildChatOptions() {
        var builder = OpenAiChatOptions.builder().deploymentName(deploymentName);
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            builder.reasoningEffort(reasoningEffort);
        }
        else {
            builder.temperature(temperature);
        }
        return builder;
    }

    private static String renderExerciseChangeBatch(List<ExerciseChange> changes) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (ExerciseChange change : changes) {
            String safeTitle = sanitizeForPrompt(change.title(), EXERCISE_TITLE_MAX);
            String safeBody = change.problemStatement() == null || change.problemStatement().isBlank() ? "(no problem statement available)"
                    : sanitizeForPrompt(change.problemStatement(), PROBLEM_STATEMENT_MAX);
            if (index > 1) {
                sb.append("\n\n");
            }
            sb.append(index).append(". [UPDATE id=").append(change.exerciseId()).append("] ").append(safeTitle).append('\n').append(safeBody);
            index++;
        }
        return sb.toString();
    }

    /** One extracted exercise change rendered as a numbered entry in the EXERCISE CHANGE BATCH block. */
    private record ExerciseChange(long exerciseId, String title, @Nullable String problemStatement) {
    }

    /**
     * Neutralizes instructor text before prompt interpolation: strips control / zero-width
     * characters, neutralizes the user-data fence delimiters, and hard-truncates at {@code maxChars}.
     */
    static String sanitizeForPrompt(@Nullable String raw, int maxChars) {
        if (raw == null || raw.isBlank()) {
            return "(empty)";
        }
        String normalized = raw.replace('\u00A0', ' ').replace('\u200B', ' ').replace('\u200C', ' ').replace('\u200D', ' ').replace('\uFEFF', ' ');
        normalized = normalized.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "");
        normalized = normalized.replaceAll("\\n{3,}", "\n\n").strip();
        if (normalized.isEmpty()) {
            return "(empty)";
        }
        normalized = normalized.replace(USER_DATA_BEGIN, "<<<USER_DATA_LITERAL>>>").replace(USER_DATA_END, "<<<END_USER_DATA_LITERAL>>>");
        if (normalized.length() > maxChars) {
            int cut = Math.max(0, maxChars - TRUNCATION_MARKER.length());
            normalized = normalized.substring(0, cut) + TRUNCATION_MARKER;
        }
        return normalized;
    }

    private static String renderCompetencyIndex(CompetencyIndexResponseDTO index) {
        List<CompetencyIndexDTO> competencies = index.competencies();
        List<CompetencyIndexResponseDTO.UnassignedExerciseRefDTO> unassigned = index.unassignedExercises();
        StringBuilder sb = new StringBuilder();
        if (competencies.isEmpty()) {
            sb.append("(no competencies defined in this course yet)\n");
        }
        else {
            for (int i = 0; i < competencies.size(); i++) {
                boolean lastCompetency = i == competencies.size() - 1;
                appendCompetencyBranch(sb, competencies.get(i), lastCompetency);
            }
        }
        sb.append('\n').append("UNASSIGNED EXERCISES (currently linked to no competency):\n");
        if (unassigned.isEmpty()) {
            sb.append("(all course exercises are linked to at least one competency)");
        }
        else {
            for (int i = 0; i < unassigned.size(); i++) {
                boolean last = i == unassigned.size() - 1;
                sb.append(last ? "└── " : "├── ").append(formatUnassignedLine(unassigned.get(i))).append('\n');
            }
        }
        return sb.toString().stripTrailing();
    }

    private static String formatUnassignedLine(CompetencyIndexResponseDTO.UnassignedExerciseRefDTO exercise) {
        String title = sanitizeForPrompt(Objects.requireNonNullElse(exercise.title(), "(untitled)"), EXERCISE_TITLE_MAX);
        String type = sanitizeForPrompt(Objects.requireNonNullElse(exercise.type(), "unknown"), TYPE_LABEL_MAX);
        return "[" + exercise.id() + "] " + title + " (" + type + ")";
    }

    private static void appendCompetencyBranch(StringBuilder sb, CompetencyIndexDTO entry, boolean lastCompetency) {
        String taxonomy = entry.taxonomy() != null ? entry.taxonomy().name() : "UNSPECIFIED";
        String safeTitle = sanitizeForPrompt(entry.title(), COMPETENCY_TITLE_MAX);
        sb.append(lastCompetency ? "└── " : "├── ").append('[').append(entry.id()).append("] ").append(safeTitle).append(" (").append(entry.type()).append(", ").append(taxonomy)
                .append(")\n");
        String childIndent = lastCompetency ? "    " : "│   ";
        boolean hasLectureUnits = !entry.lectureUnits().isEmpty();
        List<String> exerciseLines = entry.exercises().stream().map(CompetencyOrchestrationService::formatExerciseLine).toList();
        appendLeafGroup(sb, childIndent, "exercises", exerciseLines, !hasLectureUnits);
        if (hasLectureUnits) {
            List<String> lectureUnitLines = entry.lectureUnits().stream().map(CompetencyOrchestrationService::formatLectureUnitLine).toList();
            appendLeafGroup(sb, childIndent, "lecture units", lectureUnitLines, true);
        }
    }

    private static String formatExerciseLine(CompetencyIndexDTO.ExerciseLinkRefDTO exercise) {
        String safeTitle = sanitizeForPrompt(exercise.title(), EXERCISE_TITLE_MAX);
        String safeType = sanitizeForPrompt(Objects.requireNonNullElse(exercise.type(), "unknown"), TYPE_LABEL_MAX);
        if (exercise.weight() == null) {
            return safeTitle + " (" + safeType + ")";
        }
        return safeTitle + " (" + safeType + ", w=" + String.format(Locale.ROOT, "%.1f", exercise.weight()) + ")";
    }

    private static String formatLectureUnitLine(CompetencyIndexDTO.LectureUnitRefDTO lectureUnit) {
        String safeName = sanitizeForPrompt(lectureUnit.name(), LECTURE_UNIT_NAME_MAX);
        String safeType = sanitizeForPrompt(Objects.requireNonNullElse(lectureUnit.type(), "unknown"), TYPE_LABEL_MAX);
        return safeName + " (" + safeType + ")";
    }

    /** Distributed map entry guarding per-course runs; expired via {@link #RUN_LEASE} in {@link #claimRun}. */
    record RunInfo(String runId, long exerciseId, @Nullable Instant startedAt) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    private static void appendLeafGroup(StringBuilder sb, String parentIndent, String label, List<String> items, boolean lastGroup) {
        String groupBranch = lastGroup ? "└── " : "├── ";
        sb.append(parentIndent).append(groupBranch).append(label);
        if (items.isEmpty()) {
            sb.append(": —\n");
            return;
        }
        sb.append('\n');
        String leafIndent = parentIndent + (lastGroup ? "    " : "│   ");
        for (int i = 0; i < items.size(); i++) {
            boolean lastItem = i == items.size() - 1;
            sb.append(leafIndent).append(lastItem ? "└── " : "├── ").append(items.get(i)).append('\n');
        }
    }
}
