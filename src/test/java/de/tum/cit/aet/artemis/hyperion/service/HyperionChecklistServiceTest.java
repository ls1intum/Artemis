package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
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

import de.tum.cit.aet.artemis.hyperion.domain.QualityIssueCategory;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistAnalysisRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistAnalysisResponseDTO;
import io.micrometer.observation.ObservationRegistry;

class HyperionChecklistServiceTest {

    @Mock
    private ChatModel chatModel;

    private HyperionChecklistService hyperionChecklistService;

    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        ChatClient chatClient = ChatClient.create(chatModel);

        var templateService = new HyperionPromptTemplateService();
        this.hyperionChecklistService = new HyperionChecklistService(chatClient, templateService, ObservationRegistry.NOOP);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void analyzeChecklist_returnsQualityAnalysis() {
        String qualityJson = """
                {
                    "issues": [
                        {
                            "category": "CLARITY",
                            "severity": "MEDIUM",
                            "description": "Edge case behavior undefined",
                            "location": { "startLine": 5, "endLine": 7 },
                            "suggestedFix": "Specify behavior for empty input",
                            "impactOnLearners": "Students may be confused about requirements"
                        }
                    ]
                }
                """;

        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(qualityJson)))));

        ChecklistAnalysisRequestDTO request = new ChecklistAnalysisRequestDTO("Problem statement", "EASY", "JAVA", 1L);

        ChecklistAnalysisResponseDTO response = hyperionChecklistService.analyzeChecklist(request, 1L).join();

        assertThat(response).isNotNull();
        assertThat(response.qualityIssues()).hasSize(1);
        assertThat(response.qualityIssues().getFirst().category()).isEqualTo(QualityIssueCategory.CLARITY);
        assertThat(response.qualityIssues().getFirst().impactOnLearners()).isNotNull();
        assertThat(response.bloomRadar()).isNotNull();
    }

    @Test
    void analyzeChecklist_handlesFailure() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("AI error"));

        ChecklistAnalysisRequestDTO request = new ChecklistAnalysisRequestDTO("Problem", null, null, 1L);

        ChecklistAnalysisResponseDTO response = hyperionChecklistService.analyzeChecklist(request, 1L).join();

        assertThat(response).isNotNull();
        assertThat(response.qualityIssues()).isEmpty();
        assertThat(response.bloomRadar()).isNotNull();
    }

    @Test
    void analyzeSection_qualityOnly() {
        String qualityJson = """
                {
                    "issues": [
                        {
                            "category": "COMPLETENESS",
                            "severity": "HIGH",
                            "description": "Missing edge case",
                            "suggestedFix": "Add edge case handling",
                            "impactOnLearners": "Students may miss requirements"
                        }
                    ]
                }
                """;

        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(qualityJson)))));

        ChecklistAnalysisRequestDTO request = new ChecklistAnalysisRequestDTO("Problem", null, null, null);

        ChecklistAnalysisResponseDTO response = hyperionChecklistService.analyzeSection(request, 1L).join();

        assertThat(response).isNotNull();
        assertThat(response.qualityIssues()).hasSize(1);
        assertThat(response.qualityIssues().getFirst().category()).isEqualTo(QualityIssueCategory.COMPLETENESS);
    }
}
