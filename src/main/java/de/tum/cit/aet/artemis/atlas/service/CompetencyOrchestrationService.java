package de.tum.cit.aet.artemis.atlas.service;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.config.AtlasOrchestratorProperties;
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
            AtlasAgentToolCallbackService toolCallbackFactory, @Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance, AtlasOrchestratorProperties properties) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.contentExtractionService = contentExtractionService;
        this.orchestratorToolsService = orchestratorToolsService;
        this.templateService = templateService;
        this.chatClient = chatClient;
        this.orchestratorToolCallbackProvider = toolCallbackFactory.createOrchestratorProvider();
        this.deploymentName = properties.model();
        this.temperature = properties.temperature();
        this.reasoningEffort = properties.reasoningEffort();
        this.hazelcastInstance = hazelcastInstance;
    }

    /** TTL configured in {@code HazelcastConfiguration#registerCustomMaps}. */
    @PostConstruct
    void initRunMap() {
        this.runMap = hazelcastInstance.getMap(RUN_MAP_NAME);
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
        // Reject exam exercises BEFORE acquiring the Hazelcast lock or doing any work. For an exam
        // exercise, getCourseViaExerciseGroupOrCourseMember() resolves to the underlying course,
        // so the orchestrator would silently mutate course-wide competencies — never desired.
        if (exercise.isExamExercise()) {
            log.info("Atlas orchestrator rejected for exam exercise {}", exerciseId);
            return CompetencyOrchestrationResultDTO.failed("Atlas orchestrator only operates on course exercises.",
                    CompetencyOrchestrationResultDTO.FailureReason.UNSUPPORTED_EXERCISE);
        }
        if (chatClient == null) {
            log.info("Atlas orchestrator requested for exercise {} but no ChatClient is available", exerciseId);
            return CompetencyOrchestrationResultDTO.failed("Atlas chat model is not configured.", CompetencyOrchestrationResultDTO.FailureReason.NO_CHAT_CLIENT);
        }
        long courseId = exercise.getCourseViaExerciseGroupOrCourseMember().getId();

        String runId = UUID.randomUUID().toString();
        RunInfo claim = new RunInfo(runId, exerciseId, Instant.now());
        RunInfo existing = runMap.putIfAbsent(courseId, claim);
        if (existing != null) {
            log.info("Atlas orchestrator rejected for exercise {} (course {}): run {} already in progress for exercise {}", exerciseId, courseId, existing.runId(),
                    existing.exerciseId());
            return CompetencyOrchestrationResultDTO.inProgress("Another Atlas orchestrator run is already in progress for this course. Please wait for it to finish.");
        }
        // Synchronized list: Spring AI's roadmap supports parallel tool calls; the orchestrator's
        // write tools all go through OrchestratorToolsService.appendAction which only adds.
        List<AppliedActionDTO> appliedActions = Collections.synchronizedList(new ArrayList<>());
        try {
            String content;
            try {
                ExtractedContentDTO extracted = contentExtractionService.extractContent(exercise);
                CompetencyIndexResponseDTO competencyIndex = orchestratorToolsService.listCompetencyIndex(courseId);
                String renderedIndex = renderCompetencyIndex(competencyIndex);
                String renderedChanges = renderExerciseChangeBatch(exerciseId, extracted.title(), extracted.extractedLearningText());
                // Map.of key order is irrelevant: the prompt template references both placeholders
                // by name, and the fence sanitization in renderExerciseChangeBatch /
                // renderCompetencyIndex guarantees neither user-supplied string can break out and
                // reposition the other.
                String systemPrompt = templateService.render(EXECUTE_PROMPT_PATH, Map.of("exerciseChanges", renderedChanges, "competencyIndex", renderedIndex));

                content = callChatClient(systemPrompt, courseId, appliedActions);
            }
            catch (Exception ex) {
                log.warn("Atlas orchestrator failed for exercise {} after applying {} action(s): {}", exerciseId, appliedActions.size(), ex.getMessage(), ex);
                if (appliedActions.isEmpty()) {
                    return CompetencyOrchestrationResultDTO.failed("Atlas orchestrator run failed.", CompetencyOrchestrationResultDTO.FailureReason.LLM_ERROR);
                }
                return CompetencyOrchestrationResultDTO.partial("Atlas orchestrator run failed after applying " + appliedActions.size() + " action(s).",
                        List.copyOf(appliedActions), CompetencyOrchestrationResultDTO.FailureReason.LLM_ERROR);
            }
            log.info("Atlas orchestrator completed for exercise {} (course {}) with {} applied actions", exerciseId, courseId, appliedActions.size());
            String message = content.isBlank() ? "Atlas orchestrator run completed." : content;
            return CompetencyOrchestrationResultDTO.success(message, List.copyOf(appliedActions));
        }
        finally {
            // Compare-and-remove: leaves a TTL-evicted entry replaced by another claim untouched.
            runMap.remove(courseId, claim);
        }
    }

    /**
     * Drive the Spring AI tool-calling loop. {@link #run(long)} guarantees {@link #chatClient} is
     * non-null before we get here, so no null check is needed and no null is returned. Returns the
     * (possibly empty) final assistant message; the orchestrator's mutations have already been
     * appended to {@code appliedActions} via the typed buffer in the tool context.
     */
    private String callChatClient(String systemPrompt, long courseId, List<AppliedActionDTO> appliedActions) {
        ToolCallingChatOptions options = buildChatOptions();
        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put(OrchestratorToolsService.COURSE_ID_KEY, courseId);
        toolContext.put(OrchestratorToolsService.APPLIED_ACTIONS_KEY, new OrchestratorToolsService.AppliedActionsBuffer(appliedActions));
        var promptSpec = chatClient.prompt().system(systemPrompt).user("Plan and execute the competency-management actions required by the listed exercise change.")
                .options(options).toolContext(toolContext);
        if (orchestratorToolCallbackProvider != null) {
            promptSpec = promptSpec.toolCallbacks(orchestratorToolCallbackProvider);
        }
        String content = promptSpec.call().content();
        return Objects.requireNonNullElse(content, "");
    }

    /** GPT-5 reasoning models reject explicit temperature alongside reasoningEffort, so we omit one when the other is set. */
    private ToolCallingChatOptions buildChatOptions() {
        // LOCAL PATCH: self-hosted OpenAI-compatible endpoint — do NOT commit
        var builder = OpenAiChatOptions.builder().model(deploymentName);
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            builder.reasoningEffort(reasoningEffort);
        }
        else {
            builder.temperature(temperature);
        }
        return builder.build();
    }

    private static String renderExerciseChangeBatch(long exerciseId, String title, String problemStatement) {
        String safeTitle = sanitizeForPrompt(title, EXERCISE_TITLE_MAX);
        String safeBody = problemStatement == null || problemStatement.isBlank() ? "(no problem statement available)" : sanitizeForPrompt(problemStatement, PROBLEM_STATEMENT_MAX);
        return "1. [UPDATE id=" + exerciseId + "] " + safeTitle + "\n" + safeBody;
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

    /** Hazelcast map entry guarding per-course orchestrator runs; expiry is handled by the map TTL. */
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
