package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.AtlasAgentChatResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.AtlasAgentHistoryMessageDTO;

@ExtendWith(MockitoExtension.class)
class AtlasAgentServiceTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private AtlasPromptTemplateService templateService;

    @Mock
    private ChatMemory chatMemory;

    private AtlasAgentService atlasAgentService;

    @BeforeEach
    void setUp() {
        ChatClient chatClient = ChatClient.create(chatModel);
        atlasAgentService = new AtlasAgentService(chatClient, templateService, null, null, chatMemory, null, 0.2);
    }

    @Test
    void testProcessChatMessage_Success() {
        String testMessage = "Help me create competencies for Java programming";
        Long courseId = 123L;
        String sessionId = "course_123_user_456";
        String expectedResponse = "I can help you create competencies for Java programming. Here are my suggestions:\n1. Object-Oriented Programming (APPLY)\n2. Data Structures (UNDERSTAND)\n3. Algorithms (ANALYZE)";

        when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(expectedResponse)))));

        AtlasAgentChatResponseDTO result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

        assertThat(result).isNotNull();

        assertThat(result.message()).isEqualTo(expectedResponse);
        assertThat(result.competenciesModified()).isFalse();
    }

    @Test
    void testProcessChatMessage_EmptyResponse() {
        String testMessage = "Test message";
        Long courseId = 456L;
        String sessionId = "course_456_user_789";
        String emptyResponse = "";

        when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(emptyResponse)))));

        AtlasAgentChatResponseDTO result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

        assertThat(result).isNotNull();
        assertThat(result.message()).isEqualTo("I apologize, but I couldn't generate a response.");
        assertThat(result.competenciesModified()).isFalse();
    }

    @Test
    void testProcessChatMessage_NullResponse() {
        String testMessage = "Test message";
        Long courseId = 789L;
        String sessionId = "course_789_user_101";

        when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("")))));

        AtlasAgentChatResponseDTO result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

        assertThat(result).isNotNull();

        assertThat(result.message()).isEqualTo("I apologize, but I couldn't generate a response.");
        assertThat(result.competenciesModified()).isFalse();
    }

    @Test
    void testProcessChatMessage_WhitespaceOnlyResponse() {
        String testMessage = "Test message";
        Long courseId = 321L;
        String sessionId = "course_321_user_202";
        String whitespaceResponse = "   \n\t  ";

        when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(whitespaceResponse)))));

        AtlasAgentChatResponseDTO result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

        assertThat(result).isNotNull();

        assertThat(result.message()).isEqualTo("I apologize, but I couldn't generate a response.");
        assertThat(result.competenciesModified()).isFalse();
    }

    @Test
    void testProcessChatMessage_ExceptionHandling() {
        String testMessage = "Test message";
        Long courseId = 654L;
        String sessionId = "course_654_user_303";

        when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("ChatModel error"));

        AtlasAgentChatResponseDTO result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

        assertThat(result).isNotNull();

        assertThat(result.message()).isEqualTo("I apologize, but I'm having trouble processing your request right now. Please try again later.");
        assertThat(result.competenciesModified()).isFalse();
    }

    @Test
    void testIsAvailable_WithValidChatClient() {
        boolean available = atlasAgentService.isAvailable();

        assertThat(available).isTrue();
    }

    @Test
    void testIsAvailable_WithNullChatClient() {
        AtlasAgentService serviceWithNullClient = new AtlasAgentService(null, templateService, null, null, chatMemory, null, 0.2);
        boolean available = serviceWithNullClient.isAvailable();

        assertThat(available).isFalse();
    }

    @Test
    void testIsAvailable_WithNullChatMemory() {
        ChatClient chatClient = ChatClient.create(chatModel);
        AtlasAgentService serviceWithNullMemory = new AtlasAgentService(chatClient, templateService, null, null, null, null, 0.2);

        boolean available = serviceWithNullMemory.isAvailable();

        assertThat(available).isFalse();
    }

    @Test
    void testConversationIsolation_DifferentUsers() {
        Long courseId = 123L;
        String instructor1SessionId = "course_123_user_1";
        String instructor2SessionId = "course_123_user_2";
        String instructor1Message = "Create competency A";
        String instructor2Message = "Create competency B";

        when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Response for instructor 1")))))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Response for instructor 2")))));

        AtlasAgentChatResponseDTO result1 = atlasAgentService.processChatMessage(instructor1Message, courseId, instructor1SessionId);
        AtlasAgentChatResponseDTO result2 = atlasAgentService.processChatMessage(instructor2Message, courseId, instructor2SessionId);

        assertThat(result1.message()).isEqualTo("Response for instructor 1");
        assertThat(result2.message()).isEqualTo("Response for instructor 2");

        assertThat(instructor1SessionId).isNotEqualTo(instructor2SessionId);
    }

    @Test
    void testGetConversationHistoryAsDTO_Success() {
        String sessionId = "course_123_user_456";
        Message userMessage = new UserMessage("What are competencies?");
        Message assistantMessage = new AssistantMessage("Competencies are learning objectives that define what students should know and be able to do.");
        List<Message> messages = List.of(userMessage, assistantMessage);

        when(chatMemory.get(sessionId)).thenReturn(messages);

        List<AtlasAgentHistoryMessageDTO> result = atlasAgentService.getConversationHistoryAsDTO(sessionId);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().content()).isEqualTo("What are competencies?");
        assertThat(result.getFirst().isUser()).isTrue();
        assertThat(result.get(1).content()).isEqualTo("Competencies are learning objectives that define what students should know and be able to do.");
        assertThat(result.get(1).isUser()).isFalse();
        verify(chatMemory).get(sessionId);
    }

    @Test
    void testGetConversationHistoryAsDTO_EmptyHistory() {
        String sessionId = "course_789_user_101";
        List<Message> emptyMessages = List.of();

        when(chatMemory.get(sessionId)).thenReturn(emptyMessages);

        List<AtlasAgentHistoryMessageDTO> result = atlasAgentService.getConversationHistoryAsDTO(sessionId);

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
        verify(chatMemory).get(sessionId);
    }

    @Test
    void testGetConversationHistoryAsDTO_NullChatMemory() {
        String sessionId = "course_456_user_789";
        AtlasAgentService serviceWithNullMemory = new AtlasAgentService(ChatClient.create(chatModel), templateService, null, null, null, null, 0.2);

        List<AtlasAgentHistoryMessageDTO> result = serviceWithNullMemory.getConversationHistoryAsDTO(sessionId);

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    void testGetConversationHistoryAsDTO_ExceptionHandling() {
        String sessionId = "course_321_user_202";

        when(chatMemory.get(sessionId)).thenThrow(new RuntimeException("Database error"));

        List<AtlasAgentHistoryMessageDTO> result = atlasAgentService.getConversationHistoryAsDTO(sessionId);

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
        verify(chatMemory).get(sessionId);
    }

    @Test
    void testGetConversationHistoryAsDTO_MultipleMessages() {
        String sessionId = "course_999_user_888";
        List<Message> messages = List.of(new UserMessage("Hello"), new AssistantMessage("Hi there!"), new UserMessage("How are you?"),
                new AssistantMessage("I'm doing well, thanks!"));

        when(chatMemory.get(sessionId)).thenReturn(messages);

        List<AtlasAgentHistoryMessageDTO> result = atlasAgentService.getConversationHistoryAsDTO(sessionId);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(4);
        assertThat(result.getFirst().isUser()).isTrue();
        assertThat(result.get(1).isUser()).isFalse();
        assertThat(result.get(2).isUser()).isTrue();
        assertThat(result.get(3).isUser()).isFalse();
        verify(chatMemory).get(sessionId);
    }

    @Nested
    class MultiAgentOrchestration {

        @Test
        void shouldDetectCompetencyModificationWhenToolsServiceIndicatesModification() {
            String testMessage = "Create a competency for OOP";
            Long courseId = 123L;
            String sessionId = "test_session";

            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            // Simulate competency modification during chat model call (mimics tool execution)
            when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> {
                AtlasAgentService.markCompetencyModified();
                return new ChatResponse(List.of(new Generation(new AssistantMessage("Competency created successfully"))));
            });

            AtlasAgentChatResponseDTO result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

            assertThat(result).isNotNull();

            assertThat(result.competenciesModified()).as("Should detect competency modification from tools service").isTrue();
        }

        @Test
        void shouldNotIndicateModificationWhenToolsServiceReportsNoChange() {
            String testMessage = "Show me existing competencies";
            Long courseId = 123L;
            String sessionId = "test_session";

            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            // No modification - just return response without calling markCompetencyModified
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Here are the competencies")))));

            AtlasAgentChatResponseDTO result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

            assertThat(result).isNotNull();

            assertThat(result.competenciesModified()).as("Should not indicate modification for read-only operations").isFalse();
        }

        @Test
        void shouldHandleDelegationMarkerInResponse() {
            String testMessage = "Create a new competency";
            Long courseId = 123L;
            String sessionId = "delegation_test";
            String responseWithDelegationMarker = "%%ARTEMIS_DELEGATE_TO_COMPETENCY_EXPERT%%:Create OOP competency";

            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            // First call returns delegation marker, second call (from competency expert) returns clean response
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(responseWithDelegationMarker)))))
                    .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("{\"preview\": true}")))));

            AtlasAgentChatResponseDTO result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

            assertThat(result).isNotNull();

            // The response should be processed and replaced with clean JSON from competency expert
            assertThat(result.message()).isNotNull();
            // At minimum, the raw delegation marker should be sanitized
            assertThat(result.message()).doesNotContain("%%ARTEMIS_DELEGATE_TO_COMPETENCY_EXPERT%%:");
        }

        @Test
        void shouldHandleCompetencyExpertToolsServiceNull() {
            AtlasAgentService serviceWithoutTools = new AtlasAgentService(ChatClient.create(chatModel), templateService, null, null, null, null, 0.2);

            String testMessage = "Test message";
            Long courseId = 123L;
            String sessionId = "test_session";

            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Response")))));

            AtlasAgentChatResponseDTO result = serviceWithoutTools.processChatMessage(testMessage, courseId, sessionId);

            assertThat(result).isNotNull();

            assertThat(result.competenciesModified()).as("Should handle null tools service gracefully").isFalse();
        }

        @Test
        void shouldMaintainSessionIsolationAcrossMultipleAgentTransitions() {
            Long courseId = 123L;
            String session1 = "instructor1_session";
            String session2 = "instructor2_session";

            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Response 1")))))
                    .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Response 2")))))
                    .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Response 3")))))
                    .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Response 4")))));

            // Process messages for both sessions
            AtlasAgentChatResponseDTO result1_1 = atlasAgentService.processChatMessage("Message 1 for session 1", courseId, session1);
            AtlasAgentChatResponseDTO result2_1 = atlasAgentService.processChatMessage("Message 1 for session 2", courseId, session2);
            AtlasAgentChatResponseDTO result1_2 = atlasAgentService.processChatMessage("Message 2 for session 1", courseId, session1);
            AtlasAgentChatResponseDTO result2_2 = atlasAgentService.processChatMessage("Message 2 for session 2", courseId, session2);

            // All results should be independent
            assertThat(result1_1.message()).isEqualTo("Response 1");
            assertThat(result2_1.message()).isEqualTo("Response 2");
            assertThat(result1_2.message()).isEqualTo("Response 3");
            assertThat(result2_2.message()).isEqualTo("Response 4");
        }
    }

    @Nested
    class ErrorHandlingAndEdgeCases {

        @Test
        void shouldHandleMultipleDelegationMarkersGracefully() {
            String testMessage = "Test multiple delegations";
            Long courseId = 123L;
            String sessionId = "multi_delegation_test";
            String responseWithMultipleMarkers = "First %%ARTEMIS_DELEGATE_TO_COMPETENCY_EXPERT%%{\"brief\": \"test\"} Second %%ARTEMIS_DELEGATE_TO_COMPETENCY_EXPERT%%{\"brief\": \"test2\"}";

            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(responseWithMultipleMarkers)))));

            AtlasAgentChatResponseDTO result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

            assertThat(result).isNotNull();

            // Should handle multiple markers without crashing
            assertThat(result.message()).isNotNull();
        }

        @Test
        void shouldHandleEmptyDelegationBrief() {
            String testMessage = "Test empty brief";
            Long courseId = 123L;
            String sessionId = "empty_brief_test";
            String responseWithEmptyBrief = "%%ARTEMIS_DELEGATE_TO_COMPETENCY_EXPERT%%";

            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(responseWithEmptyBrief)))));

            AtlasAgentChatResponseDTO result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

            assertThat(result).isNotNull();

            // Should handle empty brief gracefully
            assertThat(result.message()).isNotNull();
        }

        @Test
        void shouldHandleReturnToMainAgentMarker() {
            String testMessage = "Test return marker";
            Long courseId = 123L;
            String sessionId = "return_marker_test";
            String responseWithReturnMarker = "Task complete %%ARTEMIS_RETURN_TO_MAIN_AGENT%%";

            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(responseWithReturnMarker)))));

            AtlasAgentChatResponseDTO result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

            assertThat(result).isNotNull();

            // The marker should be removed from the response
            assertThat(result.message()).doesNotContain("%%ARTEMIS_RETURN_TO_MAIN_AGENT%%");
            assertThat(result.message()).isEqualTo("Task complete");
        }

        @Test
        void shouldHandleCreateApprovedCompetencyMarker() {
            String testMessage = "Approve creation";
            Long courseId = 123L;
            String sessionId = "approval_test";
            String responseWithApprovalMarker = "Creating competency [CREATE_APPROVED_COMPETENCY]";

            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            // First call returns approval marker, second call (for creation) returns success message
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(responseWithApprovalMarker)))))
                    .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("{\"success\": true, \"created\": 1}")))));

            AtlasAgentChatResponseDTO result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

            assertThat(result).isNotNull();

            // The response should include both the message and creation confirmation
            assertThat(result.message()).isNotNull();
            // The marker itself should be removed (but content around it remains)
            assertThat(result.message()).doesNotContain("[CREATE_APPROVED_COMPETENCY]");
        }
    }

    @Nested
    class AtlasAgentHistoryMessageDTOTests {

        private final ObjectMapper objectMapper = new ObjectMapper();

        @Test
        void shouldSerializeToJsonWhenValidData() throws Exception {
            AtlasAgentHistoryMessageDTO dto = new AtlasAgentHistoryMessageDTO("Test message content", true, null);

            String actualJson = objectMapper.writeValueAsString(dto);

            assertThat(actualJson).contains("\"content\":\"Test message content\"");
            assertThat(actualJson).contains("\"isUser\":true");
        }

        @Test
        void shouldDeserializeFromJsonWhenValidJson() throws Exception {
            String json = "{\"content\":\"Deserialized content\",\"isUser\":false}";

            AtlasAgentHistoryMessageDTO actualDto = objectMapper.readValue(json, AtlasAgentHistoryMessageDTO.class);

            assertThat(actualDto.content()).isEqualTo("Deserialized content");
            assertThat(actualDto.isUser()).isFalse();
        }

        @Test
        void shouldExcludeContentWhenEmpty() throws Exception {
            AtlasAgentHistoryMessageDTO dto = new AtlasAgentHistoryMessageDTO("", true, null);

            String actualJson = objectMapper.writeValueAsString(dto);

            assertThat(actualJson).doesNotContain("content");
            assertThat(actualJson).contains("\"isUser\":true");
        }

        @Test
        void shouldExcludeContentWhenNull() throws Exception {
            AtlasAgentHistoryMessageDTO dto = new AtlasAgentHistoryMessageDTO(null, false, null);

            String actualJson = objectMapper.writeValueAsString(dto);

            assertThat(actualJson).doesNotContain("content");
            assertThat(actualJson).contains("\"isUser\":false");
        }
    }

    @Nested
    class ConversationHistoryWithPreviewData {

        @Test
        void shouldExtractSingleCompetencyPreviewFromHistory() {
            String sessionId = "course_123_user_456";
            String responseText = "Here's your competency preview %%PREVIEW_DATA_START%%{\"previews\":[{\"title\":\"OOP Basics\",\"description\":\"Object-Oriented Programming fundamentals\",\"taxonomy\":\"UNDERSTAND\",\"icon\":\"comments\",\"competencyId\":null,\"viewOnly\":null}]}%%PREVIEW_DATA_END%%";
            List<Message> messages = List.of(new UserMessage("Create a competency"), new AssistantMessage(responseText));

            when(chatMemory.get(sessionId)).thenReturn(messages);

            List<AtlasAgentHistoryMessageDTO> result = atlasAgentService.getConversationHistoryAsDTO(sessionId);

            assertThat(result).hasSize(2);
            assertThat(result.getFirst().content()).isEqualTo("Create a competency");
            assertThat(result.getFirst().isUser()).isTrue();
            assertThat(result.getFirst().competencyPreviews()).isNull();

            assertThat(result.get(1).content()).isEqualTo("Here's your competency preview");
            assertThat(result.get(1).isUser()).isFalse();
            assertThat(result.get(1).competencyPreviews()).isNotNull();
            assertThat(result.get(1).competencyPreviews()).hasSize(1);
            assertThat(result.get(1).competencyPreviews().getFirst().title()).isEqualTo("OOP Basics");
        }

        @Test
        void shouldExtractBatchCompetencyPreviewFromHistory() {
            String sessionId = "course_123_user_789";
            String responseText = "Here are multiple competencies %%PREVIEW_DATA_START%%{\"previews\":[{\"title\":\"Comp 1\",\"description\":\"Description 1\",\"taxonomy\":\"REMEMBER\",\"icon\":\"brain\",\"competencyId\":null,\"viewOnly\":null},{\"title\":\"Comp 2\",\"description\":\"Description 2\",\"taxonomy\":\"APPLY\",\"icon\":\"pen-fancy\",\"competencyId\":null,\"viewOnly\":null}]}%%PREVIEW_DATA_END%%";
            List<Message> messages = List.of(new UserMessage("Create multiple competencies"), new AssistantMessage(responseText));

            when(chatMemory.get(sessionId)).thenReturn(messages);

            List<AtlasAgentHistoryMessageDTO> result = atlasAgentService.getConversationHistoryAsDTO(sessionId);

            assertThat(result).hasSize(2);
            assertThat(result.get(1).content()).isEqualTo("Here are multiple competencies");
            assertThat(result.get(1).competencyPreviews()).isNotNull();
            assertThat(result.get(1).competencyPreviews()).hasSize(2);
            assertThat(result.get(1).competencyPreviews().getFirst().title()).isEqualTo("Comp 1");
            assertThat(result.get(1).competencyPreviews().get(1).title()).isEqualTo("Comp 2");
        }

        @Test
        void shouldFilterOutDelegationMarkers() {
            String sessionId = "course_123_user_101";
            List<Message> messages = List.of(new UserMessage("Create a competency"), new AssistantMessage("%%ARTEMIS_DELEGATE_TO_COMPETENCY_EXPERT%%:Create OOP competency"),
                    new AssistantMessage("Competency created successfully"));

            when(chatMemory.get(sessionId)).thenReturn(messages);

            List<AtlasAgentHistoryMessageDTO> result = atlasAgentService.getConversationHistoryAsDTO(sessionId);

            assertThat(result).hasSize(2);
            assertThat(result.getFirst().content()).isEqualTo("Create a competency");
            assertThat(result.get(1).content()).isEqualTo("Competency created successfully");
        }

        @Test
        void shouldFilterOutBriefingMessages() {
            String sessionId = "course_123_user_202";
            List<Message> messages = List.of(new UserMessage("Show me competencies"),
                    new AssistantMessage("TOPIC: Competency Management\nREQUIREMENTS: List all competencies\nCONSTRAINTS: Read-only\nCONTEXT: Course 123"),
                    new AssistantMessage("Here are your competencies"));

            when(chatMemory.get(sessionId)).thenReturn(messages);

            List<AtlasAgentHistoryMessageDTO> result = atlasAgentService.getConversationHistoryAsDTO(sessionId);

            assertThat(result).hasSize(2);
            assertThat(result.getFirst().content()).isEqualTo("Show me competencies");
            assertThat(result.get(1).content()).isEqualTo("Here are your competencies");
            // Briefing message should be filtered out
        }

        @Test
        void shouldHandleMessageWithoutPreviewData() {
            String sessionId = "course_123_user_303";
            List<Message> messages = List.of(new UserMessage("What are competencies?"),
                    new AssistantMessage("Competencies are learning objectives that define what students should know and be able to do."));

            when(chatMemory.get(sessionId)).thenReturn(messages);

            List<AtlasAgentHistoryMessageDTO> result = atlasAgentService.getConversationHistoryAsDTO(sessionId);

            assertThat(result).hasSize(2);
            assertThat(result.getFirst().content()).isEqualTo("What are competencies?");
            assertThat(result.get(1).content()).isEqualTo("Competencies are learning objectives that define what students should know and be able to do.");
            assertThat(result.get(1).competencyPreviews()).isNull();
        }

        @Test
        void shouldHandleMalformedPreviewData() {
            String sessionId = "course_123_user_404";
            String responseText = "Message %%PREVIEW_DATA_START%%{invalid json}%%PREVIEW_DATA_END%%";
            List<Message> messages = List.of(new UserMessage("Test"), new AssistantMessage(responseText));

            when(chatMemory.get(sessionId)).thenReturn(messages);

            List<AtlasAgentHistoryMessageDTO> result = atlasAgentService.getConversationHistoryAsDTO(sessionId);

            assertThat(result).hasSize(2);
            assertThat(result.get(1).content()).isEqualTo("Message");
            assertThat(result.get(1).competencyPreviews()).isNull();
        }

        @Test
        void shouldHandlePreviewDataWithoutEndMarker() {
            String sessionId = "course_123_user_505";
            String responseText = "Message %%PREVIEW_DATA_START%%{\"singlePreview\":{}}";
            List<Message> messages = List.of(new UserMessage("Test"), new AssistantMessage(responseText));

            when(chatMemory.get(sessionId)).thenReturn(messages);

            List<AtlasAgentHistoryMessageDTO> result = atlasAgentService.getConversationHistoryAsDTO(sessionId);

            assertThat(result).hasSize(2);
            // Should return original text when end marker is missing
            assertThat(result.get(1).content()).contains("Message");
            assertThat(result.get(1).competencyPreviews()).isNull();
        }

        @Test
        void shouldHandleMultipleMessagesWithMixedPreviewData() {
            String sessionId = "course_123_user_606";
            String message1 = "First response %%PREVIEW_DATA_START%%{\"previews\":[{\"title\":\"Test 1\",\"description\":\"Desc 1\",\"taxonomy\":\"APPLY\",\"icon\":\"pen-fancy\",\"competencyId\":null,\"viewOnly\":null}]}%%PREVIEW_DATA_END%%";
            String message2 = "Second response without preview";
            String message3 = "Third response %%PREVIEW_DATA_START%%{\"previews\":[{\"title\":\"Test 2\",\"description\":\"Desc 2\",\"taxonomy\":\"ANALYZE\",\"icon\":\"magnifying-glass\",\"competencyId\":null,\"viewOnly\":null}]}%%PREVIEW_DATA_END%%";

            List<Message> messages = List.of(new UserMessage("User message 1"), new AssistantMessage(message1), new UserMessage("User message 2"), new AssistantMessage(message2),
                    new UserMessage("User message 3"), new AssistantMessage(message3));

            when(chatMemory.get(sessionId)).thenReturn(messages);

            List<AtlasAgentHistoryMessageDTO> result = atlasAgentService.getConversationHistoryAsDTO(sessionId);

            assertThat(result).hasSize(6);

            assertThat(result.get(1).content()).isEqualTo("First response");
            assertThat(result.get(1).competencyPreviews()).isNotNull();
            assertThat(result.get(1).competencyPreviews()).hasSize(1);
            assertThat(result.get(1).competencyPreviews().getFirst().title()).isEqualTo("Test 1");

            // Second assistant message without preview
            assertThat(result.get(3).content()).isEqualTo("Second response without preview");
            assertThat(result.get(3).competencyPreviews()).isNull();

            // Third assistant message with batch preview
            assertThat(result.get(5).content()).isEqualTo("Third response");
            assertThat(result.get(5).competencyPreviews()).isNotNull();
            assertThat(result.get(5).competencyPreviews()).hasSize(1);
            assertThat(result.get(5).competencyPreviews().getFirst().title()).isEqualTo("Test 2");
        }

        @Test
        void shouldFilterOutAllInternalMessagesAndKeepUserFacingOnes() {
            String sessionId = "course_123_user_707";
            List<Message> messages = List.of(new UserMessage("Create OOP competency"), new AssistantMessage("%%ARTEMIS_DELEGATE_TO_COMPETENCY_EXPERT%%:Brief"),
                    new AssistantMessage("TOPIC: OOP\nREQUIREMENTS: Create\nCONSTRAINTS: None\nCONTEXT: Course"),
                    new AssistantMessage(
                            "Competency created %%PREVIEW_DATA_START%%{\"singlePreview\":{\"preview\":true,\"title\":\"OOP\",\"description\":\"Test\",\"taxonomy\":\"APPLY\",\"icon\":\"pen-fancy\"}}}%%PREVIEW_DATA_END%%"),
                    new AssistantMessage("%%ARTEMIS_RETURN_TO_MAIN_AGENT%%"), new AssistantMessage("Task completed successfully"));

            when(chatMemory.get(sessionId)).thenReturn(messages);

            List<AtlasAgentHistoryMessageDTO> result = atlasAgentService.getConversationHistoryAsDTO(sessionId);

            // Should only have user message and two assistant messages (competency created and task completed)
            assertThat(result).hasSize(3);
            assertThat(result.getFirst().content()).isEqualTo("Create OOP competency");
            assertThat(result.get(1).content()).isEqualTo("Competency created");
            assertThat(result.get(1).competencyPreviews()).isNull();
            assertThat(result.get(2).content()).isEqualTo("Task completed successfully");
        }
    }

}
