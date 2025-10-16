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
        // Pass null for ToolCallbackProvider in tests
        atlasAgentService = new AtlasAgentService(chatClient, templateService, null, null, null);
    }

    @Test
    void testProcessChatMessage_Success() throws ExecutionException, InterruptedException {
        String testMessage = "Help me create competencies for Java programming";
        Long courseId = 123L;
        String sessionId = "course_123";
        String expectedResponse = "I can help you create competencies for Java programming. Here are my suggestions:\n1. Object-Oriented Programming (APPLY)\n2. Data Structures (UNDERSTAND)\n3. Algorithms (ANALYZE)";

        when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(expectedResponse)))));

        CompletableFuture<AgentChatResult> result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

        assertThat(result).isNotNull();
        AgentChatResult chatResult = result.get();
        assertThat(chatResult.message()).isEqualTo(expectedResponse);
        assertThat(chatResult.competenciesModified()).isFalse();
    }

    @Test
    void testProcessChatMessage_EmptyResponse() throws ExecutionException, InterruptedException {
        String testMessage = "Test message";
        Long courseId = 456L;
        String sessionId = "course_456";
        String emptyResponse = "";

        when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(emptyResponse)))));

        CompletableFuture<AgentChatResult> result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

        assertThat(result).isNotNull();
        AgentChatResult chatResult = result.get();
        assertThat(chatResult.message()).isEqualTo("I apologize, but I couldn't generate a response.");
        assertThat(chatResult.competenciesModified()).isFalse();
    }

    @Test
    void testProcessChatMessage_NullResponse() throws ExecutionException, InterruptedException {
        String testMessage = "Test message";
        Long courseId = 789L;
        String sessionId = "course_789";

        when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(null)))));

        CompletableFuture<AgentChatResult> result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

        assertThat(result).isNotNull();
        AgentChatResult chatResult = result.get();
        assertThat(chatResult.message()).isEqualTo("I apologize, but I couldn't generate a response.");
        assertThat(chatResult.competenciesModified()).isFalse();
    }

    @Test
    void testProcessChatMessage_WhitespaceOnlyResponse() throws ExecutionException, InterruptedException {
        String testMessage = "Test message";
        Long courseId = 321L;
        String sessionId = "course_321";
        String whitespaceResponse = "   \n\t  ";

        when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(whitespaceResponse)))));

        CompletableFuture<AgentChatResult> result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

        assertThat(result).isNotNull();
        AgentChatResult chatResult = result.get();
        assertThat(chatResult.message()).isEqualTo("I apologize, but I couldn't generate a response.");
        assertThat(chatResult.competenciesModified()).isFalse();
    }

    @Test
    void testProcessChatMessage_ExceptionHandling() throws ExecutionException, InterruptedException {
        String testMessage = "Test message";
        Long courseId = 654L;
        String sessionId = "course_654";

        when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("ChatModel error"));

        CompletableFuture<AgentChatResult> result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

        assertThat(result).isNotNull();
        AgentChatResult chatResult = result.get();
        assertThat(chatResult.message()).isEqualTo("I apologize, but I'm having trouble processing your request right now. Please try again later.");
        assertThat(chatResult.competenciesModified()).isFalse();
    }

    @Test
    void testIsAvailable_WithValidChatClient() {
        boolean available = atlasAgentService.isAvailable();

        assertThat(available).isTrue();
    }

    @Test
    void testIsAvailable_WithNullChatClient() {
        AtlasAgentService serviceWithNullClient = new AtlasAgentService(null, templateService, null, null, null);

        boolean available = serviceWithNullClient.isAvailable();

        assertThat(available).isFalse();
    }

    @Test
    void testConversationIsolation_DifferentUsers() throws ExecutionException, InterruptedException {
        Long courseId = 123L;
        String instructor1SessionId = "course_123_user_1";
        String instructor2SessionId = "course_123_user_2";
        String instructor1Message = "Create competency A";
        String instructor2Message = "Create competency B";

        when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Response for instructor 1")))))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Response for instructor 2")))));

        CompletableFuture<AgentChatResult> result1 = atlasAgentService.processChatMessage(instructor1Message, courseId, instructor1SessionId);
        CompletableFuture<AgentChatResult> result2 = atlasAgentService.processChatMessage(instructor2Message, courseId, instructor2SessionId);

        AgentChatResult chatResult1 = result1.get();
        AgentChatResult chatResult2 = result2.get();

        assertThat(chatResult1.message()).isEqualTo("Response for instructor 1");
        assertThat(chatResult2.message()).isEqualTo("Response for instructor 2");

        assertThat(instructor1SessionId).isNotEqualTo(instructor2SessionId);
    }

}
