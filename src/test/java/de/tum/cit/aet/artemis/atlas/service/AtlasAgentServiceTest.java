package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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

@ExtendWith(MockitoExtension.class)
class AtlasAgentServiceTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private AtlasPromptTemplateService templateService;

    private AtlasAgentService atlasAgentService;

    @BeforeEach
    void setUp() {
        ChatClient chatClient = ChatClient.create(chatModel);
        atlasAgentService = new AtlasAgentService(chatClient, templateService);
    }

    @Test
    void testProcessChatMessage_Success() throws ExecutionException, InterruptedException {
        // Given
        String testMessage = "Help me create competencies for Java programming";
        Long courseId = 123L;
        String expectedResponse = "I can help you create competencies for Java programming. Here are my suggestions:\n1. Object-Oriented Programming (APPLY)\n2. Data Structures (UNDERSTAND)\n3. Algorithms (ANALYZE)";

        when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(expectedResponse)))));

        // When
        CompletableFuture<String> result = atlasAgentService.processChatMessage(testMessage, courseId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.get()).isEqualTo(expectedResponse);
    }

    @Test
    void testProcessChatMessage_EmptyResponse() throws ExecutionException, InterruptedException {
        // Given
        String testMessage = "Test message";
        Long courseId = 456L;
        String emptyResponse = "";

        when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(emptyResponse)))));

        // When
        CompletableFuture<String> result = atlasAgentService.processChatMessage(testMessage, courseId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.get()).isEqualTo("I apologize, but I couldn't generate a response.");
    }

    @Test
    void testProcessChatMessage_NullResponse() throws ExecutionException, InterruptedException {
        // Given
        String testMessage = "Test message";
        Long courseId = 789L;

        when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(null)))));

        // When
        CompletableFuture<String> result = atlasAgentService.processChatMessage(testMessage, courseId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.get()).isEqualTo("I apologize, but I couldn't generate a response.");
    }

    @Test
    void testProcessChatMessage_WhitespaceOnlyResponse() throws ExecutionException, InterruptedException {
        // Given
        String testMessage = "Test message";
        Long courseId = 321L;
        String whitespaceResponse = "   \n\t  ";

        when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(whitespaceResponse)))));

        // When
        CompletableFuture<String> result = atlasAgentService.processChatMessage(testMessage, courseId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.get()).isEqualTo("I apologize, but I couldn't generate a response.");
    }

    @Test
    void testProcessChatMessage_ExceptionHandling() throws ExecutionException, InterruptedException {
        // Given
        String testMessage = "Test message";
        Long courseId = 654L;

        when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
        // Mock the ChatModel to throw an exception
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("ChatModel error"));

        // When
        CompletableFuture<String> result = atlasAgentService.processChatMessage(testMessage, courseId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.get()).isEqualTo("I apologize, but I'm having trouble processing your request right now. Please try again later.");
    }

    @Test
    void testIsAvailable_WithValidChatClient() {
        // When
        boolean available = atlasAgentService.isAvailable();

        // Then
        assertThat(available).isTrue();
    }

    @Test
    void testIsAvailable_WithNullChatClient() {
        // Given
        AtlasAgentService serviceWithNullClient = new AtlasAgentService(null, templateService);

        // When
        boolean available = serviceWithNullClient.isAvailable();

        // Then
        assertThat(available).isFalse();
    }
}
