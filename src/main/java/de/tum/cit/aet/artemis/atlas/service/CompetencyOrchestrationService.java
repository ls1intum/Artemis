package de.tum.cit.aet.artemis.atlas.service;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jakarta.annotation.PostConstruct;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.hazelcast.config.MapConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.dto.AppliedActionDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyIndexDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyIndexResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO;
import de.tum.cit.aet.artemis.atlas.dto.ExtractedContentDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * Entry point for autonomous competency management runs.
 * <p>
 * {@link #run(long)} drives a tool-calling LLM loop: the model is given the exercise as anchor
 * text and can call {@link OrchestratorToolsService}'s read tools to inspect course state and
 * the five write tools ({@code createCompetency}, {@code editCompetency},
 * {@code assignExerciseToCompetency}, {@code unassignExerciseFromCompetency},
 * {@code deleteCompetency}) to mutate it. Every successful mutation is appended to a
 * per-run applied-actions list held in the Spring AI {@code ToolContext}.
 * <p>
 * Course context is injected via {@code ToolContext} (see {@link OrchestratorToolsService}) so
 * the LLM cannot forge the course id through tool arguments.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Service
public class CompetencyOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(CompetencyOrchestrationService.class);

    private static final String EXECUTE_PROMPT_PATH = "prompts/atlas/orchestrator_execute_prompt.st";

    private static final String RUN_MAP_NAME = "atlas-orchestrator-runs";

    /**
     * Upper bound after which a stale Hazelcast run entry is evicted automatically. The lock is
     * always released explicitly in the {@code finally} block; the TTL only catches the case
     * where the JVM dies mid-run. Must be longer than the longest plausible LLM session (the
     * GPT-5.4 + medium-reasoning runs observed so far took ~5 min).
     */
    private static final int RUN_TTL_SECONDS = 30 * 60;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ContentExtractionService contentExtractionService;

    private final OrchestratorToolsService orchestratorToolsService;

    private final AtlasPromptTemplateService templateService;

    @Nullable
    private final ChatClient chatClient;

    @Nullable
    private final ToolCallbackProvider orchestratorToolCallbackProvider;

    private final String deploymentName;

    private final double temperature;

    private final String reasoningEffort;

    private final HazelcastInstance hazelcastInstance;

    private IMap<Long, RunInfo> runMap;

    public CompetencyOrchestrationService(ProgrammingExerciseRepository programmingExerciseRepository, ContentExtractionService contentExtractionService,
            OrchestratorToolsService orchestratorToolsService, AtlasPromptTemplateService templateService, @Nullable ChatClient chatClient,
            @Nullable @Qualifier("orchestratorToolCallbackProvider") ToolCallbackProvider orchestratorToolCallbackProvider,
            @Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance, @Value("${artemis.atlas.orchestrator-model:gpt-5.4}") String deploymentName,
            @Value("${artemis.atlas.orchestrator-temperature:1.0}") double temperature, @Value("${artemis.atlas.orchestrator-reasoning-effort:medium}") String reasoningEffort) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.contentExtractionService = contentExtractionService;
        this.orchestratorToolsService = orchestratorToolsService;
        this.templateService = templateService;
        this.chatClient = chatClient;
        this.orchestratorToolCallbackProvider = orchestratorToolCallbackProvider;
        this.hazelcastInstance = hazelcastInstance;
        this.deploymentName = deploymentName;
        this.temperature = temperature;
        this.reasoningEffort = reasoningEffort;
    }

    @PostConstruct
    void init() {
        MapConfig mapConfig = hazelcastInstance.getConfig().getMapConfig(RUN_MAP_NAME);
        mapConfig.setTimeToLiveSeconds(RUN_TTL_SECONDS);
        runMap = hazelcastInstance.getMap(RUN_MAP_NAME);
    }

    /**
     * Run one orchestration pass for the given programming exercise. The orchestrator plans
     * internally and executes its plan by calling write tools — each tool call mutates state
     * immediately and appends to the applied-actions list returned in the result.
     *
     * @param exerciseId the programming exercise to orchestrate competencies for
     * @return a {@link CompetencyOrchestrationResultDTO.Status#SUCCESS} result with the LLM's
     *         summary message and the list of applied actions, or a
     *         {@link CompetencyOrchestrationResultDTO.Status#FAILED} result when the LLM call fails
     *         or no ChatClient is configured
     */
    public CompetencyOrchestrationResultDTO run(long exerciseId) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        long courseId = exercise.getCourseViaExerciseGroupOrCourseMember().getId();

        String runId = UUID.randomUUID().toString();
        RunInfo existing = runMap.putIfAbsent(courseId, new RunInfo(runId, exerciseId, Instant.now()));
        if (existing != null) {
            log.info("Atlas orchestrator rejected for exercise {} (course {}): run {} already in progress for exercise {}", exerciseId, courseId, existing.runId(),
                    existing.exerciseId());
            return CompetencyOrchestrationResultDTO.inProgress("Another Atlas orchestrator run is already in progress for this course. Please wait for it to finish.");
        }
        try {
            ExtractedContentDTO extracted = contentExtractionService.extractContent(exercise);
            CompetencyIndexResponseDTO competencyIndex = orchestratorToolsService.listCompetencyIndex(courseId);
            String renderedIndex = renderCompetencyIndex(competencyIndex);
            String renderedChanges = renderExerciseChangeBatch(exerciseId, extracted.title(), extracted.extractedLearningText());
            String systemPrompt = templateService.render(EXECUTE_PROMPT_PATH, Map.of("exerciseChanges", renderedChanges, "competencyIndex", renderedIndex));

            List<AppliedActionDTO> appliedActions = new ArrayList<>();
            String content;
            try {
                content = callChatClient(systemPrompt, courseId, appliedActions);
            }
            catch (Exception ex) {
                log.warn("Atlas orchestrator chat call failed for exercise {}: {}", exerciseId, ex.getMessage());
                return CompetencyOrchestrationResultDTO.failed("Atlas orchestrator run failed.");
            }
            if (content == null) {
                log.info("Atlas orchestrator requested for exercise {} but no ChatClient is available", exerciseId);
                return CompetencyOrchestrationResultDTO.failed("Atlas chat model is not configured.");
            }
            log.info("Atlas orchestrator completed for exercise {} (course {}) with {} applied actions", exerciseId, courseId, appliedActions.size());
            String message = content.isBlank() ? "Atlas orchestrator run completed." : content;
            return CompetencyOrchestrationResultDTO.success(message, appliedActions);
        }
        finally {
            // Only clear if the stored run is still ours — TTL eviction followed by a new run would
            // otherwise let us delete someone else's claim.
            RunInfo stored = runMap.get(courseId);
            if (stored != null && runId.equals(stored.runId())) {
                runMap.remove(courseId);
            }
        }
    }

    @Nullable
    private String callChatClient(String systemPrompt, long courseId, List<AppliedActionDTO> appliedActions) {
        if (chatClient == null) {
            return null;
        }
        ToolCallingChatOptions options = AzureOpenAiChatOptions.builder().deploymentName(deploymentName).temperature(temperature).reasoningEffort(reasoningEffort).build();
        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put(OrchestratorToolsService.COURSE_ID_KEY, courseId);
        toolContext.put(OrchestratorToolsService.APPLIED_ACTIONS_KEY, appliedActions);
        var promptSpec = chatClient.prompt().system(systemPrompt).user("Plan and execute the competency-management actions required by the listed exercise change.")
                .options(options).toolContext(toolContext);
        if (orchestratorToolCallbackProvider != null) {
            promptSpec = promptSpec.toolCallbacks(orchestratorToolCallbackProvider);
        }
        String content = promptSpec.call().content();
        return Objects.requireNonNullElse(content, "");
    }

    private static String renderExerciseChangeBatch(long exerciseId, String title, String problemStatement) {
        String body = problemStatement == null || problemStatement.isBlank() ? "(no problem statement available)" : problemStatement.strip();
        return "1. [UPDATE id=" + exerciseId + "] " + title + "\n" + body;
    }

    private static String renderCompetencyIndex(CompetencyIndexResponseDTO index) {
        List<CompetencyIndexDTO> competencies = index.competencies();
        List<CompetencyIndexResponseDTO.UnassignedExerciseRef> unassigned = index.unassignedExercises();
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

    private static String formatUnassignedLine(CompetencyIndexResponseDTO.UnassignedExerciseRef exercise) {
        String title = exercise.title() != null ? exercise.title() : "(untitled)";
        String type = exercise.type() != null ? exercise.type() : "unknown";
        return "[" + exercise.id() + "] " + title + " (" + type + ")";
    }

    private static void appendCompetencyBranch(StringBuilder sb, CompetencyIndexDTO entry, boolean lastCompetency) {
        String taxonomy = entry.taxonomy() != null ? entry.taxonomy().name() : "UNSPECIFIED";
        sb.append(lastCompetency ? "└── " : "├── ").append('[').append(entry.id()).append("] ").append(entry.title()).append(" (").append(entry.type()).append(", ")
                .append(taxonomy).append(")\n");
        String childIndent = lastCompetency ? "    " : "│   ";
        boolean hasLectureUnits = !entry.lectureUnitNames().isEmpty();
        List<String> exerciseLines = entry.exercises().stream().map(CompetencyOrchestrationService::formatExerciseLine).toList();
        appendLeafGroup(sb, childIndent, "exercises", exerciseLines, !hasLectureUnits);
        if (hasLectureUnits) {
            appendLeafGroup(sb, childIndent, "lecture units", entry.lectureUnitNames(), true);
        }
    }

    private static String formatExerciseLine(CompetencyIndexDTO.ExerciseLinkRef exercise) {
        if (exercise.weight() == null) {
            return exercise.title();
        }
        return exercise.title() + " (w=" + String.format(Locale.ROOT, "%.1f", exercise.weight()) + ")";
    }

    /**
     * Hazelcast map entry guarding per-course orchestrator runs.
     *
     * @param runId      unique id of the currently active run, used to reject concurrent requests
     *                       for the same course and to ensure the run-holder only clears its own entry
     * @param exerciseId the exercise that triggered the run, kept for diagnostics/logging
     * @param startedAt  wall-clock start timestamp (advisory; real expiry is handled by the map TTL)
     */
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
