package de.tum.cit.aet.artemis.hyperion.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.ArtifactType;
import de.tum.cit.aet.artemis.hyperion.domain.ConsistencyIssueCategory;
import de.tum.cit.aet.artemis.hyperion.domain.Severity;
import de.tum.cit.aet.artemis.hyperion.dto.ArtifactLocationDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistAnalysisRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistAnalysisResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyIssueDTO;
import de.tum.cit.aet.artemis.hyperion.dto.DifficultyAssessmentDTO;
import de.tum.cit.aet.artemis.hyperion.dto.LearningGoalItemDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionChecklistService {

    private static final Logger log = LoggerFactory.getLogger(HyperionChecklistService.class);

    private static final String AI_SPAN_KEY = "ai.span";

    private static final String AI_SPAN_VALUE = "true";

    private static final String LF_SPAN_NAME_KEY = "lf.span.name";

    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templates;

    private final ObservationRegistry observationRegistry;

    public HyperionChecklistService(ChatClient chatClient, HyperionPromptTemplateService templates, ObservationRegistry observationRegistry) {
        this.chatClient = chatClient;
        this.templates = templates;
        this.observationRegistry = observationRegistry;
    }

    @Observed(name = "hyperion.checklist", contextualName = "checklist analysis", lowCardinalityKeyValues = { AI_SPAN_KEY, AI_SPAN_VALUE })
    public ChecklistAnalysisResponseDTO analyzeChecklist(ProgrammingExercise exercise, ChecklistAnalysisRequestDTO request) {
        log.info("Performing checklist analysis for exercise {}", exercise.getId());

        String difficulty = request.existingDifficulty() != null ? request.existingDifficulty() : (exercise.getDifficulty() != null ? exercise.getDifficulty().name() : "UNKNOWN");
        String learningGoals = request.existingLearningGoals() != null ? String.join(", ", request.existingLearningGoals()) : "None declared";
        String problemStatement = request.problemStatement() != null ? request.problemStatement() : "";

        var input = Map.of("problem_statement", problemStatement, "declared_difficulty", difficulty, "existing_learning_goals", learningGoals);

        Observation parentObs = observationRegistry.getCurrentObservation();

        var learningGoalsMono = Mono.fromCallable(() -> runLearningGoalsAnalysis(input, parentObs)).subscribeOn(Schedulers.boundedElastic()).onErrorReturn(List.of());
        var difficultyMono = Mono.fromCallable(() -> runDifficultyAnalysis(input, parentObs, difficulty)).subscribeOn(Schedulers.boundedElastic())
                .onErrorReturn(new DifficultyAssessmentDTO("UNKNOWN", "Failed to analyze difficulty", false));
        var qualityMono = Mono.fromCallable(() -> runQualityAnalysis(input, parentObs)).subscribeOn(Schedulers.boundedElastic()).onErrorReturn(List.of());

        var resultTuple = Mono.zip(learningGoalsMono, difficultyMono, qualityMono).block();

        if (resultTuple == null) {
            return new ChecklistAnalysisResponseDTO(List.of(), null, List.of());
        }

        return new ChecklistAnalysisResponseDTO(resultTuple.getT1(), resultTuple.getT2(), resultTuple.getT3());
    }

    private List<LearningGoalItemDTO> runLearningGoalsAnalysis(Map<String, String> input, Observation parentObs) {
        var child = Observation.createNotStarted("hyperion.checklist.learning_goals", observationRegistry).contextualName("learning goals check")
                .lowCardinalityKeyValue(io.micrometer.common.KeyValue.of(AI_SPAN_KEY, AI_SPAN_VALUE))
                .highCardinalityKeyValue(io.micrometer.common.KeyValue.of(LF_SPAN_NAME_KEY, "learning goals check")).parentObservation(parentObs).start();

        var resourcePath = "/prompts/hyperion/checklist_learning_goals.st";
        String renderedPrompt = templates.render(resourcePath, input);

        try (Observation.Scope scope = child.openScope()) {
            var response = chatClient.prompt().system("You are an expert in computer science education and curriculum design. Return only JSON matching the schema.")
                    .user(renderedPrompt).call().responseEntity(StructuredOutputSchema.LearningGoalsResponse.class);

            var entity = response.entity();
            if (entity == null || entity.goals() == null)
                return List.of();

            return entity.goals().stream().map(g -> new LearningGoalItemDTO(g.skill(), g.taxonomyLevel(), g.confidence(), g.explanation())).toList();
        }
        catch (Exception e) {
            child.error(e);
            log.warn("Failed to analyze learning goals", e);
            return List.of();
        }
        finally {
            child.stop();
        }
    }

    private DifficultyAssessmentDTO runDifficultyAnalysis(Map<String, String> input, Observation parentObs, String declaredDifficulty) {
        var child = Observation.createNotStarted("hyperion.checklist.difficulty", observationRegistry).contextualName("difficulty check")
                .lowCardinalityKeyValue(io.micrometer.common.KeyValue.of(AI_SPAN_KEY, AI_SPAN_VALUE))
                .highCardinalityKeyValue(io.micrometer.common.KeyValue.of(LF_SPAN_NAME_KEY, "difficulty check")).parentObservation(parentObs).start();

        var resourcePath = "/prompts/hyperion/checklist_difficulty.st";
        String renderedPrompt = templates.render(resourcePath, input);

        try (Observation.Scope scope = child.openScope()) {
            var response = chatClient.prompt().system("You are an expert in computer science education. Return only JSON matching the schema.").user(renderedPrompt).call()
                    .responseEntity(StructuredOutputSchema.DifficultyResponse.class);

            var entity = response.entity();
            if (entity == null)
                return new DifficultyAssessmentDTO("UNKNOWN", "AI returned no response", false);

            boolean matches = entity.suggested() != null && entity.suggested().equalsIgnoreCase(declaredDifficulty);
            return new DifficultyAssessmentDTO(entity.suggested(), entity.reasoning(), matches);
        }
        catch (Exception e) {
            child.error(e);
            log.warn("Failed to analyze difficulty", e);
            return new DifficultyAssessmentDTO("UNKNOWN", "Analysis failed: " + e.getMessage(), false);
        }
        finally {
            child.stop();
        }
    }

    private List<ConsistencyIssueDTO> runQualityAnalysis(Map<String, String> input, Observation parentObs) {
        var child = Observation.createNotStarted("hyperion.checklist.quality", observationRegistry).contextualName("quality check")
                .lowCardinalityKeyValue(io.micrometer.common.KeyValue.of(AI_SPAN_KEY, AI_SPAN_VALUE))
                .highCardinalityKeyValue(io.micrometer.common.KeyValue.of(LF_SPAN_NAME_KEY, "quality check")).parentObservation(parentObs).start();

        var resourcePath = "/prompts/hyperion/checklist_quality.st";
        String renderedPrompt = templates.render(resourcePath, input);

        try (Observation.Scope scope = child.openScope()) {
            var response = chatClient.prompt().system("You are a technical documentarian and educator. Return only JSON matching the schema.").user(renderedPrompt).call()
                    .responseEntity(StructuredOutputSchema.QualityResponse.class);

            var entity = response.entity();
            if (entity == null || entity.issues() == null)
                return List.of();

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

    private ConsistencyIssueDTO mapQualityIssueToDto(StructuredOutputSchema.QualityIssue issue) {
        Severity severity = switch (issue.severity() == null ? "MEDIUM" : issue.severity().toUpperCase()) {
            case "LOW", "INFO" -> Severity.LOW;
            case "HIGH", "ERROR" -> Severity.HIGH;
            default -> Severity.MEDIUM;
        };

        List<ArtifactLocationDTO> locations = issue.relatedLocations() == null ? List.of()
                : issue.relatedLocations().stream()
                        .map(loc -> new ArtifactLocationDTO(loc.type() == null ? ArtifactType.PROBLEM_STATEMENT : loc.type(), loc.filePath(), loc.startLine(), loc.endLine()))
                        .toList();

        ConsistencyIssueCategory category;
        try {
            category = issue.category() != null ? ConsistencyIssueCategory.valueOf(issue.category().toUpperCase()) : ConsistencyIssueCategory.CLARITY;
        }
        catch (IllegalArgumentException e) {
            category = ConsistencyIssueCategory.CLARITY; // Default fallback
        }

        return new ConsistencyIssueDTO(severity, category, issue.description(), issue.suggestedFix(), locations);
    }

    // Structured Output Schema
    private static class StructuredOutputSchema {

        record LearningGoalsResponse(List<LearningGoalItem> goals) {
        }

        record LearningGoalItem(String skill, String taxonomyLevel, Double confidence, String explanation) {
        }

        record DifficultyResponse(String suggested, String reasoning) {
        }

        record QualityResponse(List<QualityIssue> issues) {
        }

        record QualityIssue(String category, String severity, String description, String suggestedFix, List<ArtifactLocation> relatedLocations) {
        }

        record ArtifactLocation(ArtifactType type, String filePath, Integer startLine, Integer endLine) {
        }
    }
}
