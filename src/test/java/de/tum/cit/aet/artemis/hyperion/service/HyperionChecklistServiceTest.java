package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import de.tum.cit.aet.artemis.hyperion.domain.ChecklistSection;
import de.tum.cit.aet.artemis.hyperion.domain.QualityIssueCategory;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistActionRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistActionResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistAnalysisRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistAnalysisResponseDTO;
import io.micrometer.observation.ObservationRegistry;

@ExtendWith(MockitoExtension.class)
class HyperionChecklistServiceTest {

    @Mock
    private ChatModel chatModel;

    private HyperionChecklistService hyperionChecklistService;

    @BeforeEach
    void setup() {
        ChatClient chatClient = ChatClient.create(chatModel);

        var templateService = new HyperionPromptTemplateService();
        this.hyperionChecklistService = new HyperionChecklistService(chatClient, templateService, ObservationRegistry.NOOP);
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

        ChecklistAnalysisResponseDTO response = hyperionChecklistService.analyzeSection(request, 1L, ChecklistSection.QUALITY).join();

        assertThat(response).isNotNull();
        assertThat(response.qualityIssues()).hasSize(1);
        assertThat(response.qualityIssues().getFirst().category()).isEqualTo(QualityIssueCategory.COMPLETENESS);
    }

    @Test
    void applyChecklistAction_fixQualityIssue() {
        String updatedStatement = "Updated problem statement with fix applied";
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(updatedStatement)))));

        var request = new ChecklistActionRequestDTO(ChecklistActionRequestDTO.ActionType.FIX_QUALITY_ISSUE, "Original problem statement",
                Map.of("issueDescription", "Vague instructions", "category", "CLARITY", "suggestedFix", "Be more specific"));

        ChecklistActionResponseDTO response = hyperionChecklistService.applyChecklistAction(request).join();

        assertThat(response).isNotNull();
        assertThat(response.applied()).isTrue();
        assertThat(response.updatedProblemStatement()).isEqualTo(updatedStatement);
        assertThat(response.summary()).contains("CLARITY");
    }

    @Test
    void applyChecklistAction_handlesFailure() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("AI error"));

        var request = new ChecklistActionRequestDTO(ChecklistActionRequestDTO.ActionType.FIX_QUALITY_ISSUE, "Original problem statement",
                Map.of("issueDescription", "Vague", "category", "CLARITY"));

        ChecklistActionResponseDTO response = hyperionChecklistService.applyChecklistAction(request).join();

        assertThat(response).isNotNull();
        assertThat(response.applied()).isFalse();
        assertThat(response.updatedProblemStatement()).isEqualTo("Original problem statement");
    }
}
