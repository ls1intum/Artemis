package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.LLMRequest;
import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;

class HyperionTokenTrackingServiceTest {

    @Mock
    private LLMTokenUsageService llmTokenUsageService;

    @Mock
    private UserTestRepository userRepository;

    private HyperionTokenTrackingService tokenTrackingService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        when(llmTokenUsageService.buildLLMRequest(any(), anyInt(), anyInt(), any())).thenReturn(new LLMRequest("model", 10, 0.0f, 20, 0.0f, "pipeline"));
        tokenTrackingService = new HyperionTokenTrackingService(llmTokenUsageService, userRepository);
    }

    @Test
    void extractResponseText_returnsNullForNullResponse() {
        assertThat(HyperionTokenTrackingService.extractResponseText(null)).isNull();
    }

    @Test
    void extractResponseText_returnsNullWhenResultIsNull() {
        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(null);
        assertThat(HyperionTokenTrackingService.extractResponseText(chatResponse)).isNull();
    }

    @Test
    void extractResponseText_returnsNullWhenOutputIsNull() {
        ChatResponse chatResponse = mock(ChatResponse.class);
        var generation = mock(Generation.class);
        when(generation.getOutput()).thenReturn(null);
        when(chatResponse.getResult()).thenReturn(generation);
        assertThat(HyperionTokenTrackingService.extractResponseText(chatResponse)).isNull();
    }

    @Test
    void extractResponseText_returnsTextFromValidResponse() {
        String expectedText = "Generated content";
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(expectedText))));
        assertThat(HyperionTokenTrackingService.extractResponseText(chatResponse)).isEqualTo(expectedText);
    }

    @Test
    void trackTokenUsage_doesNothingForNullResponse() {
        tokenTrackingService.trackTokenUsage(null, 1L, "PIPELINE");
        verify(llmTokenUsageService, never()).saveLLMTokenUsage(any(), any(), any());
    }

    @Test
    void trackTokenUsage_doesNothingWhenMetadataIsNull() {
        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getMetadata()).thenReturn(null);
        tokenTrackingService.trackTokenUsage(chatResponse, 1L, "PIPELINE");
        verify(llmTokenUsageService, never()).saveLLMTokenUsage(any(), any(), any());
    }

    @Test
    void trackTokenUsage_doesNothingWhenUsageIsNull() {
        ChatResponse chatResponse = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(metadata.getUsage()).thenReturn(null);
        when(chatResponse.getMetadata()).thenReturn(metadata);
        tokenTrackingService.trackTokenUsage(chatResponse, 1L, "PIPELINE");
        verify(llmTokenUsageService, never()).saveLLMTokenUsage(any(), any(), any());
    }

    @Test
    @WithMockUser(username = "instructor1")
    void trackTokenUsage_savesUsageForValidResponse() {
        ChatResponse chatResponse = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(15);
        when(usage.getCompletionTokens()).thenReturn(25);
        when(metadata.getUsage()).thenReturn(usage);
        when(metadata.getModel()).thenReturn("gpt-4o");
        when(chatResponse.getMetadata()).thenReturn(metadata);
        when(userRepository.findIdByLogin("instructor1")).thenReturn(Optional.of(42L));

        tokenTrackingService.trackTokenUsage(chatResponse, 123L, "TEST_PIPELINE");

        verify(llmTokenUsageService).buildLLMRequest("gpt-4o", 15, 25, "TEST_PIPELINE");
        verify(llmTokenUsageService).saveLLMTokenUsage(any(), eq(LLMServiceType.HYPERION), any());
    }

    @Test
    void trackTokenUsage_handlesNullModel() {
        ChatResponse chatResponse = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(10);
        when(usage.getCompletionTokens()).thenReturn(20);
        when(metadata.getUsage()).thenReturn(usage);
        when(metadata.getModel()).thenReturn(null);
        when(chatResponse.getMetadata()).thenReturn(metadata);

        tokenTrackingService.trackTokenUsage(chatResponse, 1L, "PIPELINE");

        verify(llmTokenUsageService).buildLLMRequest(eq(""), anyInt(), anyInt(), anyString());
    }

    @Test
    void trackTokenUsage_handlesNullTokenCounts() {
        ChatResponse chatResponse = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(null);
        when(usage.getCompletionTokens()).thenReturn(null);
        when(metadata.getUsage()).thenReturn(usage);
        when(metadata.getModel()).thenReturn("model");
        when(chatResponse.getMetadata()).thenReturn(metadata);

        tokenTrackingService.trackTokenUsage(chatResponse, 1L, "PIPELINE");

        verify(llmTokenUsageService).buildLLMRequest("model", 0, 0, "PIPELINE");
    }

    @Test
    void trackTokenUsage_handlesNullCourseId() {
        ChatResponse chatResponse = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(10);
        when(usage.getCompletionTokens()).thenReturn(20);
        when(metadata.getUsage()).thenReturn(usage);
        when(metadata.getModel()).thenReturn("model");
        when(chatResponse.getMetadata()).thenReturn(metadata);

        // Should not throw even with null courseId
        tokenTrackingService.trackTokenUsage(chatResponse, null, "PIPELINE");

        verify(llmTokenUsageService).saveLLMTokenUsage(any(), eq(LLMServiceType.HYPERION), any());
    }

    @Test
    void trackTokenUsage_doesNotThrowWhenSaveFails() {
        ChatResponse chatResponse = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(10);
        when(usage.getCompletionTokens()).thenReturn(20);
        when(metadata.getUsage()).thenReturn(usage);
        when(metadata.getModel()).thenReturn("model");
        when(chatResponse.getMetadata()).thenReturn(metadata);
        when(llmTokenUsageService.saveLLMTokenUsage(any(), any(), any())).thenThrow(new RuntimeException("DB error"));

        // Should swallow the exception and not propagate it
        tokenTrackingService.trackTokenUsage(chatResponse, 1L, "PIPELINE");

        verify(llmTokenUsageService).saveLLMTokenUsage(any(), any(), any());
    }

    @Test
    @WithMockUser(username = "instructor1")
    void trackTokenUsage_passesCorrectPipelineId() {
        ChatResponse chatResponse = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(5);
        when(usage.getCompletionTokens()).thenReturn(10);
        when(metadata.getUsage()).thenReturn(usage);
        when(metadata.getModel()).thenReturn("gpt-4o");
        when(chatResponse.getMetadata()).thenReturn(metadata);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LLMRequest>> requestCaptor = ArgumentCaptor.forClass(List.class);

        tokenTrackingService.trackTokenUsage(chatResponse, 99L, "HYPERION_PROBLEM_GENERATION");

        verify(llmTokenUsageService).saveLLMTokenUsage(requestCaptor.capture(), eq(LLMServiceType.HYPERION), any());
        assertThat(requestCaptor.getValue()).hasSize(1);
    }
}
