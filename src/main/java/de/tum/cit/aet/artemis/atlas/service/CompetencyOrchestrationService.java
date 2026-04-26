package de.tum.cit.aet.artemis.atlas.service;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
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
import de.tum.cit.aet.artemis.atlas.dto.CompetencyIndexDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyIndexResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO;
import de.tum.cit.aet.artemis.atlas.dto.ExtractedContentDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * Entry point for advisory competency-management runs.
 * <p>
 * {@link #run(long)} drives a tool-calling LLM loop: the model is given the exercise as anchor
 * text and can call {@link OrchestratorToolsService}'s read tools to inspect course state, then
 * writes a natural-language summary of the changes it would recommend. Mutation tools are
 * deferred to a follow-up PR — the orchestrator does not persist any changes today.
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
     * Length caps applied to instructor-controlled strings before they are interpolated into the
     * system prompt. Instructor input is untrusted ground truth — without caps, hallucinated 4 kB
     * titles or pasted code dumps could blow up the prompt budget AND give an injection attempt
     * more room to maneuver. Hard truncation produces a clear marker the LLM can recognize.
     */
    private static final int EXERCISE_TITLE_MAX = 200;

    private static final int PROBLEM_STATEMENT_MAX = 8_000;

    private static final int COMPETENCY_TITLE_MAX = 200;

    private static final int LECTURE_UNIT_NAME_MAX = 200;

    private static final int EXERCISE_TYPE_MAX = 50;

    private static final String TRUNCATION_MARKER = " …[truncated]";

    /**
     * Markers used in {@link #orchestrator_execute_prompt.st} to delimit the untrusted-data sections.
     * Any literal occurrence inside user content is neutralized so the model cannot be tricked
     * into believing user-supplied text has closed the fence and reverted to instruction mode.
     */
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

    private final IMap<Long, RunInfo> runMap;

    public CompetencyOrchestrationService(ProgrammingExerciseRepository programmingExerciseRepository, ContentExtractionService contentExtractionService,
            OrchestratorToolsService orchestratorToolsService, AtlasPromptTemplateService templateService, @Nullable ChatClient chatClient,
            AtlasAgentToolCallbackService toolCallbackFactory, @Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance, AtlasOrchestratorProperties properties) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.contentExtractionService = contentExtractionService;
        this.orchestratorToolsService = orchestratorToolsService;
        this.templateService = templateService;
        this.chatClient = chatClient;
        this.orchestratorToolCallbackProvider = toolCallbackFactory.createOrchestratorProvider();
        this.deploymentName = properties.orchestratorModel();
        this.temperature = properties.orchestratorTemperature();
        this.reasoningEffort = properties.orchestratorReasoningEffort();
        // TTL for this map is configured at Hazelcast bean construction time in
        // HazelcastConfiguration#registerCustomMaps — setting it here via @PostConstruct would be
        // a no-op once the map proxy is built.
        this.runMap = hazelcastInstance.getMap(RUN_MAP_NAME);
    }

    /**
     * Run one orchestration pass for the given programming exercise. Returns the LLM's
     * advisory summary of which competency changes it would recommend.
     *
     * @param exerciseId the programming exercise to orchestrate competencies for
     * @return one of:
     *         <ul>
     *         <li>{@link CompetencyOrchestrationResultDTO.Status#SUCCESS} with the LLM's summary message;</li>
     *         <li>{@link CompetencyOrchestrationResultDTO.Status#FAILED} with {@link CompetencyOrchestrationResultDTO.FailureReason#LLM_ERROR} if the call failed,
     *         or {@link CompetencyOrchestrationResultDTO.FailureReason#NO_CHAT_CLIENT} if no ChatClient is configured;</li>
     *         <li>{@link CompetencyOrchestrationResultDTO.Status#IN_PROGRESS} if a run is already active for the same course.</li>
     *         </ul>
     */
    public CompetencyOrchestrationResultDTO run(long exerciseId) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        // Reject exam exercises BEFORE acquiring the Hazelcast lock or doing any work. For an exam
        // exercise, getCourseViaExerciseGroupOrCourseMember() resolves to the underlying course,
        // so the orchestrator would silently advise on course-wide competencies — never desired.
        if (exercise.isExamExercise()) {
            log.info("Atlas orchestrator rejected for exam exercise {}", exerciseId);
            return CompetencyOrchestrationResultDTO.failed("Atlas orchestrator only operates on course exercises.",
                    CompetencyOrchestrationResultDTO.FailureReason.UNSUPPORTED_EXERCISE);
        }
        // Fail fast (and without acquiring the per-course Hazelcast lock) when the chat client is
        // not configured — keeps the no-chat-client path orthogonal to the "ok-but-empty content"
        // path inside callChatClient.
        if (chatClient == null) {
            log.info("Atlas orchestrator requested for exercise {} but no ChatClient is available", exerciseId);
            return CompetencyOrchestrationResultDTO.failed("Atlas chat model is not configured.", CompetencyOrchestrationResultDTO.FailureReason.NO_CHAT_CLIENT);
        }
        // Exam exercises were rejected above, so the utility resolves to the directly-attached
        // course without walking the lazy exerciseGroup.exam.course chain.
        long courseId = exercise.getCourseViaExerciseGroupOrCourseMember().getId();

        String runId = UUID.randomUUID().toString();
        RunInfo existing = runMap.putIfAbsent(courseId, new RunInfo(runId, exerciseId, Instant.now()));
        if (existing != null) {
            log.info("Atlas orchestrator rejected for exercise {} (course {}): run {} already in progress for exercise {}", exerciseId, courseId, existing.runId(),
                    existing.exerciseId());
            return CompetencyOrchestrationResultDTO.inProgress("Another Atlas orchestrator run is already in progress for this course. Please wait for it to finish.");
        }
        try {
            String content;
            try {
                // Pre-LLM helpers (extractContent / listCompetencyIndex / templateService.render)
                // are inside the try so a failure becomes FAILED instead of bubbling a 500 to the
                // resource — and so the finally block's lock release is symmetric with all paths.
                ExtractedContentDTO extracted = contentExtractionService.extractContent(exercise);
                CompetencyIndexResponseDTO competencyIndex = orchestratorToolsService.listCompetencyIndex(courseId);
                String renderedIndex = renderCompetencyIndex(competencyIndex);
                String renderedChanges = renderExerciseChangeBatch(exerciseId, extracted.title(), extracted.extractedLearningText());
                String systemPrompt = templateService.render(EXECUTE_PROMPT_PATH, Map.of("exerciseChanges", renderedChanges, "competencyIndex", renderedIndex));

                content = callChatClient(systemPrompt, courseId);
            }
            catch (Exception ex) {
                log.warn("Atlas orchestrator failed for exercise {}: {}", exerciseId, ex.getMessage());
                return CompetencyOrchestrationResultDTO.failed("Atlas orchestrator run failed.", CompetencyOrchestrationResultDTO.FailureReason.LLM_ERROR);
            }
            log.info("Atlas orchestrator completed for exercise {} (course {})", exerciseId, courseId);
            String summary = content.isBlank() ? "Atlas orchestrator run completed." : content;
            return CompetencyOrchestrationResultDTO.success(summary);
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

    /**
     * Drive the Spring AI tool-calling loop. {@link #run(long)} guarantees {@link #chatClient} is
     * non-null before we get here, so no null check is needed and no null is returned. Returns the
     * (possibly empty) final assistant message.
     */
    private String callChatClient(String systemPrompt, long courseId) {
        ToolCallingChatOptions options = buildChatOptions();
        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put(OrchestratorToolsService.COURSE_ID_KEY, courseId);
        var promptSpec = chatClient.prompt().system(systemPrompt)
                .user("Inspect the listed exercise change and produce an advisory summary of the competency changes you would recommend.").options(options)
                .toolContext(toolContext);
        if (orchestratorToolCallbackProvider != null) {
            promptSpec = promptSpec.toolCallbacks(orchestratorToolCallbackProvider);
        }
        String content = promptSpec.call().content();
        return Objects.requireNonNullElse(content, "");
    }

    /**
     * Build {@link AzureOpenAiChatOptions} for the orchestrator call. GPT-5 reasoning models reject
     * an explicit {@code temperature} alongside {@code reasoningEffort} (only the default 1.0 is
     * accepted), so we omit temperature whenever a reasoning effort is configured. Older / non-
     * reasoning deployments still get the temperature.
     */
    private ToolCallingChatOptions buildChatOptions() {
        var builder = AzureOpenAiChatOptions.builder().deploymentName(deploymentName);
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
     * Neutralize instructor-controlled text before it is concatenated into the orchestrator's
     * system prompt. Three concerns:
     * <ol>
     * <li>Strip control characters and normalize whitespace so injected ANSI / zero-width tricks
     * cannot reposition tokens within the prompt.</li>
     * <li>Hard-truncate at {@code maxChars} with an explicit marker so a hallucinated or pasted
     * code-dump cannot blow up the prompt budget — and so the LLM can see the truncation
     * happened.</li>
     * <li>Neutralize literal occurrences of the user-data fence delimiters so user content
     * cannot pretend to close the fence and revert to instruction mode.</li>
     * </ol>
     */
    static String sanitizeForPrompt(@Nullable String raw, int maxChars) {
        if (raw == null || raw.isBlank()) {
            return "(empty)";
        }
        String normalized = raw.replace(' ', ' ').replace('​', ' ').replace('‌', ' ').replace('‍', ' ').replace('﻿', ' ');
        normalized = normalized.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "");
        normalized = normalized.replaceAll("\\n{3,}", "\n\n").strip();
        if (normalized.isEmpty()) {
            return "(empty)";
        }
        // Defuse fence delimiters so user content cannot break out of the fenced data section.
        normalized = normalized.replace(USER_DATA_BEGIN, "<<<USER_DATA_LITERAL>>>").replace(USER_DATA_END, "<<<END_USER_DATA_LITERAL>>>");
        if (normalized.length() > maxChars) {
            int cut = Math.max(0, maxChars - TRUNCATION_MARKER.length());
            normalized = normalized.substring(0, cut) + TRUNCATION_MARKER;
        }
        return normalized;
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
        String title = sanitizeForPrompt(exercise.title() != null ? exercise.title() : "(untitled)", EXERCISE_TITLE_MAX);
        String type = sanitizeForPrompt(exercise.type() != null ? exercise.type() : "unknown", EXERCISE_TYPE_MAX);
        return "[" + exercise.id() + "] " + title + " (" + type + ")";
    }

    private static void appendCompetencyBranch(StringBuilder sb, CompetencyIndexDTO entry, boolean lastCompetency) {
        String taxonomy = entry.taxonomy() != null ? entry.taxonomy().name() : "UNSPECIFIED";
        String safeTitle = sanitizeForPrompt(entry.title(), COMPETENCY_TITLE_MAX);
        sb.append(lastCompetency ? "└── " : "├── ").append('[').append(entry.id()).append("] ").append(safeTitle).append(" (").append(entry.type()).append(", ").append(taxonomy)
                .append(")\n");
        String childIndent = lastCompetency ? "    " : "│   ";
        boolean hasLectureUnits = !entry.lectureUnitNames().isEmpty();
        List<String> exerciseLines = entry.exercises().stream().map(CompetencyOrchestrationService::formatExerciseLine).toList();
        appendLeafGroup(sb, childIndent, "exercises", exerciseLines, !hasLectureUnits);
        if (hasLectureUnits) {
            List<String> safeLectureNames = entry.lectureUnitNames().stream().map(name -> sanitizeForPrompt(name, LECTURE_UNIT_NAME_MAX)).toList();
            appendLeafGroup(sb, childIndent, "lecture units", safeLectureNames, true);
        }
    }

    private static String formatExerciseLine(CompetencyIndexDTO.ExerciseLinkRef exercise) {
        String safeTitle = sanitizeForPrompt(exercise.title(), EXERCISE_TITLE_MAX);
        if (exercise.weight() == null) {
            return safeTitle;
        }
        return safeTitle + " (w=" + String.format(Locale.ROOT, "%.1f", exercise.weight()) + ")";
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
