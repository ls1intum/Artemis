package de.tum.cit.aet.artemis.hyperion.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.api.StandardizedCompetencyApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.KnowledgeArea;
import de.tum.cit.aet.artemis.atlas.domain.competency.StandardizedCompetency;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.BloomRadarDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistActionRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistActionResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistAnalysisRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistAnalysisResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.DifficultyAssessmentDTO;
import de.tum.cit.aet.artemis.hyperion.dto.InferredCompetencyDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QualityIssueDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QualityIssueLocationDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTaskRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
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

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templates;

    private final ObservationRegistry observationRegistry;

    private final StandardizedCompetencyApi standardizedCompetencyApi;

    private final ProgrammingExerciseTaskRepository taskRepository;

    private final ProgrammingExerciseTestCaseRepository testCaseRepository;

    public HyperionChecklistService(ChatClient chatClient, HyperionPromptTemplateService templates, ObservationRegistry observationRegistry,
            StandardizedCompetencyApi standardizedCompetencyApi, ProgrammingExerciseTaskRepository taskRepository, ProgrammingExerciseTestCaseRepository testCaseRepository) {
        this.chatClient = chatClient;
        this.templates = templates;
        this.observationRegistry = observationRegistry;
        this.standardizedCompetencyApi = standardizedCompetencyApi;
        this.taskRepository = taskRepository;
        this.testCaseRepository = testCaseRepository;
    }

    /**
     * Analyzes the checklist for the given programming exercise.
     * Runs three concurrent analyses: competency inference, difficulty assessment,
     * and quality check.
     *
     * @param exercise The programming exercise to analyze
     * @param request  The request containing the problem statement and other
     *                     details
     * @return The analysis response containing inferred competencies, bloom radar,
     *         difficulty, and quality issues
     */
    @Observed(name = "hyperion.checklist", contextualName = "checklist analysis", lowCardinalityKeyValues = { AI_SPAN_KEY, AI_SPAN_VALUE })
    public ChecklistAnalysisResponseDTO analyzeChecklist(ProgrammingExercise exercise, ChecklistAnalysisRequestDTO request) {
        log.info("Performing checklist analysis for exercise {}", exercise.getId());

        String problemStatement = request.problemStatementMarkdown() != null ? request.problemStatementMarkdown() : "";
        String declaredDifficulty = request.declaredDifficulty() != null ? request.declaredDifficulty()
                : (exercise.getDifficulty() != null ? exercise.getDifficulty().name() : null);
        String language = request.language() != null ? request.language() : (exercise.getProgrammingLanguage() != null ? exercise.getProgrammingLanguage().name() : "JAVA");

        // Fetch and serialize the competency catalog
        String catalogJson = serializeCompetencyCatalog();

        var input = Map.of("problem_statement", problemStatement, "declared_difficulty", declaredDifficulty != null ? declaredDifficulty : "NOT_DECLARED", "language", language,
                "competency_catalog", catalogJson);

        Observation parentObs = observationRegistry.getCurrentObservation();

        // Load tasks with their test cases for structural metrics
        Set<ProgrammingExerciseTask> tasks = taskRepository.findByExerciseIdWithTestCases(exercise.getId());
        int taskCount = tasks.size();
        int testCount = testCaseRepository.findByExerciseId(exercise.getId()).size();

        // Build task name list for the competency prompt
        List<String> taskNames = tasks.stream().map(ProgrammingExerciseTask::getTaskName).filter(name -> name != null && !name.isBlank()).toList();

        // Run three analyses concurrently
        var competenciesMono = Mono.fromCallable(() -> runCompetencyInference(input, parentObs, taskNames, tasks, testCount)).subscribeOn(Schedulers.boundedElastic())
                .onErrorReturn(List.of());

        var difficultyMono = Mono.fromCallable(() -> runDifficultyAnalysis(input, parentObs, declaredDifficulty, taskCount, testCount)).subscribeOn(Schedulers.boundedElastic())
                .onErrorReturn(DifficultyAssessmentDTO.unknown("Analysis failed"));

        var qualityMono = Mono.fromCallable(() -> runQualityAnalysis(input, parentObs)).subscribeOn(Schedulers.boundedElastic()).onErrorReturn(List.of());

        var resultTuple = Mono.zip(competenciesMono, difficultyMono, qualityMono).block();

        if (resultTuple == null) {
            return ChecklistAnalysisResponseDTO.empty();
        }

        List<InferredCompetencyDTO> competencies = resultTuple.getT1();
        BloomRadarDTO bloomRadar = computeBloomRadar(competencies);

        return new ChecklistAnalysisResponseDTO(competencies, bloomRadar, resultTuple.getT2(), resultTuple.getT3());
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
        log.info("Applying checklist action: {}", request.actionType());

        String instructions = buildActionInstructions(request);
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
            String summary = buildActionSummary(request);
            return new ChecklistActionResponseDTO(trimmed, changed, summary);
        }
        catch (Exception e) {
            log.warn("Failed to apply checklist action {}: {}", request.actionType(), e.getMessage(), e);
            return ChecklistActionResponseDTO.failed(request.problemStatementMarkdown());
        }
    }

    /**
     * Builds action-specific instructions for the AI prompt based on the action type
     * and context.
     */
    private String buildActionInstructions(ChecklistActionRequestDTO request) {
        Map<String, String> ctx = request.context() != null ? request.context() : Map.of();

        return switch (request.actionType()) {
            case FIX_QUALITY_ISSUE -> {
                String description = ctx.getOrDefault("issueDescription", "Unknown issue");
                String suggestedFix = ctx.getOrDefault("suggestedFix", "");
                String category = ctx.getOrDefault("category", "");
                yield "Fix the following " + category + " quality issue in the problem statement:\n" + "Issue: " + description + "\n"
                        + (suggestedFix.isEmpty() ? "" : "Suggested fix: " + suggestedFix + "\n") + "Make minimal, targeted changes to address ONLY this specific issue.";
            }
            case FIX_ALL_QUALITY_ISSUES -> {
                String issuesList = ctx.getOrDefault("allIssues", "No issues provided");
                yield "Fix ALL of the following quality issues in the problem statement:\n" + issuesList + "\n"
                        + "Address each issue with targeted changes. Do not rewrite unrelated sections.";
            }
            case ADAPT_DIFFICULTY -> {
                String targetDifficulty = ctx.getOrDefault("targetDifficulty", "MEDIUM");
                String currentDifficulty = ctx.getOrDefault("currentDifficulty", "unknown");
                String reasoning = ctx.getOrDefault("reasoning", "");
                String taskCount = ctx.getOrDefault("taskCount", "unknown");
                String testCount = ctx.getOrDefault("testCount", "unknown");
                yield "Adapt the problem statement difficulty from " + currentDifficulty + " to " + targetDifficulty + ".\n" + "Current structural metrics: " + taskCount
                        + " tasks, " + testCount + " tests.\n"
                        + "Reference ranges: EASY (1-6 tasks, 3-15 tests), MEDIUM (4-15 tasks, 8-20 tests), HARD (8-25 tasks, 12-30 tests).\n"
                        + (reasoning.isEmpty() ? "" : "Context: " + reasoning + "\n")
                        + "For EASIER: simplify requirements, reduce edge cases, consolidate tasks, add more hints and structure.\n"
                        + "For HARDER: add complexity, edge cases, split tasks into finer steps, require deeper analysis.\n"
                        + "Preserve all Artemis task markers and test references.";
            }
            case EMPHASIZE_COMPETENCY -> {
                String competency = ctx.getOrDefault("competencyTitle", "Unknown");
                String taxonomyLevel = ctx.getOrDefault("taxonomyLevel", "");
                String taskCoverage = ctx.getOrDefault("taskCoverage", "unknown");
                String relatedTasks = ctx.getOrDefault("relatedTasks", "");
                yield "Increase the emphasis on the competency '" + competency + "'" + (taxonomyLevel.isEmpty() ? "" : " (taxonomy level: " + taxonomyLevel + ")")
                        + " in the problem statement.\n" + "Current task coverage: " + taskCoverage + (relatedTasks.isEmpty() ? "" : ". Currently related tasks: " + relatedTasks)
                        + ".\n" + "Add more tasks, examples, or requirements that exercise this skill to increase its coverage.\n"
                        + "Preserve the overall structure and all existing Artemis task markers.";
            }
            case DEEMPHASIZE_COMPETENCY -> {
                String competency = ctx.getOrDefault("competencyTitle", "Unknown");
                String taskCoverage = ctx.getOrDefault("taskCoverage", "unknown");
                String relatedTasks = ctx.getOrDefault("relatedTasks", "");
                yield "Reduce the emphasis on the competency '" + competency + "' in the problem statement.\n" + "Current task coverage: " + taskCoverage
                        + (relatedTasks.isEmpty() ? "" : ". Currently related tasks: " + relatedTasks) + ".\n"
                        + "Simplify or reduce the tasks listed above that heavily exercise this skill, without removing essential requirements.\n"
                        + "Preserve the overall structure and all existing Artemis task markers.";
            }
        };
    }

    /**
     * Builds a short human-readable summary of what action was applied.
     */
    private String buildActionSummary(ChecklistActionRequestDTO request) {
        Map<String, String> ctx = request.context() != null ? request.context() : Map.of();

        return switch (request.actionType()) {
            case FIX_QUALITY_ISSUE -> "Fixed quality issue: " + ctx.getOrDefault("category", "unknown");
            case FIX_ALL_QUALITY_ISSUES -> "Fixed all quality issues";
            case ADAPT_DIFFICULTY -> "Adapted difficulty to " + ctx.getOrDefault("targetDifficulty", "unknown");
            case EMPHASIZE_COMPETENCY -> "Emphasized competency: " + ctx.getOrDefault("competencyTitle", "unknown");
            case DEEMPHASIZE_COMPETENCY -> "De-emphasized competency: " + ctx.getOrDefault("competencyTitle", "unknown");
        };
    }

    /**
     * Serializes the standardized competency catalog to a condensed JSON format for
     * the prompt.
     */
    private String serializeCompetencyCatalog() {
        try {
            List<KnowledgeArea> knowledgeAreas = standardizedCompetencyApi.getAllForTreeView();
            List<Map<String, Object>> catalog = new ArrayList<>();

            for (KnowledgeArea ka : knowledgeAreas) {
                serializeKnowledgeArea(ka, catalog);
            }

            return objectMapper.writeValueAsString(catalog);
        }
        catch (JsonProcessingException e) {
            log.error("Failed to serialize competency catalog", e);
            return "[]";
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
     * Runs competency inference using the standardized catalog.
     */
    private List<InferredCompetencyDTO> runCompetencyInference(Map<String, String> input, Observation parentObs, List<String> taskNames, Set<ProgrammingExerciseTask> tasks,
            int totalTestCount) {
        var child = Observation.createNotStarted("hyperion.checklist.competencies", observationRegistry).contextualName("competency inference")
                .lowCardinalityKeyValue(KeyValue.of(AI_SPAN_KEY, AI_SPAN_VALUE)).highCardinalityKeyValue(KeyValue.of(LF_SPAN_NAME_KEY, "competency inference"))
                .parentObservation(parentObs).start();

        var resourcePath = "/prompts/hyperion/checklist_competencies.st";
        var competencyInput = new HashMap<>(input);
        competencyInput.put("task_names", taskNames.isEmpty() ? "(no tasks detected)" : String.join(", ", taskNames));
        String renderedPrompt = templates.render(resourcePath, competencyInput);

        try (Observation.Scope scope = child.openScope()) {
            var response = chatClient.prompt().system("You are an expert in computer science education and curriculum design. Return only JSON matching the schema.")
                    .user(renderedPrompt).call().responseEntity(StructuredOutputSchema.CompetenciesResponse.class);

            var entity = response.entity();
            if (entity == null || entity.competencies() == null) {
                return List.of();
            }

            // Build task name -> test count lookup
            Map<String, Integer> taskTestCounts = new HashMap<>();
            for (ProgrammingExerciseTask task : tasks) {
                if (task.getTaskName() != null) {
                    taskTestCounts.put(task.getTaskName().toLowerCase(), task.getTestCases() != null ? task.getTestCases().size() : 0);
                }
            }
            int totalTasks = tasks.size();

            return entity.competencies().stream().map(c -> {
                List<String> relatedTasks = c.relatedTaskNames() != null ? c.relatedTaskNames() : List.of();
                double taskCoverage = totalTasks > 0 ? (double) relatedTasks.size() / totalTasks : 0.0;
                int coveredTests = relatedTasks.stream().mapToInt(name -> taskTestCounts.getOrDefault(name.toLowerCase(), 0)).sum();
                double testCoverage = totalTestCount > 0 ? (double) coveredTests / totalTestCount : 0.0;
                return new InferredCompetencyDTO(c.knowledgeAreaShortTitle(), c.competencyTitle(), c.competencyVersion(), c.catalogSourceId(), c.taxonomyLevel(), c.confidence(),
                        c.rank(), c.evidence(), c.whyThisMatches(), c.isLikelyPrimary(), relatedTasks, Math.min(1.0, taskCoverage), Math.min(1.0, testCoverage));
            }).toList();
        }
        catch (Exception e) {
            child.error(e);
            log.warn("Failed to analyze competencies", e);
            return List.of();
        }
        finally {
            child.stop();
        }
    }

    /**
     * Computes the Bloom radar distribution from inferred competencies.
     */
    private BloomRadarDTO computeBloomRadar(List<InferredCompetencyDTO> competencies) {
        if (competencies == null || competencies.isEmpty()) {
            return BloomRadarDTO.empty();
        }

        // Aggregate confidence-weighted taxonomy levels
        Map<String, Double> taxonomyWeights = new HashMap<>();
        double totalWeight = 0.0;

        for (InferredCompetencyDTO comp : competencies) {
            String level = comp.taxonomyLevel();
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
    private DifficultyAssessmentDTO runDifficultyAnalysis(Map<String, String> input, Observation parentObs, String declaredDifficulty, int taskCount, int testCount) {
        var child = Observation.createNotStarted("hyperion.checklist.difficulty", observationRegistry).contextualName("difficulty check")
                .lowCardinalityKeyValue(KeyValue.of(AI_SPAN_KEY, AI_SPAN_VALUE)).highCardinalityKeyValue(KeyValue.of(LF_SPAN_NAME_KEY, "difficulty check"))
                .parentObservation(parentObs).start();

        var resourcePath = "/prompts/hyperion/checklist_difficulty.st";
        var difficultyInput = new HashMap<>(input);
        difficultyInput.put("task_count", String.valueOf(taskCount));
        difficultyInput.put("test_count", String.valueOf(testCount));
        String renderedPrompt = templates.render(resourcePath, difficultyInput);

        try (Observation.Scope scope = child.openScope()) {
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

            return new DifficultyAssessmentDTO(suggested, confidence, entity.reasoning(), matches, delta, taskCount, testCount);
        }
        catch (Exception e) {
            child.error(e);
            log.warn("Failed to analyze difficulty", e);
            return DifficultyAssessmentDTO.unknown("Analysis failed: " + e.getMessage());
        }
        finally {
            child.stop();
        }
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

        if (suggestedLevel < declaredLevel) {
            return "LOWER";
        }
        else if (suggestedLevel > declaredLevel) {
            return "HIGHER";
        }
        else {
            return "MATCH";
        }
    }

    /**
     * Runs quality analysis.
     */
    private List<QualityIssueDTO> runQualityAnalysis(Map<String, String> input, Observation parentObs) {
        var child = Observation.createNotStarted("hyperion.checklist.quality", observationRegistry).contextualName("quality check")
                .lowCardinalityKeyValue(KeyValue.of(AI_SPAN_KEY, AI_SPAN_VALUE)).highCardinalityKeyValue(KeyValue.of(LF_SPAN_NAME_KEY, "quality check"))
                .parentObservation(parentObs).start();

        var resourcePath = "/prompts/hyperion/checklist_quality.st";
        String renderedPrompt = templates.render(resourcePath, input);

        try (Observation.Scope scope = child.openScope()) {
            var response = chatClient.prompt().system("You are a technical documentarian and educator. Return only JSON matching the schema.").user(renderedPrompt).call()
                    .responseEntity(StructuredOutputSchema.QualityResponse.class);

            var entity = response.entity();
            if (entity == null || entity.issues() == null) {
                return List.of();
            }

            return entity.issues().stream().map(this::mapQualityIssueToDto).toList();
        }
        catch (Exception e) {
            child.error(e);
            log.warn("Failed to analyze quality", e);
            return List.of();
        }
        finally {
            child.stop();
        }
    }

    private QualityIssueDTO mapQualityIssueToDto(StructuredOutputSchema.QualityIssue issue) {
        QualityIssueLocationDTO location = null;
        if (issue.location() != null) {
            location = new QualityIssueLocationDTO(issue.location().startLine(), issue.location().endLine());
        }

        return new QualityIssueDTO(issue.category(), issue.severity(), issue.description(), location, issue.suggestedFix(), issue.impactOnLearners());
    }

    // ===== Structured Output Schemas =====

    private static class StructuredOutputSchema {

        record CompetenciesResponse(List<CompetencyItem> competencies) {
        }

        record CompetencyItem(String knowledgeAreaShortTitle, String competencyTitle, String competencyVersion, Long catalogSourceId, String taxonomyLevel, Double confidence,
                Integer rank, List<String> evidence, String whyThisMatches, Boolean isLikelyPrimary, List<String> relatedTaskNames) {
        }

        record DifficultyResponse(String suggested, Double confidence, String reasoning) {
        }

        record QualityResponse(List<QualityIssue> issues) {
        }

        record QualityIssue(String category, String severity, String description, LocationRef location, String suggestedFix, String impactOnLearners) {
        }

        record LocationRef(Integer startLine, Integer endLine) {
        }
    }
}
