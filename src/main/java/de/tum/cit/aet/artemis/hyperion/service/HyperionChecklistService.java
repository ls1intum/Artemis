package de.tum.cit.aet.artemis.hyperion.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.api.CourseCompetencyApi;
import de.tum.cit.aet.artemis.atlas.api.StandardizedCompetencyApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.KnowledgeArea;
import de.tum.cit.aet.artemis.atlas.domain.competency.StandardizedCompetency;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.BloomRadarDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistActionRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistActionResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistAnalysisRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistAnalysisResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistSection;
import de.tum.cit.aet.artemis.hyperion.dto.DifficultyAssessmentDTO;
import de.tum.cit.aet.artemis.hyperion.dto.InferredCompetencyDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QualityIssueDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QualityIssueLocationDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTaskRepository;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Service for analyzing the instructor checklist for programming exercises.
 * Uses the standardized competency catalog to infer competencies, assess
 * difficulty,
 * and identify quality issues in the problem statement.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionChecklistService {

    private static final Logger log = LoggerFactory.getLogger(HyperionChecklistService.class);

    private static final String AI_SPAN_KEY = "ai.span";

    private static final String AI_SPAN_VALUE = "true";

    private static final String LF_SPAN_NAME_KEY = "lf.span.name";

    private final ObjectMapper objectMapper;

    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templates;

    private final ObservationRegistry observationRegistry;

    private final Optional<StandardizedCompetencyApi> standardizedCompetencyApi;

    private final Optional<CourseCompetencyApi> courseCompetencyApi;

    private final ProgrammingExerciseTaskRepository taskRepository;

    /** Lazily cached JSON representation of the standardized competency catalog. */
    private volatile String cachedCatalogJson;

    /** Timestamp of when the catalog was last cached. */
    private volatile Instant catalogCachedAt;

    /** Time-to-live for the competency catalog cache. */
    private static final Duration CATALOG_CACHE_TTL = Duration.ofHours(1);

    /**
     * Maximum time to block the servlet thread waiting for all three concurrent LLM analyses.
     * This call blocks a Tomcat worker thread; keep low to avoid thread-pool exhaustion under
     * concurrent use. Only instructor-level endpoints invoke this, so concurrency is expected
     * to be low.
     */
    private static final Duration ANALYSIS_TIMEOUT = Duration.ofSeconds(60);

    private final Object catalogLock = new Object();

    public HyperionChecklistService(ChatClient chatClient, HyperionPromptTemplateService templates, ObservationRegistry observationRegistry,
            Optional<StandardizedCompetencyApi> standardizedCompetencyApi, Optional<CourseCompetencyApi> courseCompetencyApi, ProgrammingExerciseTaskRepository taskRepository,
            ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.templates = templates;
        this.observationRegistry = observationRegistry;
        this.standardizedCompetencyApi = standardizedCompetencyApi;
        this.courseCompetencyApi = courseCompetencyApi;
        this.taskRepository = taskRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Analyzes the checklist for a programming exercise problem statement.
     * Runs three concurrent analyses: competency inference, difficulty assessment,
     * and quality check.
     *
     * @param request  The request containing the problem statement, metadata, and
     *                     an optional exerciseId for task/test lookups
     * @param courseId The ID of the course (used to load course competencies for matching)
     * @return The analysis response containing inferred competencies, bloom radar,
     *         difficulty, and quality issues
     */
    @Observed(name = "hyperion.checklist", contextualName = "checklist analysis", lowCardinalityKeyValues = { AI_SPAN_KEY, AI_SPAN_VALUE })
    public ChecklistAnalysisResponseDTO analyzeChecklist(ChecklistAnalysisRequestDTO request, long courseId) {
        log.debug("Performing checklist analysis (exerciseId={})", request.exerciseId());

        var ctx = buildAnalysisContext(request, courseId);

        // Run three analyses concurrently
        var competenciesMono = Mono.fromCallable(() -> runCompetencyInference(ctx.input(), ctx.parentObs(), ctx.taskNames())).subscribeOn(Schedulers.boundedElastic())
                .onErrorReturn(List.of());

        var difficultyMono = Mono.fromCallable(() -> runDifficultyAnalysis(ctx.input(), ctx.parentObs(), ctx.declaredDifficulty())).subscribeOn(Schedulers.boundedElastic())
                .onErrorReturn(DifficultyAssessmentDTO.unknown("Analysis failed"));

        var qualityMono = Mono.fromCallable(() -> runQualityAnalysis(ctx.input(), ctx.parentObs())).subscribeOn(Schedulers.boundedElastic()).onErrorReturn(List.of());

        try {
            var resultTuple = Mono.zip(competenciesMono, difficultyMono, qualityMono).block(ANALYSIS_TIMEOUT);

            if (resultTuple == null) {
                return ChecklistAnalysisResponseDTO.empty();
            }

            List<InferredCompetencyDTO> competencies = resultTuple.getT1();
            BloomRadarDTO bloomRadar = computeBloomRadar(competencies);

            return new ChecklistAnalysisResponseDTO(competencies, bloomRadar, resultTuple.getT2(), resultTuple.getT3());
        }
        catch (IllegalStateException e) {
            log.warn("Checklist analysis timed out or failed (exerciseId={})", request.exerciseId(), e);
            return ChecklistAnalysisResponseDTO.empty();
        }
    }

    /**
     * Analyzes a single section of the checklist for a programming exercise.
     * Only runs the requested analysis (competencies, difficulty, or quality),
     * avoiding unnecessary LLM calls for the other sections.
     *
     * @param request  The request containing the problem statement and metadata
     * @param section  The section to analyze
     * @param courseId The ID of the course
     * @return The analysis response with only the requested section populated
     */
    @Observed(name = "hyperion.checklist.section", contextualName = "checklist section analysis", lowCardinalityKeyValues = { AI_SPAN_KEY, AI_SPAN_VALUE })
    public ChecklistAnalysisResponseDTO analyzeSection(ChecklistAnalysisRequestDTO request, ChecklistSection section, long courseId) {
        log.debug("Performing single-section checklist analysis: {} (exerciseId={})", section, request.exerciseId());

        var ctx = buildAnalysisContext(request, courseId);

        return switch (section) {
            case COMPETENCIES -> {
                List<InferredCompetencyDTO> competencies = runCompetencyInference(ctx.input(), ctx.parentObs(), ctx.taskNames());
                BloomRadarDTO bloomRadar = computeBloomRadar(competencies);
                yield new ChecklistAnalysisResponseDTO(competencies, bloomRadar, null, null);
            }
            case DIFFICULTY -> {
                DifficultyAssessmentDTO difficulty = runDifficultyAnalysis(ctx.input(), ctx.parentObs(), ctx.declaredDifficulty());
                yield new ChecklistAnalysisResponseDTO(null, null, difficulty, null);
            }
            case QUALITY -> {
                List<QualityIssueDTO> issues = runQualityAnalysis(ctx.input(), ctx.parentObs());
                yield new ChecklistAnalysisResponseDTO(null, null, null, issues);
            }
        };
    }

    /**
     * Builds the shared analysis context (input map, parent observation, task names,
     * declared difficulty) used by both full and single-section analysis.
     */
    private AnalysisContext buildAnalysisContext(ChecklistAnalysisRequestDTO request, long courseId) {
        String problemStatement = request.problemStatementMarkdown(); // @NotBlank guarantees non-null after validation
        String rawDifficulty = request.declaredDifficulty();
        String declaredDifficulty = (rawDifficulty == null || rawDifficulty.isBlank()) ? "NOT_DECLARED" : rawDifficulty;
        String language = Objects.requireNonNullElse(request.language(), "JAVA");

        // Fetch and serialize the competency catalog
        String catalogJson = serializeCompetencyCatalog();

        // Load and serialize existing course competencies for AI-based matching
        String courseCompetenciesJson = serializeCourseCompetencies(courseId);

        var input = Map.of("problem_statement", problemStatement, "declared_difficulty", declaredDifficulty, "language", language, "competency_catalog", catalogJson,
                "course_competencies", courseCompetenciesJson);

        // Capture the parent observation on the servlet thread so that child spans created
        // on Reactor's boundedElastic threads can be linked correctly. This is safe because
        // parentObs is only read (never mutated) by the Mono callables.
        Observation parentObs = observationRegistry.getCurrentObservation();

        // Load task names for the competency prompt (only for existing exercises)
        List<String> taskNames;
        if (request.exerciseId() != null) {
            Set<ProgrammingExerciseTask> tasks = taskRepository.findByExerciseIdWithTestCases(request.exerciseId());
            taskNames = tasks.stream().map(ProgrammingExerciseTask::getTaskName).filter(name -> name != null && !name.isBlank()).toList();
        }
        else {
            taskNames = List.of();
        }

        return new AnalysisContext(input, parentObs, taskNames, declaredDifficulty);
    }

    /**
     * Internal record holding the shared context needed for analysis methods.
     */
    private record AnalysisContext(Map<String, String> input, Observation parentObs, List<String> taskNames, String declaredDifficulty) {
    }

    /**
     * Applies a checklist action to modify the problem statement using AI.
     * Builds action-specific instructions and calls the LLM to produce an updated
     * problem statement.
     *
     * @param request the action request containing the action type, problem statement,
     *                    and context
     * @return the response containing the updated problem statement
     */
    @Observed(name = "hyperion.checklist.action", contextualName = "checklist action", lowCardinalityKeyValues = { AI_SPAN_KEY, AI_SPAN_VALUE })
    public ChecklistActionResponseDTO applyChecklistAction(ChecklistActionRequestDTO request) {
        log.debug("Applying checklist action: {}", request.actionType());

        // Sanitize context once and reuse for both instructions and summary
        Map<String, String> sanitizedContext = sanitizeContext(request.context());

        String instructions = buildActionInstructions(request.actionType(), sanitizedContext);
        var templateInput = Map.of("action_type", request.actionType().name(), "instructions", instructions, "problem_statement", request.problemStatementMarkdown());

        String renderedPrompt = templates.render("/prompts/hyperion/checklist_action.st", templateInput);

        try {
            String result = chatClient.prompt()
                    .system("You are an expert instructor modifying a programming exercise problem statement. Return ONLY the complete updated problem statement in Markdown.")
                    .user(renderedPrompt).call().content();

            if (result == null || result.isBlank()) {
                return ChecklistActionResponseDTO.failed(request.problemStatementMarkdown());
            }

            String trimmed = result.trim();
            boolean changed = !trimmed.equals(request.problemStatementMarkdown().trim());
            String summary = buildActionSummary(request.actionType(), sanitizedContext);
            return new ChecklistActionResponseDTO(trimmed, changed, summary);
        }
        catch (Exception e) {
            log.warn("Failed to apply checklist action {}: {}", request.actionType(), e.getMessage(), e);
            return ChecklistActionResponseDTO.failed(request.problemStatementMarkdown());
        }
    }

    private static final int MAX_CONTEXT_VALUE_LENGTH = 2000;

    /**
     * Builds action-specific instructions for the AI prompt based on the action type
     * and pre-sanitized context. Context values have already been truncated to
     * {@value MAX_CONTEXT_VALUE_LENGTH} characters to mitigate prompt injection risk.
     */
    private String buildActionInstructions(ChecklistActionRequestDTO.ActionType actionType, Map<String, String> ctx) {
        return switch (actionType) {
            case FIX_QUALITY_ISSUE -> {
                String description = ctx.getOrDefault("issueDescription", "Unknown issue");
                String suggestedFix = ctx.getOrDefault("suggestedFix", "");
                String category = ctx.getOrDefault("category", "");
                yield "Fix the following quality issue in the problem statement:\n" + "Category: " + wrapUserValue(category) + "\n" + "Issue: " + wrapUserValue(description) + "\n"
                        + (suggestedFix.isEmpty() ? "" : "Suggested fix: " + wrapUserValue(suggestedFix) + "\n")
                        + "Make minimal, targeted changes to address ONLY this specific issue.";
            }
            case FIX_ALL_QUALITY_ISSUES -> {
                String issuesList = ctx.getOrDefault("allIssues", "No issues provided");
                yield "Fix ALL of the following quality issues in the problem statement:\n" + wrapUserValue(issuesList) + "\n"
                        + "Address each issue with targeted changes. Do not rewrite unrelated sections.";
            }
            case ADAPT_DIFFICULTY -> {
                String targetDifficulty = ctx.getOrDefault("targetDifficulty", "MEDIUM");
                String currentDifficulty = ctx.getOrDefault("currentDifficulty", "unknown");
                String reasoning = ctx.getOrDefault("reasoning", "");
                String taskCount = ctx.getOrDefault("taskCount", "unknown");
                String testCount = ctx.getOrDefault("testCount", "unknown");
                yield "Adapt the problem statement difficulty from " + wrapUserValue(currentDifficulty) + " to " + wrapUserValue(targetDifficulty) + ".\n"
                        + "Current structural metrics: " + wrapUserValue(taskCount) + " tasks, " + wrapUserValue(testCount) + " tests.\n"
                        + "Target ranges: EASY (1-6 tasks, 3-15 tests), MEDIUM (4-15 tasks, 8-20 tests), HARD (8-25 tasks, 12-30 tests).\n"
                        + (reasoning.isEmpty() ? "" : "Context: " + wrapUserValue(reasoning) + "\n") + "\nCRITICAL INSTRUCTIONS:\n"
                        + "You MUST adjust the NUMBER of tasks and tests to fall within the target range.\n"
                        + "- Artemis tasks use this exact format: [task][Task Name](testCaseName1,testCaseName2)\n"
                        + "- Each [task] block counts as one task. The names inside (...) count as tests.\n"
                        + "- For EASIER: MERGE or REMOVE [task] blocks to reduce task count. Remove test references from the parentheses.\n"
                        + "- For HARDER: SPLIT existing [task] blocks into multiple smaller ones or ADD new [task] blocks. Add new test references inside the parentheses.\n"
                        + "- Invent reasonable new test case names following the existing naming convention (e.g., testMethodName).\n" + "\nContent changes:\n"
                        + "- For EASIER: simplify requirements, reduce edge cases, add more hints and structure.\n"
                        + "- For HARDER: add complexity, edge cases, require deeper analysis.\n" + "\nPreserve the overall Artemis markdown structure and formatting style.";
            }
        };
    }

    /**
     * Wraps a user-supplied context value with structural delimiters to reduce
     * prompt injection risk. Uses triple-backtick fences rather than XML tags
     * to avoid triggering Azure Content Safety filters.
     */
    private static String wrapUserValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return "\n```user-input\n" + value + "\n```\n";
    }

    /**
     * Builds a short human-readable summary of what action was applied.
     */
    private String buildActionSummary(ChecklistActionRequestDTO.ActionType actionType, Map<String, String> ctx) {
        return switch (actionType) {
            case FIX_QUALITY_ISSUE -> "Fixed quality issue: " + ctx.getOrDefault("category", "unknown");
            case FIX_ALL_QUALITY_ISSUES -> "Fixed all quality issues";
            case ADAPT_DIFFICULTY -> "Adapted difficulty to " + ctx.getOrDefault("targetDifficulty", "unknown");
        };
    }

    /**
     * Serializes the standardized competency catalog to a condensed JSON format for
     * the prompt.
     */
    private String serializeCompetencyCatalog() {
        String cached = this.cachedCatalogJson;
        Instant cachedAt = this.catalogCachedAt;
        if (cached != null && cachedAt != null && Instant.now().isBefore(cachedAt.plus(CATALOG_CACHE_TTL))) {
            return cached;
        }
        synchronized (catalogLock) {
            // Double-checked locking: re-read after acquiring lock
            cached = this.cachedCatalogJson;
            cachedAt = this.catalogCachedAt;
            if (cached != null && cachedAt != null && Instant.now().isBefore(cachedAt.plus(CATALOG_CACHE_TTL))) {
                return cached;
            }
            if (standardizedCompetencyApi.isEmpty()) {
                log.warn("StandardizedCompetencyApi is not available (Atlas module disabled). Using empty catalog.");
                return "[]";
            }
            try {
                List<KnowledgeArea> knowledgeAreas = standardizedCompetencyApi.get().getAllForTreeView();
                List<Map<String, Object>> catalog = new ArrayList<>();

                for (KnowledgeArea ka : knowledgeAreas) {
                    serializeKnowledgeArea(ka, catalog);
                }

                String json = objectMapper.writeValueAsString(catalog);
                this.cachedCatalogJson = json;
                this.catalogCachedAt = Instant.now();
                return json;
            }
            catch (JsonProcessingException e) {
                log.error("Failed to serialize competency catalog", e);
                return "[]";
            }
        }
    }

    private void serializeKnowledgeArea(KnowledgeArea ka, List<Map<String, Object>> catalog) {
        if (ka.getCompetencies() != null && !ka.getCompetencies().isEmpty()) {
            for (StandardizedCompetency comp : ka.getCompetencies()) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("knowledgeAreaShortTitle", ka.getShortTitle());
                entry.put("knowledgeAreaTitle", ka.getTitle());
                entry.put("competencyTitle", comp.getTitle());
                entry.put("competencyDescription", comp.getDescription());
                entry.put("taxonomy", comp.getTaxonomy() != null ? comp.getTaxonomy().name() : null);
                entry.put("version", comp.getVersion());
                entry.put("sourceId", comp.getSource() != null ? comp.getSource().getId() : null);
                catalog.add(entry);
            }
        }

        // Recursively process children
        if (ka.getChildren() != null) {
            for (KnowledgeArea child : ka.getChildren()) {
                serializeKnowledgeArea(child, catalog);
            }
        }
    }

    /**
     * Serializes the existing course competencies to a condensed JSON array for inclusion
     * in the competency inference prompt. This allows the AI to directly match inferred
     * competencies against existing course competencies by returning their IDs.
     *
     * @param courseId the ID of the course
     * @return JSON array string of course competencies, or "[]" if unavailable
     */
    private String serializeCourseCompetencies(long courseId) {
        if (courseCompetencyApi.isEmpty()) {
            return "[]";
        }
        try {
            var competencies = courseCompetencyApi.get().findAllForCourse(courseId);
            List<Map<String, Object>> result = new ArrayList<>();
            for (CourseCompetency cc : competencies) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("id", cc.getId());
                entry.put("title", cc.getTitle());
                entry.put("description", cc.getDescription());
                entry.put("taxonomy", cc.getTaxonomy() != null ? cc.getTaxonomy().name() : null);
                result.add(entry);
            }
            return objectMapper.writeValueAsString(result);
        }
        catch (Exception e) {
            log.warn("Failed to serialize course competencies for courseId={}", courseId, e);
            return "[]";
        }
    }

    /**
     * Runs competency inference using the standardized catalog.
     */
    private List<InferredCompetencyDTO> runCompetencyInference(Map<String, String> input, Observation parentObs, List<String> taskNames) {
        var competencyInput = new HashMap<>(input);
        competencyInput.put("task_names", taskNames.isEmpty() ? "(no tasks detected)" : String.join(", ", taskNames));
        String renderedPrompt = templates.render("/prompts/hyperion/checklist_competencies.st", competencyInput);

        return runWithObservation("hyperion.checklist.competencies", "competency inference", parentObs, () -> {
            var response = chatClient.prompt().system("You are an expert in computer science education and curriculum design. Return only JSON matching the schema.")
                    .user(renderedPrompt).call().responseEntity(StructuredOutputSchema.CompetenciesResponse.class);

            var entity = response.entity();
            if (entity == null || entity.competencies() == null) {
                return List.of();
            }

            return entity.competencies().stream().map(c -> {
                List<String> relatedTasks = c.relatedTaskNames() != null ? c.relatedTaskNames() : List.of();
                return new InferredCompetencyDTO(c.knowledgeAreaShortTitle(), c.competencyTitle(), c.competencyVersion(), c.catalogSourceId(), c.taxonomyLevel(), c.confidence(),
                        c.rank(), c.evidence(), c.whyThisMatches(), c.isLikelyPrimary(), relatedTasks, c.matchedCourseCompetencyId());
            }).toList();
        }, List.of());
    }

    /**
     * Computes the Bloom radar distribution from inferred competencies.
     * Only recognised Bloom levels are aggregated; unknown or null taxonomy
     * values are silently ignored so they don't distort the radar.
     */
    private BloomRadarDTO computeBloomRadar(List<InferredCompetencyDTO> competencies) {
        if (competencies == null || competencies.isEmpty()) {
            return BloomRadarDTO.empty();
        }

        Set<String> knownLevels = Set.of("REMEMBER", "UNDERSTAND", "APPLY", "ANALYZE", "EVALUATE", "CREATE");

        // Aggregate confidence-weighted taxonomy levels
        Map<String, Double> taxonomyWeights = new HashMap<>();
        double totalWeight = 0.0;

        for (InferredCompetencyDTO comp : competencies) {
            String level = comp.taxonomyLevel() != null ? comp.taxonomyLevel().toUpperCase() : null;
            if (level == null || !knownLevels.contains(level)) {
                continue;
            }
            double confidence = comp.confidence() != null ? comp.confidence() : 0.5;

            taxonomyWeights.merge(level, confidence, Double::sum);
            totalWeight += confidence;
        }

        // Normalize to sum to 1.0
        final double finalTotal = totalWeight > 0 ? totalWeight : 1.0;

        return new BloomRadarDTO(taxonomyWeights.getOrDefault("REMEMBER", 0.0) / finalTotal, taxonomyWeights.getOrDefault("UNDERSTAND", 0.0) / finalTotal,
                taxonomyWeights.getOrDefault("APPLY", 0.0) / finalTotal, taxonomyWeights.getOrDefault("ANALYZE", 0.0) / finalTotal,
                taxonomyWeights.getOrDefault("EVALUATE", 0.0) / finalTotal, taxonomyWeights.getOrDefault("CREATE", 0.0) / finalTotal);
    }

    /**
     * Runs difficulty analysis.
     */
    private DifficultyAssessmentDTO runDifficultyAnalysis(Map<String, String> input, Observation parentObs, String declaredDifficulty) {
        String renderedPrompt = templates.render("/prompts/hyperion/checklist_difficulty.st", input);

        return runWithObservation("hyperion.checklist.difficulty", "difficulty check", parentObs, () -> {
            var response = chatClient.prompt().system("You are an expert in computer science education. Return only JSON matching the schema.").user(renderedPrompt).call()
                    .responseEntity(StructuredOutputSchema.DifficultyResponse.class);

            var entity = response.entity();
            if (entity == null) {
                return DifficultyAssessmentDTO.unknown("AI returned no response");
            }

            String suggested = entity.suggested();
            Double confidence = entity.confidence();
            boolean matches = suggested != null && declaredDifficulty != null && suggested.equalsIgnoreCase(declaredDifficulty);
            String delta = computeDelta(declaredDifficulty, suggested);
            int taskCount = entity.taskCount() != null ? entity.taskCount() : 0;
            int testCount = entity.testCount() != null ? entity.testCount() : 0;

            return new DifficultyAssessmentDTO(suggested, confidence, entity.reasoning(), matches, delta, taskCount, testCount);
        }, DifficultyAssessmentDTO.unknown("Analysis failed"));
    }

    /**
     * Computes the delta between declared and suggested difficulty.
     */
    private String computeDelta(String declared, String suggested) {
        if (declared == null || suggested == null) {
            return "UNKNOWN";
        }

        Map<String, Integer> levels = Map.of("EASY", 1, "MEDIUM", 2, "HARD", 3);
        Integer declaredLevel = levels.get(declared.toUpperCase());
        Integer suggestedLevel = levels.get(suggested.toUpperCase());

        if (declaredLevel == null || suggestedLevel == null) {
            return "UNKNOWN";
        }

        int cmp = Integer.compare(suggestedLevel, declaredLevel);
        return cmp < 0 ? "LOWER" : cmp > 0 ? "HIGHER" : "MATCH";
    }

    /**
     * Runs quality analysis.
     */
    private List<QualityIssueDTO> runQualityAnalysis(Map<String, String> input, Observation parentObs) {
        String renderedPrompt = templates.render("/prompts/hyperion/checklist_quality.st", input);

        return runWithObservation("hyperion.checklist.quality", "quality check", parentObs, () -> {
            var response = chatClient.prompt().system("You are a technical documentarian and educator. Return only JSON matching the schema.").user(renderedPrompt).call()
                    .responseEntity(StructuredOutputSchema.QualityResponse.class);

            var entity = response.entity();
            if (entity == null || entity.issues() == null) {
                return List.of();
            }

            return entity.issues().stream().map(this::mapQualityIssueToDto).toList();
        }, List.of());
    }

    private QualityIssueDTO mapQualityIssueToDto(StructuredOutputSchema.QualityIssue issue) {
        QualityIssueLocationDTO location = null;
        if (issue.location() != null) {
            location = new QualityIssueLocationDTO(issue.location().startLine(), issue.location().endLine());
        }

        return new QualityIssueDTO(issue.category(), issue.severity(), issue.description(), location, issue.suggestedFix(), issue.impactOnLearners());
    }

    /**
     * Sanitizes context map values by truncating them to {@value MAX_CONTEXT_VALUE_LENGTH}
     * characters. This limits the surface area for prompt injection via user-controlled
     * context values.
     */
    private Map<String, String> sanitizeContext(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            return Map.of();
        }
        Map<String, String> sanitized = new HashMap<>();
        for (var entry : context.entrySet()) {
            String value = entry.getValue();
            if (value != null && value.length() > MAX_CONTEXT_VALUE_LENGTH) {
                value = value.substring(0, MAX_CONTEXT_VALUE_LENGTH);
            }
            sanitized.put(entry.getKey(), value != null ? value : "");
        }
        return sanitized;
    }

    // ===== Observation Helper =====

    /**
     * A supplier that may throw a checked exception.
     */
    @FunctionalInterface
    private interface CheckedSupplier<T> {

        T get() throws Exception;
    }

    /**
     * Runs a unit of work inside a child observation span. On success the result is returned;
     * on failure the error is recorded on the span and the {@code fallback} value is returned.
     *
     * @param <T>         the result type
     * @param name        the metric/span name
     * @param contextName the human-readable context name
     * @param parentObs   the parent observation (may be {@code null})
     * @param work        the work to execute inside the observation scope
     * @param fallback    the value returned when {@code work} throws
     * @return the result of {@code work}, or {@code fallback} on error
     */
    private <T> T runWithObservation(String name, String contextName, Observation parentObs, CheckedSupplier<T> work, T fallback) {
        var child = Observation.createNotStarted(name, observationRegistry).contextualName(contextName).lowCardinalityKeyValue(KeyValue.of(AI_SPAN_KEY, AI_SPAN_VALUE))
                .highCardinalityKeyValue(KeyValue.of(LF_SPAN_NAME_KEY, contextName)).parentObservation(parentObs).start();
        try (var scope = child.openScope()) {
            return work.get();
        }
        catch (Exception e) {
            child.error(e);
            log.warn("Failed to run {}", contextName, e);
            return fallback;
        }
        finally {
            child.stop();
        }
    }

    // ===== Structured Output Schemas =====

    private static class StructuredOutputSchema {

        record CompetenciesResponse(List<CompetencyItem> competencies) {
        }

        record CompetencyItem(String knowledgeAreaShortTitle, String competencyTitle, String competencyVersion, Long catalogSourceId, String taxonomyLevel, Double confidence,
                Integer rank, List<String> evidence, String whyThisMatches, Boolean isLikelyPrimary, List<String> relatedTaskNames, Long matchedCourseCompetencyId) {
        }

        record DifficultyResponse(String suggested, Double confidence, String reasoning, Integer taskCount, Integer testCount) {
        }

        record QualityResponse(List<QualityIssue> issues) {
        }

        record QualityIssue(String category, String severity, String description, LocationRef location, String suggestedFix, String impactOnLearners) {
        }

        record LocationRef(Integer startLine, Integer endLine) {
        }
    }
}
