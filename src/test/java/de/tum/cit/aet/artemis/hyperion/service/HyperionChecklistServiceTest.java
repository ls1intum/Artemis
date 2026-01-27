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

    private HyperionChecklistService hyperionChecklistService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        // Mock ChatClient creation
        ChatClient chatClient = ChatClient.create(chatModel);

        // Mock ObservationRegistry to return a no-op observation
        when(observationRegistry.getCurrentObservation()).thenReturn(null);
        when(observationRegistry.observationConfig()).thenReturn(new ObservationRegistry.ObservationConfig());

        var templateService = new HyperionPromptTemplateService();
        this.hyperionChecklistService = new HyperionChecklistService(chatClient, templateService, observationRegistry);
    }

    @Test
    void analyzeChecklist_returnsFullAnalysis() {
        String learningGoalsJson = """
                {
                    "goals": [
                        { "skill": "Loops", "taxonomyLevel": "APPLY", "confidence": 0.9, "explanation": "Loop found" }
                    ]
                }
                """;
        String difficultyJson = """
                {
                    "suggested": "EASY",
                    "reasoning": "Simple loops"
                }
                """;
        String qualityJson = """
                {
                    "issues": [
                        { "category": "CLARITY", "severity": "LOW", "description": "Vague", "suggestedFix": "Be specific", "relatedLocations": [] }
                    ]
                }
                """;

        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt p = invocation.getArgument(0);
            String text = p.getContents();
            if (text.contains("infer the intended learning goals")) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage(learningGoalsJson))));
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

        ChecklistAnalysisRequestDTO request = new ChecklistAnalysisRequestDTO("Problem statement", "EASY", List.of("Loops"));

        ChecklistAnalysisResponseDTO response = hyperionChecklistService.analyzeChecklist(exercise, request);

        assertThat(response).isNotNull();
        assertThat(response.inferredLearningGoals()).hasSize(1);
        assertThat(response.inferredLearningGoals().getFirst().skill()).isEqualTo("Loops");

        assertThat(response.suggestedDifficulty()).isNotNull();
        assertThat(response.suggestedDifficulty().suggested()).isEqualTo("EASY");

        assertThat(response.qualityIssues()).hasSize(1);
        assertThat(response.qualityIssues().getFirst().category().name()).isEqualTo("CLARITY");
    }

    @Test
    void analyzeChecklist_handlesPartialFailures() {
        // Mock failure for Difficulty, success for others
        String learningGoalsJson = "{ \"goals\": [] }";
        String qualityJson = "{ \"issues\": [] }";

        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt p = invocation.getArgument(0);
            String text = p.getContents();
            if (text.contains("infer the intended learning goals")) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage(learningGoalsJson))));
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
        assertThat(response.inferredLearningGoals()).isEmpty();
        assertThat(response.qualityIssues()).isEmpty();

        // Difficulty should be the fallback
        assertThat(response.suggestedDifficulty()).isNotNull();
        assertThat(response.suggestedDifficulty().suggested()).isEqualTo("UNKNOWN");
        assertThat(response.suggestedDifficulty().reasoning()).contains("Analysis failed");
    }
}
