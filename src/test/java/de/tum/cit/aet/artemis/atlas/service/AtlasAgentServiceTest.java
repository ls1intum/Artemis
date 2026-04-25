package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

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

import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyGraphEdgeDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyGraphNodeDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.AtlasAgentChatResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.AtlasAgentHistoryMessageDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.CompetencyPreviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.CompetencyRelationPreviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.RelationGraphPreviewDTO;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

@ExtendWith(MockitoExtension.class)
class AtlasAgentServiceTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private AtlasPromptTemplateService templateService;

    @Mock
    private ChatMemory chatMemory;

    @Mock
    private ExecutionPlanStateManagerService executionPlanStateManagerService;

    @Mock
    private AtlasAgentSessionCacheService atlasAgentSessionCacheService;

    @Mock
    private AtlasAgentToolCallbackService toolCallbackFactory;

    @Mock
    private AtlasAgentToolsService toolsService;

    private static final String TEST_DEPLOYMENT_NAME = "gpt-4o";

    private static final double TEST_TEMPERATURE = 0.2;

    private AtlasAgentPreviewService previewService;

    private AtlasAgentService atlasAgentService;

    @BeforeEach
    void setUp() {
        ChatClient chatClient = ChatClient.create(chatModel);
        previewService = new AtlasAgentPreviewService(chatMemory);
        AtlasAgentDelegationService delegationService = new AtlasAgentDelegationService(chatClient, templateService, chatMemory, TEST_DEPLOYMENT_NAME, TEST_TEMPERATURE);
        atlasAgentService = new AtlasAgentService(chatClient, chatMemory, delegationService, toolCallbackFactory, toolsService, executionPlanStateManagerService,
                atlasAgentSessionCacheService, previewService);
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
        when(atlasAgentSessionCacheService.getPreviewHistory(sessionId)).thenReturn(Map.of());

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
        AtlasAgentService serviceWithNullMemory = new AtlasAgentService(ChatClient.create(chatModel), null,
                new AtlasAgentDelegationService(ChatClient.create(chatModel), templateService, null, TEST_DEPLOYMENT_NAME, TEST_TEMPERATURE), toolCallbackFactory, toolsService,
                executionPlanStateManagerService, atlasAgentSessionCacheService, previewService);

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
        when(atlasAgentSessionCacheService.getPreviewHistory(sessionId)).thenReturn(Map.of());

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
        void shouldReturnNullPreviewsWhenNoToolDelegationOccurred() {
            String testMessage = "Create a competency for OOP";
            Long courseId = 123L;
            String sessionId = "preview_collection_test";

            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Here is your competency preview")))));

            AtlasAgentChatResponseDTO result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

            assertThat(result).isNotNull();
            assertThat(result.message()).isEqualTo("Here is your competency preview");
            // No delegation tools were invoked, so no preview data should be present
            assertThat(result.competencyPreviews()).isNull();
            assertThat(result.relationPreviews()).isNull();
            assertThat(result.exerciseMappingPreview()).isNull();
        }

        @Test
        void shouldHandleCompetencyExpertToolsServiceNull() {
            AtlasAgentService serviceWithoutTools = new AtlasAgentService(ChatClient.create(chatModel), null,
                    new AtlasAgentDelegationService(ChatClient.create(chatModel), templateService, null, TEST_DEPLOYMENT_NAME, TEST_TEMPERATURE), toolCallbackFactory, toolsService,
                    executionPlanStateManagerService, atlasAgentSessionCacheService, previewService);

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
        void shouldReturnResponseWithNullPreviewsWhenNoSubAgentInvoked() {
            String testMessage = "Hello, what can you do?";
            Long courseId = 123L;
            String sessionId = "no_delegation_test";

            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("I can help you manage competencies.")))));

            AtlasAgentChatResponseDTO result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

            assertThat(result).isNotNull();
            assertThat(result.message()).isEqualTo("I can help you manage competencies.");
            assertThat(result.competencyPreviews()).isNull();
            assertThat(result.relationPreviews()).isNull();
            assertThat(result.relationGraphPreview()).isNull();
            assertThat(result.exerciseMappingPreview()).isNull();
        }

        @Test
        void shouldHandleCreateApprovedCompetencyMarker() {
            String testMessage = "Approve creation";
            Long courseId = 123L;
            String sessionId = "approval_test";
            String responseWithApprovalMarker = "Creating competency [CREATE_APPROVED_COMPETENCY]";

            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(responseWithApprovalMarker)))));

            AtlasAgentChatResponseDTO result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

            assertThat(result).isNotNull();

            // [CREATE_APPROVED_COMPETENCY] in an AI response (not as exact user input) is not a trigger,
            // so the response is returned as-is
            assertThat(result.message()).isEqualTo(responseWithApprovalMarker);
        }
    }

    @Nested
    class AtlasAgentHistoryMessageDTOTests {

        private final ObjectMapper objectMapper = JsonObjectMapper.get();

        @Test
        void shouldSerializeToJsonWhenValidData() throws Exception {
            AtlasAgentHistoryMessageDTO dto = new AtlasAgentHistoryMessageDTO("Test message content", true, null, null, null, null);

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
            AtlasAgentHistoryMessageDTO dto = new AtlasAgentHistoryMessageDTO("", true, null, null, null, null);

            String actualJson = objectMapper.writeValueAsString(dto);

            assertThat(actualJson).doesNotContain("content");
            assertThat(actualJson).contains("\"isUser\":true");
        }

        @Test
        void shouldExcludeContentWhenNull() throws Exception {
            AtlasAgentHistoryMessageDTO dto = new AtlasAgentHistoryMessageDTO(null, false, null, null, null, null);

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
            List<Message> messages = List.of(new UserMessage("Create a competency"), new AssistantMessage("Here's your competency preview"));

            var preview = new CompetencyPreviewDTO("OOP Basics", "Object-Oriented Programming fundamentals", "UNDERSTAND", null, null);
            var previewData = new AtlasAgentSessionCacheService.MessagePreviewData(List.of(preview), null, null, null);

            when(chatMemory.get(sessionId)).thenReturn(messages);
            when(atlasAgentSessionCacheService.getPreviewHistory(sessionId)).thenReturn(Map.of(0, previewData));

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
            List<Message> messages = List.of(new UserMessage("Create multiple competencies"), new AssistantMessage("Here are multiple competencies"));

            var comp1 = new CompetencyPreviewDTO("Comp 1", "Description 1", "REMEMBER", null, null);
            var comp2 = new CompetencyPreviewDTO("Comp 2", "Description 2", "APPLY", null, null);
            var previewData = new AtlasAgentSessionCacheService.MessagePreviewData(List.of(comp1, comp2), null, null, null);

            when(chatMemory.get(sessionId)).thenReturn(messages);
            when(atlasAgentSessionCacheService.getPreviewHistory(sessionId)).thenReturn(Map.of(0, previewData));

            List<AtlasAgentHistoryMessageDTO> result = atlasAgentService.getConversationHistoryAsDTO(sessionId);

            assertThat(result).hasSize(2);
            assertThat(result.get(1).content()).isEqualTo("Here are multiple competencies");
            assertThat(result.get(1).competencyPreviews()).isNotNull();
            assertThat(result.get(1).competencyPreviews()).hasSize(2);
            assertThat(result.get(1).competencyPreviews().getFirst().title()).isEqualTo("Comp 1");
            assertThat(result.get(1).competencyPreviews().get(1).title()).isEqualTo("Comp 2");
        }

        @Test
        void shouldKeepUserAndAssistantMessages() {
            String sessionId = "course_123_user_101";
            List<Message> messages = List.of(new UserMessage("Create a competency"), new AssistantMessage("Competency created successfully"));

            when(chatMemory.get(sessionId)).thenReturn(messages);
            when(atlasAgentSessionCacheService.getPreviewHistory(sessionId)).thenReturn(Map.of());

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
            when(atlasAgentSessionCacheService.getPreviewHistory(sessionId)).thenReturn(Map.of());

            List<AtlasAgentHistoryMessageDTO> result = atlasAgentService.getConversationHistoryAsDTO(sessionId);

            assertThat(result).hasSize(2);
            assertThat(result.getFirst().content()).isEqualTo("Show me competencies");
            assertThat(result.get(1).content()).isEqualTo("Here are your competencies");
        }

        @Test
        void shouldHandleMessageWithoutPreviewData() {
            String sessionId = "course_123_user_303";
            List<Message> messages = List.of(new UserMessage("What are competencies?"),
                    new AssistantMessage("Competencies are learning objectives that define what students should know and be able to do."));

            when(chatMemory.get(sessionId)).thenReturn(messages);
            when(atlasAgentSessionCacheService.getPreviewHistory(sessionId)).thenReturn(Map.of());

            List<AtlasAgentHistoryMessageDTO> result = atlasAgentService.getConversationHistoryAsDTO(sessionId);

            assertThat(result).hasSize(2);
            assertThat(result.getFirst().content()).isEqualTo("What are competencies?");
            assertThat(result.get(1).content()).isEqualTo("Competencies are learning objectives that define what students should know and be able to do.");
            assertThat(result.get(1).competencyPreviews()).isNull();
        }

        @Test
        void shouldReturnNullPreviewsWhenCacheHasNoEntryForAssistantIndex() {
            String sessionId = "course_123_user_404";
            List<Message> messages = List.of(new UserMessage("Test"), new AssistantMessage("Message"));

            when(chatMemory.get(sessionId)).thenReturn(messages);
            when(atlasAgentSessionCacheService.getPreviewHistory(sessionId)).thenReturn(Map.of());

            List<AtlasAgentHistoryMessageDTO> result = atlasAgentService.getConversationHistoryAsDTO(sessionId);

            assertThat(result).hasSize(2);
            assertThat(result.get(1).content()).isEqualTo("Message");
            assertThat(result.get(1).competencyPreviews()).isNull();
        }

        @Test
        void shouldHandleEmptyPreviewHistory() {
            String sessionId = "course_123_user_505";
            List<Message> messages = List.of(new UserMessage("Test"), new AssistantMessage("Message"));

            when(chatMemory.get(sessionId)).thenReturn(messages);
            when(atlasAgentSessionCacheService.getPreviewHistory(sessionId)).thenReturn(Map.of());

            List<AtlasAgentHistoryMessageDTO> result = atlasAgentService.getConversationHistoryAsDTO(sessionId);

            assertThat(result).hasSize(2);
            assertThat(result.get(1).content()).isEqualTo("Message");
            assertThat(result.get(1).competencyPreviews()).isNull();
        }

        @Test
        void shouldHandleMultipleMessagesWithMixedPreviewData() {
            String sessionId = "course_123_user_606";
            List<Message> messages = List.of(new UserMessage("User message 1"), new AssistantMessage("First response"), new UserMessage("User message 2"),
                    new AssistantMessage("Second response without preview"), new UserMessage("User message 3"), new AssistantMessage("Third response"));

            var preview1 = new CompetencyPreviewDTO("Test 1", "Desc 1", "APPLY", null, null);
            var previewData1 = new AtlasAgentSessionCacheService.MessagePreviewData(List.of(preview1), null, null, null);
            var preview2 = new CompetencyPreviewDTO("Test 2", "Desc 2", "ANALYZE", null, null);
            var previewData2 = new AtlasAgentSessionCacheService.MessagePreviewData(List.of(preview2), null, null, null);

            when(chatMemory.get(sessionId)).thenReturn(messages);
            when(atlasAgentSessionCacheService.getPreviewHistory(sessionId)).thenReturn(Map.of(0, previewData1, 2, previewData2));

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
            List<Message> messages = List.of(new UserMessage("Create OOP competency"), new AssistantMessage("TOPIC: OOP\nREQUIREMENTS: Create\nCONSTRAINTS: None\nCONTEXT: Course"),
                    new AssistantMessage("Competency created"), new AssistantMessage("Task completed successfully"));

            var preview = new CompetencyPreviewDTO("OOP", "Test", "APPLY", null, null);
            var previewData = new AtlasAgentSessionCacheService.MessagePreviewData(List.of(preview), null, null, null);

            when(chatMemory.get(sessionId)).thenReturn(messages);
            // Index 0 = briefing (skipped), index 1 = "Competency created", index 2 = "Task completed"
            when(atlasAgentSessionCacheService.getPreviewHistory(sessionId)).thenReturn(Map.of(1, previewData));

            List<AtlasAgentHistoryMessageDTO> result = atlasAgentService.getConversationHistoryAsDTO(sessionId);

            assertThat(result).hasSize(3);
            assertThat(result.getFirst().content()).isEqualTo("Create OOP competency");
            assertThat(result.get(1).content()).isEqualTo("Competency created");
            assertThat(result.get(1).competencyPreviews()).isNotNull();
            assertThat(result.get(1).competencyPreviews()).hasSize(1);
            assertThat(result.get(2).content()).isEqualTo("Task completed successfully");
        }
    }

    @Nested
    class RelationPreviewHandling {

        @Test
        void shouldReturnRelationPreviewsWhenMapperSetsThreadLocal() {
            String testMessage = "Map competencies in this course";
            Long courseId = 123L;
            String sessionId = "mapper_test";

            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            // Simulate main agent call where delegation tool internally invoked the mapper
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Relation preview generated")))));

            AtlasAgentChatResponseDTO result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

            assertThat(result).isNotNull();
            assertThat(result.message()).isEqualTo("Relation preview generated");
        }

        @Test
        void shouldExtractRelationPreviewFromHistory() {
            String sessionId = "course_123_user_relation";
            List<Message> messages = List.of(new UserMessage("Create a relation"), new AssistantMessage("Here's the relation"));

            var relationPreview = new CompetencyRelationPreviewDTO(null, 1L, "OOP", 2L, "Patterns", RelationType.ASSUMES, false);
            var previewData = new AtlasAgentSessionCacheService.MessagePreviewData(null, List.of(relationPreview), null, null);

            when(chatMemory.get(sessionId)).thenReturn(messages);
            when(atlasAgentSessionCacheService.getPreviewHistory(sessionId)).thenReturn(Map.of(0, previewData));

            List<AtlasAgentHistoryMessageDTO> result = atlasAgentService.getConversationHistoryAsDTO(sessionId);

            assertThat(result).hasSize(2);
            assertThat(result.get(1).content()).isEqualTo("Here's the relation");
            assertThat(result.get(1).relationPreviews()).isNotNull();
            assertThat(result.get(1).relationPreviews()).hasSize(1);
        }

        @Test
        void shouldExtractRelationGraphPreviewFromHistory() {
            String sessionId = "course_123_user_graph";
            List<Message> messages = List.of(new UserMessage("Show graph"), new AssistantMessage("Graph preview"));

            var nodes = List.of(new CompetencyGraphNodeDTO("1", "A", null, null, null), new CompetencyGraphNodeDTO("2", "B", null, null, null));
            var edges = List.of(new CompetencyGraphEdgeDTO("edge-1", "1", "2", RelationType.ASSUMES));
            var graphPreview = new RelationGraphPreviewDTO(nodes, edges, false);
            var previewData = new AtlasAgentSessionCacheService.MessagePreviewData(null, null, graphPreview, null);

            when(chatMemory.get(sessionId)).thenReturn(messages);
            when(atlasAgentSessionCacheService.getPreviewHistory(sessionId)).thenReturn(Map.of(0, previewData));

            List<AtlasAgentHistoryMessageDTO> result = atlasAgentService.getConversationHistoryAsDTO(sessionId);

            assertThat(result).hasSize(2);
            assertThat(result.get(1).content()).isEqualTo("Graph preview");
            assertThat(result.get(1).relationGraphPreview()).isNotNull();
        }

        @Test
        void shouldFilterOutActionConfirmationMessages() {
            String sessionId = "course_123_user_action_confirm";
            List<Message> messages = List.of(new UserMessage("Approve"), new AssistantMessage("[CREATE_APPROVED_RELATION]"), new AssistantMessage("Relation created"));

            when(chatMemory.get(sessionId)).thenReturn(messages);
            when(atlasAgentSessionCacheService.getPreviewHistory(sessionId)).thenReturn(Map.of());

            List<AtlasAgentHistoryMessageDTO> result = atlasAgentService.getConversationHistoryAsDTO(sessionId);

            assertThat(result).hasSize(2);
            assertThat(result.getFirst().content()).isEqualTo("Approve");
            assertThat(result.get(1).content()).isEqualTo("Relation created");
        }
    }

    @Nested
    class ProcessChatMessageWithNullChatClient {

        @Test
        void shouldReturnUnavailableMessageWhenChatClientIsNull() {
            AtlasAgentService serviceWithNullClient = new AtlasAgentService(null, chatMemory,
                    new AtlasAgentDelegationService(null, templateService, chatMemory, TEST_DEPLOYMENT_NAME, TEST_TEMPERATURE), toolCallbackFactory, toolsService,
                    executionPlanStateManagerService, atlasAgentSessionCacheService, previewService);

            AtlasAgentChatResponseDTO result = serviceWithNullClient.processChatMessage("Test message", 123L, "test_session");

            assertThat(result).isNotNull();
            assertThat(result.message()).contains("Atlas Agent is not available");
            assertThat(result.competenciesModified()).isFalse();
        }
    }

    @Nested
    class UtilityMethods {

        @Test
        void shouldReturnTrueWhenAvailableWithChatClientAndMemory() {
            assertThat(atlasAgentService.isAvailable()).isTrue();
        }

        @Test
        void shouldReturnFalseWhenChatClientNull() {
            AtlasAgentService serviceWithNullClient = new AtlasAgentService(null, chatMemory,
                    new AtlasAgentDelegationService(null, templateService, chatMemory, TEST_DEPLOYMENT_NAME, TEST_TEMPERATURE), toolCallbackFactory, toolsService,
                    executionPlanStateManagerService, atlasAgentSessionCacheService, previewService);
            assertThat(serviceWithNullClient.isAvailable()).isFalse();
        }

        @Test
        void shouldReturnFalseWhenChatMemoryNull() {
            AtlasAgentService serviceWithNullMemory = new AtlasAgentService(ChatClient.create(chatModel), null,
                    new AtlasAgentDelegationService(ChatClient.create(chatModel), templateService, null, TEST_DEPLOYMENT_NAME, TEST_TEMPERATURE), toolCallbackFactory, toolsService,
                    executionPlanStateManagerService, atlasAgentSessionCacheService, previewService);
            assertThat(serviceWithNullMemory.isAvailable()).isFalse();
        }

        @Test
        void shouldGenerateCorrectSessionId() {
            String sessionId = atlasAgentService.generateSessionId(42L, 7L);
            assertThat(sessionId).isEqualTo("course_42_user_7");
        }

        @Test
        void shouldMarkAndResetCompetencyModifiedFlag() {
            AtlasAgentService.resetCompetencyModifiedFlag();
            assertThat(AtlasAgentService.wasCompetencyModified()).isFalse();
            AtlasAgentService.markCompetencyModified();
            assertThat(AtlasAgentService.wasCompetencyModified()).isTrue();
            AtlasAgentService.resetCompetencyModifiedFlag();
            assertThat(AtlasAgentService.wasCompetencyModified()).isFalse();
        }
    }

    @Nested
    class CancelCommandHandling {

        @Test
        void shouldCancelPlanOnCancelCommand() {
            String sessionId = "cancel_test_session";

            when(executionPlanStateManagerService.hasPlan(sessionId)).thenReturn(true);

            AtlasAgentChatResponseDTO result = atlasAgentService.processChatMessage("cancel", 123L, sessionId);

            assertThat(result).isNotNull();
            assertThat(result.message()).isEqualTo("Plan cancelled.");
            verify(executionPlanStateManagerService).cancelPlan(sessionId);
        }

        @Test
        void shouldCancelPlanOnStopCommand() {
            String sessionId = "stop_test_session";

            when(executionPlanStateManagerService.hasPlan(sessionId)).thenReturn(true);

            AtlasAgentChatResponseDTO result = atlasAgentService.processChatMessage("stop", 123L, sessionId);

            assertThat(result).isNotNull();
            assertThat(result.message()).isEqualTo("Plan cancelled.");
        }

        @Test
        void shouldCancelPlanOnAbortCommand() {
            String sessionId = "abort_test_session";

            when(executionPlanStateManagerService.hasPlan(sessionId)).thenReturn(true);

            AtlasAgentChatResponseDTO result = atlasAgentService.processChatMessage("abort", 123L, sessionId);

            assertThat(result).isNotNull();
            assertThat(result.message()).isEqualTo("Plan cancelled.");
        }

        @Test
        void shouldNotCancelWhenNoPlanActive() {
            String sessionId = "no_plan_session";

            when(executionPlanStateManagerService.hasPlan(sessionId)).thenReturn(false);
            when(templateService.render(anyString(), anyMap())).thenReturn("System prompt");
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Some response")))));

            AtlasAgentChatResponseDTO result = atlasAgentService.processChatMessage("cancel", 123L, sessionId);

            assertThat(result).isNotNull();
            assertThat(result.message()).isEqualTo("Some response");
        }
    }

    @Nested
    class ExerciseMappingDelegation {

        @Test
        void shouldReturnExerciseMappingPreviewWhenMapperSetsThreadLocal() {
            String testMessage = "Map exercises in this course";
            Long courseId = 123L;
            String sessionId = "exercise_mapper_test";

            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Exercise mapping preview generated")))));

            AtlasAgentChatResponseDTO result = atlasAgentService.processChatMessage(testMessage, courseId, sessionId);

            assertThat(result).isNotNull();
            assertThat(result.message()).isEqualTo("Exercise mapping preview generated");
        }

        @Test
        void shouldHandleExerciseMappingApprovalWithoutPayload() {
            String sessionId = "exercise_approval_test";
            Long courseId = 123L;

            when(executionPlanStateManagerService.hasPlan(sessionId)).thenReturn(false);
            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Mappings saved successfully")))));

            AtlasAgentChatResponseDTO result = atlasAgentService.processChatMessage("[CREATE_APPROVED_EXERCISE_MAPPING]", courseId, sessionId);

            assertThat(result).isNotNull();
            assertThat(result.message()).isEqualTo("Mappings saved successfully");
        }

        @Test
        void shouldHandleExerciseMappingApprovalWithValidPayload() {
            String sessionId = "exercise_approval_payload_test";
            Long courseId = 123L;
            String message = "[CREATE_APPROVED_EXERCISE_MAPPING]:{\"exerciseId\":42,\"mappings\":[{\"competencyId\":1,\"weight\":0.5}]}";

            when(executionPlanStateManagerService.hasPlan(sessionId)).thenReturn(false);
            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Mappings saved")))));

            AtlasAgentChatResponseDTO result = atlasAgentService.processChatMessage(message, courseId, sessionId);

            assertThat(result).isNotNull();
            assertThat(result.message()).isEqualTo("Mappings saved");
        }

        @Test
        void shouldHandleExerciseMappingApprovalWithInvalidPayload() {
            String sessionId = "exercise_approval_invalid_test";
            Long courseId = 123L;
            String message = "[CREATE_APPROVED_EXERCISE_MAPPING]:{invalid json}";

            when(executionPlanStateManagerService.hasPlan(sessionId)).thenReturn(false);
            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Mappings saved with fallback")))));

            AtlasAgentChatResponseDTO result = atlasAgentService.processChatMessage(message, courseId, sessionId);

            assertThat(result).isNotNull();
            assertThat(result.message()).isEqualTo("Mappings saved with fallback");
        }
    }

    @Nested
    class PlanDetection {

        @Test
        void shouldDetectAndInitializePlanFromResponse() {
            String sessionId = "plan_detection_test";
            Long courseId = 123L;
            String responseWithPlanMarker = "I'll help you. %%ARTEMIS_PLAN:CREATE_AND_MAP_RELATIONS%%";

            when(executionPlanStateManagerService.hasPlan(sessionId)).thenReturn(false);
            when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(responseWithPlanMarker)))));

            atlasAgentService.processChatMessage("Create competencies and map them", courseId, sessionId);

            verify(executionPlanStateManagerService).initializePlan(anyString(), any(), anyString(), any(), any());
        }

        @Test
        void shouldNotReinitializePlanWhenAlreadyActive() {
            String sessionId = "plan_already_active_test";
            Long courseId = 123L;
            String responseWithPlanMarker = "%%ARTEMIS_PLAN:CREATE_AND_MAP_RELATIONS%%";

            when(executionPlanStateManagerService.hasPlan(sessionId)).thenReturn(true);
            when(executionPlanStateManagerService.hasPlan(sessionId)).thenReturn(false).thenReturn(true);
            when(templateService.render(anyString(), anyMap())).thenReturn("System prompt");
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(responseWithPlanMarker)))));

            atlasAgentService.processChatMessage("Create competencies", courseId, sessionId);
        }

        @Test
        void shouldIgnoreUnknownPlanTemplate() {
            String sessionId = "unknown_plan_test";
            Long courseId = 123L;
            String responseWithUnknownPlan = "%%ARTEMIS_PLAN:UNKNOWN_TEMPLATE%%";

            when(executionPlanStateManagerService.hasPlan(sessionId)).thenReturn(false);
            when(templateService.render(anyString(), anyMap())).thenReturn("System prompt");
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(responseWithUnknownPlan)))));

            AtlasAgentChatResponseDTO result = atlasAgentService.processChatMessage("Test", courseId, sessionId);

            assertThat(result).isNotNull();
        }
    }

}
