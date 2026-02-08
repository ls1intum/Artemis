package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import de.tum.cit.aet.artemis.atlas.api.StandardizedCompetencyApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.KnowledgeArea;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistAnalysisRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistAnalysisResponseDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import io.micrometer.observation.ObservationRegistry;

class HyperionChecklistServiceTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private ObservationRegistry observationRegistry;

    @Mock
    private StandardizedCompetencyApi standardizedCompetencyApi;

    private HyperionChecklistService hyperionChecklistService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        ChatClient chatClient = ChatClient.create(chatModel);

        // Mock ObservationRegistry to return a no-op observation
        when(observationRegistry.getCurrentObservation()).thenReturn(null);
        when(observationRegistry.observationConfig()).thenReturn(new ObservationRegistry.ObservationConfig());

        // Mock StandardizedCompetencyService to return empty catalog
        when(standardizedCompetencyApi.getAllForTreeView()).thenReturn(List.of());

        var templateService = new HyperionPromptTemplateService();
        this.hyperionChecklistService = new HyperionChecklistService(chatClient, templateService, observationRegistry, standardizedCompetencyApi);
    }

    @Test
    void analyzeChecklist_returnsFullAnalysis() {
        String competenciesJson = """
                {
                    "competencies": [
                        {
                            "knowledgeAreaShortTitle": "AL",
                            "competencyTitle": "Algorithm Design",
                            "competencyVersion": "1.0.0",
                            "catalogSourceId": 1,
                            "taxonomyLevel": "APPLY",
                            "confidence": 0.9,
                            "rank": 1,
                            "evidence": ["Uses loops", "Implements sorting"],
                            "whyThisMatches": "Exercise requires algorithmic thinking.",
                            "isLikelyPrimary": true
                        }
                    ]
                }
                """;
        String difficultyJson = """
                {
                    "suggested": "EASY",
                    "confidence": 0.85,
                    "reasoning": "Simple algorithmic problem with clear constraints."
                }
                """;
        String qualityJson = """
                {
                    "issues": [
                        {
                            "category": "CLARITY",
                            "severity": "WARNING",
                            "description": "Edge case behavior undefined",
                            "location": { "startLine": 5, "endLine": 7 },
                            "suggestedFix": "Specify behavior for empty input",
                            "impactOnLearners": "Students may be confused about requirements"
                        }
                    ]
                }
                """;

        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt p = invocation.getArgument(0);
            String text = p.getContents();
            if (text.contains("TOP 5 most relevant competencies")) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage(competenciesJson))));
            }
            else if (text.contains("suggest the appropriate difficulty level")) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage(difficultyJson))));
            }
            else if (text.contains("quality issues related to CLARITY")) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage(qualityJson))));
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage("{}"))));
        });

        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(1L);
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);

        ChecklistAnalysisRequestDTO request = new ChecklistAnalysisRequestDTO("Problem statement", "EASY", "JAVA");

        ChecklistAnalysisResponseDTO response = hyperionChecklistService.analyzeChecklist(exercise, request);

        assertThat(response).isNotNull();
        assertThat(response.inferredCompetencies()).hasSize(1);
        assertThat(response.inferredCompetencies().getFirst().competencyTitle()).isEqualTo("Algorithm Design");
        assertThat(response.inferredCompetencies().getFirst().knowledgeAreaShortTitle()).isEqualTo("AL");

        assertThat(response.bloomRadar()).isNotNull();

        assertThat(response.difficultyAssessment()).isNotNull();
        assertThat(response.difficultyAssessment().suggested()).isEqualTo("EASY");
        assertThat(response.difficultyAssessment().delta()).isEqualTo("MATCH");

        assertThat(response.qualityIssues()).hasSize(1);
        assertThat(response.qualityIssues().getFirst().category()).isEqualTo("CLARITY");
        assertThat(response.qualityIssues().getFirst().impactOnLearners()).isNotNull();
    }

    @Test
    void analyzeChecklist_handlesPartialFailures() {
        String competenciesJson = "{ \"competencies\": [] }";
        String qualityJson = "{ \"issues\": [] }";

        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt p = invocation.getArgument(0);
            String text = p.getContents();
            if (text.contains("TOP 5 most relevant competencies")) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage(competenciesJson))));
            }
            else if (text.contains("suggest the appropriate difficulty level")) {
                throw new RuntimeException("AI error");
            }
            else if (text.contains("quality issues related to CLARITY")) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage(qualityJson))));
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage("{}"))));
        });

        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(1L);
        ChecklistAnalysisRequestDTO request = new ChecklistAnalysisRequestDTO("Problem", null, null);

        ChecklistAnalysisResponseDTO response = hyperionChecklistService.analyzeChecklist(exercise, request);

        assertThat(response).isNotNull();
        assertThat(response.inferredCompetencies()).isEmpty();
        assertThat(response.qualityIssues()).isEmpty();

        // Difficulty should be the fallback
        assertThat(response.difficultyAssessment()).isNotNull();
        assertThat(response.difficultyAssessment().suggested()).isEqualTo("UNKNOWN");
        assertThat(response.difficultyAssessment().reasoning()).contains("Analysis failed");
    }

    @Test
    void computeBloomRadar_normalizes() {
        // Mock catalog with competencies
        KnowledgeArea ka = new KnowledgeArea();
        ka.setTitle("Algorithms");
        ka.setShortTitle("AL");
        when(standardizedCompetencyService.getAllForTreeView()).thenReturn(List.of(ka));

        String competenciesJson = """
                {
                    "competencies": [
                        { "knowledgeAreaShortTitle": "AL", "competencyTitle": "A", "taxonomyLevel": "APPLY", "confidence": 0.8, "rank": 1 },
                        { "knowledgeAreaShortTitle": "AL", "competencyTitle": "B", "taxonomyLevel": "ANALYZE", "confidence": 0.2, "rank": 2 }
                    ]
                }
                """;
        String difficultyJson = "{ \"suggested\": \"MEDIUM\", \"confidence\": 0.7, \"reasoning\": \"OK\" }";
        String qualityJson = "{ \"issues\": [] }";

        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt p = invocation.getArgument(0);
            String text = p.getContents();
            if (text.contains("TOP 5 most relevant competencies")) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage(competenciesJson))));
            }
            else if (text.contains("suggest the appropriate difficulty level")) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage(difficultyJson))));
            }
            else {
                return new ChatResponse(List.of(new Generation(new AssistantMessage(qualityJson))));
            }
        });

        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(1L);
        ChecklistAnalysisRequestDTO request = new ChecklistAnalysisRequestDTO("Problem", null, null);

        ChecklistAnalysisResponseDTO response = hyperionChecklistService.analyzeChecklist(exercise, request);

        // Bloom radar should be normalized to sum to 1.0
        var radar = response.bloomRadar();
        assertThat(radar).isNotNull();
        double total = radar.remember() + radar.understand() + radar.apply() + radar.analyze() + radar.evaluate() + radar.create();
        assertThat(total).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));

        // APPLY should have 80% of the weight (0.8 confidence)
        assertThat(radar.apply()).isCloseTo(0.8, org.assertj.core.data.Offset.offset(0.01));
        assertThat(radar.analyze()).isCloseTo(0.2, org.assertj.core.data.Offset.offset(0.01));
    }
}
