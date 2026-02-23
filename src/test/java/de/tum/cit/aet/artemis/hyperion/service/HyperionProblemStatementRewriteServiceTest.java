package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteResponseDTO;

class HyperionProblemStatementRewriteServiceTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private LLMTokenUsageService llmTokenUsageService;

    @Mock
    private UserTestRepository userRepository;

    private HyperionProblemStatementRewriteService hyperionProblemStatementRewriteService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        ChatClient chatClient = ChatClient.create(chatModel);
        var templateService = new HyperionPromptTemplateService();
        this.hyperionProblemStatementRewriteService = new HyperionProblemStatementRewriteService(chatClient, templateService, llmTokenUsageService, userRepository);
    }

    @Test
    void rewriteProblemStatement_returnsRewrittenText() {
        String rewritten = "Rewritten statement";
        ChatResponse chatResponse = org.mockito.Mockito.mock(ChatResponse.class);
        var generation = new Generation(new AssistantMessage(rewritten));
        when(chatResponse.getResult()).thenReturn(generation);

        var metadata = org.mockito.Mockito.mock(org.springframework.ai.chat.metadata.ChatResponseMetadata.class);
        var usage = org.mockito.Mockito.mock(org.springframework.ai.chat.metadata.Usage.class);
        when(usage.getPromptTokens()).thenReturn(10);
        when(usage.getCompletionTokens()).thenReturn(20);
        when(metadata.getUsage()).thenReturn(usage);
        when(chatResponse.getMetadata()).thenReturn(metadata);

        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> chatResponse);

        var course = new Course();
        course.setId(123L);
        course.setTitle("Test Course");
        course.setDescription("Test Description");
        ProblemStatementRewriteResponseDTO resp = hyperionProblemStatementRewriteService.rewriteProblemStatement(course, "Original");
        assertThat(resp).isNotNull();
        assertThat(resp.improved()).isTrue();
        assertThat(resp.rewrittenText()).isEqualTo(rewritten);
        verify(llmTokenUsageService).trackChatResponseTokenUsage(eq(chatResponse), eq(LLMServiceType.HYPERION), eq("HYPERION_PROBLEM_REWRITE"), any());
    }

    @Test
    void rewriteProblemStatement_throwsExceptionOnAIFailure() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("AI service unavailable"));

        var course = new Course();
        course.setId(123L);
        assertThatThrownBy(() -> hyperionProblemStatementRewriteService.rewriteProblemStatement(course, "Original")).isInstanceOf(InternalServerErrorAlertException.class);
    }

    @Test
    void rewriteProblemStatement_throwsExceptionOnNullResponse() {
        ChatResponse chatResponse = org.mockito.Mockito.mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(null);
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> chatResponse);

        var course = new Course();
        course.setId(123L);
        assertThatThrownBy(() -> hyperionProblemStatementRewriteService.rewriteProblemStatement(course, "Original")).isInstanceOf(InternalServerErrorAlertException.class);
    }

    @Test
    void rewriteProblemStatement_throwsExceptionOnEmptyInput() {
        var course = new Course();
        course.setId(123L);
        assertThatThrownBy(() -> hyperionProblemStatementRewriteService.rewriteProblemStatement(course, "")).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void rewriteProblemStatement_throwsExceptionWhenChatClientNull() {
        var templateService = new HyperionPromptTemplateService();
        var serviceWithoutClient = new HyperionProblemStatementRewriteService(null, templateService, llmTokenUsageService, userRepository);

        var course = new Course();
        course.setId(123L);
        assertThatThrownBy(() -> serviceWithoutClient.rewriteProblemStatement(course, "Original")).isInstanceOf(InternalServerErrorAlertException.class);
    }

    @Test
    void rewriteProblemStatement_returnsNotImprovedWhenSameText() {
        String original = "Same statement";
        ChatResponse chatResponse = org.mockito.Mockito.mock(ChatResponse.class);
        var generation = new Generation(new AssistantMessage(original));
        when(chatResponse.getResult()).thenReturn(generation);

        var metadata = org.mockito.Mockito.mock(org.springframework.ai.chat.metadata.ChatResponseMetadata.class);
        var usage = org.mockito.Mockito.mock(org.springframework.ai.chat.metadata.Usage.class);
        when(usage.getPromptTokens()).thenReturn(10);
        when(usage.getCompletionTokens()).thenReturn(20);
        when(metadata.getUsage()).thenReturn(usage);
        when(chatResponse.getMetadata()).thenReturn(metadata);

        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> chatResponse);

        var course = new Course();
        course.setId(123L);
        ProblemStatementRewriteResponseDTO resp = hyperionProblemStatementRewriteService.rewriteProblemStatement(course, original);
        assertThat(resp.improved()).isFalse();
    }
}
