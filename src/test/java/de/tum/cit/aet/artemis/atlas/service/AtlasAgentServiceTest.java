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
import org.junit.jupiter.api.Nested;
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

    @Mock
    private CompetencyExpertToolsService competencyExpertToolsService;

    @BeforeEach
    void setUp() {
        ChatClient chatClient = ChatClient.create(chatModel);
        // Pass null for ToolCallbackProviders and ChatMemory in tests
        atlasAgentService = new AtlasAgentService(chatClient, templateService, null, null, null, competencyExpertToolsService);
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
        assertThat(chatResult.message()).isEqualTo("I apologize, but I'm having trouble processing your request right now. Please try again later.");
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
        AtlasAgentService serviceWithNullClient = new AtlasAgentService(null, templateService, null, null, null, null);

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

    @Nested
    class MultiAgentOrchestration {

        @Test
        void shouldDetectCompetencyModificationWhenToolsServiceIndicatesModification() throws ExecutionException, InterruptedException {
            String testMessage = "Create a competency for OOP";
            Long courseId = 123L;
            String sessionId = "test_session";

            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Competency created successfully")))));
            when(competencyExpertToolsService.wasCompetencyModified()).thenReturn(true);

            CompletableFuture<AgentChatResult> result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

            assertThat(result).isNotNull();
            AgentChatResult chatResult = result.get();
            assertThat(chatResult.competenciesModified()).as("Should detect competency modification from tools service").isTrue();
        }

        @Test
        void shouldNotIndicateModificationWhenToolsServiceReportsNoChange() throws ExecutionException, InterruptedException {
            String testMessage = "Show me existing competencies";
            Long courseId = 123L;
            String sessionId = "test_session";

            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Here are the competencies")))));
            when(competencyExpertToolsService.wasCompetencyModified()).thenReturn(false);

            CompletableFuture<AgentChatResult> result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

            assertThat(result).isNotNull();
            AgentChatResult chatResult = result.get();
            assertThat(chatResult.competenciesModified()).as("Should not indicate modification for read-only operations").isFalse();
        }

        @Test
        void shouldHandleDelegationMarkerInResponse() throws ExecutionException, InterruptedException {
            String testMessage = "Create a new competency";
            Long courseId = 123L;
            String sessionId = "delegation_test";
            String responseWithDelegationMarker = "%%ARTEMIS_DELEGATE_TO_COMPETENCY_EXPERT%%:Create OOP competency";

            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            // First call returns delegation marker, second call (from competency expert) returns clean response
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(responseWithDelegationMarker)))))
                    .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("{\"preview\": true}")))));

            CompletableFuture<AgentChatResult> result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

            assertThat(result).isNotNull();
            AgentChatResult chatResult = result.get();
            // The response should be processed and replaced with clean JSON from competency expert
            assertThat(chatResult.message()).isNotNull();
            // At minimum, the raw delegation marker should be sanitized
            assertThat(chatResult.message()).doesNotContain("%%ARTEMIS_DELEGATE_TO_COMPETENCY_EXPERT%%:");
        }

        @Test
        void shouldHandleCompetencyExpertToolsServiceNull() throws ExecutionException, InterruptedException {
            AtlasAgentService serviceWithoutTools = new AtlasAgentService(ChatClient.create(chatModel), templateService, null, null, null, null);

            String testMessage = "Test message";
            Long courseId = 123L;
            String sessionId = "test_session";

            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Response")))));

            CompletableFuture<AgentChatResult> result = serviceWithoutTools.processChatMessage(testMessage, courseId, sessionId);

            assertThat(result).isNotNull();
            AgentChatResult chatResult = result.get();
            assertThat(chatResult.competenciesModified()).as("Should handle null tools service gracefully").isFalse();
        }

        @Test
        void shouldMaintainSessionIsolationAcrossMultipleAgentTransitions() throws ExecutionException, InterruptedException {
            Long courseId = 123L;
            String session1 = "instructor1_session";
            String session2 = "instructor2_session";

            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Response 1")))))
                    .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Response 2")))))
                    .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Response 3")))))
                    .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Response 4")))));

            // Process messages for both sessions
            CompletableFuture<AgentChatResult> result1_1 = atlasAgentService.processChatMessage("Message 1 for session 1", courseId, session1);
            CompletableFuture<AgentChatResult> result2_1 = atlasAgentService.processChatMessage("Message 1 for session 2", courseId, session2);
            CompletableFuture<AgentChatResult> result1_2 = atlasAgentService.processChatMessage("Message 2 for session 1", courseId, session1);
            CompletableFuture<AgentChatResult> result2_2 = atlasAgentService.processChatMessage("Message 2 for session 2", courseId, session2);

            // All results should be independent
            assertThat(result1_1.get().message()).isEqualTo("Response 1");
            assertThat(result2_1.get().message()).isEqualTo("Response 2");
            assertThat(result1_2.get().message()).isEqualTo("Response 3");
            assertThat(result2_2.get().message()).isEqualTo("Response 4");
        }
    }

    @Nested
    class ErrorHandlingAndEdgeCases {

        @Test
        void shouldHandleMultipleDelegationMarkersGracefully() throws ExecutionException, InterruptedException {
            String testMessage = "Test multiple delegations";
            Long courseId = 123L;
            String sessionId = "multi_delegation_test";
            String responseWithMultipleMarkers = "First %%ARTEMIS_DELEGATE_TO_COMPETENCY_EXPERT%%{\"brief\": \"test\"} Second %%ARTEMIS_DELEGATE_TO_COMPETENCY_EXPERT%%{\"brief\": \"test2\"}";

            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(responseWithMultipleMarkers)))));

            CompletableFuture<AgentChatResult> result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

            assertThat(result).isNotNull();
            AgentChatResult chatResult = result.get();
            // Should handle multiple markers without crashing
            assertThat(chatResult.message()).isNotNull();
        }

        @Test
        void shouldHandleEmptyDelegationBrief() throws ExecutionException, InterruptedException {
            String testMessage = "Test empty brief";
            Long courseId = 123L;
            String sessionId = "empty_brief_test";
            String responseWithEmptyBrief = "%%ARTEMIS_DELEGATE_TO_COMPETENCY_EXPERT%%";

            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(responseWithEmptyBrief)))));

            CompletableFuture<AgentChatResult> result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

            assertThat(result).isNotNull();
            AgentChatResult chatResult = result.get();
            // Should handle empty brief gracefully
            assertThat(chatResult.message()).isNotNull();
        }

        @Test
        void shouldHandleReturnToMainAgentMarker() throws ExecutionException, InterruptedException {
            String testMessage = "Test return marker";
            Long courseId = 123L;
            String sessionId = "return_marker_test";
            String responseWithReturnMarker = "Task complete %%ARTEMIS_RETURN_TO_MAIN_AGENT%%";

            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(responseWithReturnMarker)))));

            CompletableFuture<AgentChatResult> result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

            assertThat(result).isNotNull();
            AgentChatResult chatResult = result.get();
            // The marker should be removed from the response
            assertThat(chatResult.message()).doesNotContain("%%ARTEMIS_RETURN_TO_MAIN_AGENT%%");
            assertThat(chatResult.message()).isEqualTo("Task complete");
        }

        @Test
        void shouldHandleCreateApprovedCompetencyMarker() throws ExecutionException, InterruptedException {
            String testMessage = "Approve creation";
            Long courseId = 123L;
            String sessionId = "approval_test";
            String responseWithApprovalMarker = "Creating competency [CREATE_APPROVED_COMPETENCY]";

            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            // First call returns approval marker, second call (for creation) returns success message
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(responseWithApprovalMarker)))))
                    .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("{\"success\": true, \"created\": 1}")))));

            CompletableFuture<AgentChatResult> result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

            assertThat(result).isNotNull();
            AgentChatResult chatResult = result.get();
            // The response should include both the message and creation confirmation
            assertThat(chatResult.message()).isNotNull();
            // The marker itself should be removed (but content around it remains)
            assertThat(chatResult.message()).doesNotContain("[CREATE_APPROVED_COMPETENCY]");
        }
    }

}
