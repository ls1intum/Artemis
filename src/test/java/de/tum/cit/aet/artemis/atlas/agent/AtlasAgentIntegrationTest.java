package de.tum.cit.aet.artemis.atlas.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepositoryDialect;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.AtlasAgentChatRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.CompetencyPreviewDTO;
import de.tum.cit.aet.artemis.atlas.service.AtlasAgentService;
import de.tum.cit.aet.artemis.atlas.service.AtlasAgentSessionCacheService;
import de.tum.cit.aet.artemis.course.domain.Course;

class AtlasAgentIntegrationTest extends AbstractAtlasIntegrationTest {

    private static final String TEST_PREFIX = "atlasagentintegration";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AtlasAgentService atlasAgentService;

    @Autowired
    private AtlasAgentSessionCacheService atlasAgentSessionCacheService;

    private Course course;

    private Map<String, List<Message>> chatMemoryStorage;

    @BeforeEach
    void setupTestScenario() {
        course = courseUtilService.createCourseWithUserPrefix(TEST_PREFIX);
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);

        // Set up chatClient.mutate() to return a real builder backed by the mock ChatModel
        when(chatClient.mutate()).thenAnswer(inv -> ChatClient.builder(chatModel));

        // Set up stateful mock for ChatMemory to actually store and retrieve messages
        chatMemoryStorage = new HashMap<>();

        doAnswer(invocation -> {
            String sessionId = invocation.getArgument(0);
            Message message = invocation.getArgument(1);
            chatMemoryStorage.computeIfAbsent(sessionId, key -> new ArrayList<>()).add(message);
            return null;
        }).when(chatMemory).add(anyString(), any(Message.class));

        when(chatMemory.get(anyString())).thenAnswer(invocation -> {
            String sessionId = invocation.getArgument(0);
            return chatMemoryStorage.getOrDefault(sessionId, new ArrayList<>());
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testServiceAvailability() {
        boolean available = atlasAgentService.isAvailable();
        assertThat(available).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testBasicEndToEndFlow() throws Exception {
        String competencyMessage = "Help me create competencies for a software engineering course covering OOP, design patterns, and testing";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(competencyMessage);

        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.timestamp").exists())
                // Assert the actual mocked content (not just existence) so a failure inside the real ChatClient pipeline
                // cannot be masked by the service's catch-all fallback message
                .andExpect(jsonPath("$.message").value("Mocked AI response for testing"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDifferentCourseContexts() throws Exception {
        Course secondCourse = courseUtilService.createCourse();
        String message = "Help with competencies for Computer Science basics";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message);

        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk());

        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", secondCourse.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDatabaseRelatedMessage() throws Exception {
        String message = "What competencies should I create for a database course?";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message);

        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExerciseMappingMessage() throws Exception {
        String message = "Help me map exercises to competencies";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message);

        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSpecificCompetencyListMessage() throws Exception {
        String message = "I want to create competencies for: SQL, NoSQL, Database Design, Query Optimization";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message);

        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testBloomsTaxonomyMessage() throws Exception {
        String message = "Generate competencies based on Bloom's taxonomy for my machine learning course";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message);

        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testPrerequisiteRelationshipsMessage() throws Exception {
        String message = "Can you suggest prerequisite relationships between competencies?";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message);

        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAssessmentFocusedMessage() throws Exception {
        String message = "Help me create assessment-focused competencies for programming exercises";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message);

        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testWebDevelopmentMessage() throws Exception {
        String message = "I'm designing a new course on web development. Can you help me create competencies?";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message);

        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCourseContentMessage() throws Exception {
        String message = "The course covers HTML, CSS, JavaScript, React, Node.js, and databases.";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message);

        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").exists());
    }

    @Nested
    class ToolIntegration {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldSetCompetenciesModifiedFlagWhenToolCalled() throws Exception {
            String competencyCreationMessage = "Create a competency called 'Object-Oriented Programming' with description 'Understanding OOP principles'";

            request.performMvcRequest(post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new AtlasAgentChatRequestDTO(competencyCreationMessage)))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.competenciesModified").exists());

        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldIndicateToolsAreAvailable() {
            boolean actualAvailability = atlasAgentService.isAvailable();

            assertThat(actualAvailability).as("Agent service should be available with tools").isTrue();
        }
    }

    @Nested
    class ConversationHistory {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReturnEmptyHistoryWhenNoMessages() throws Exception {
            request.performMvcRequest(get("/api/atlas/agent/courses/{courseId}/chat/history", course.getId())).andExpect(status().isOk()).andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReturnHistoryAfterAddingMessages() throws Exception {
            var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
            String sessionId = "course_%d_user_%d".formatted(course.getId(), instructor.getId());

            // Manually populate chat memory to test history retrieval
            chatMemory.add(sessionId, new UserMessage("First user message"));
            chatMemory.add(sessionId, new AssistantMessage("First assistant response"));

            request.performMvcRequest(get("/api/atlas/agent/courses/{courseId}/chat/history", course.getId())).andExpect(status().isOk()).andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].isUser").value(true)).andExpect(jsonPath("$[0].content").value("First user message"))
                    .andExpect(jsonPath("$[1].isUser").value(false)).andExpect(jsonPath("$[1].content").value("First assistant response"));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReturnMultipleMessagesInHistory() throws Exception {
            var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
            String sessionId = "course_%d_user_%d".formatted(course.getId(), instructor.getId());

            // Manually populate chat memory with multiple conversation turns
            chatMemory.add(sessionId, new UserMessage("Tell me about competencies"));
            chatMemory.add(sessionId, new AssistantMessage("Competencies are learning objectives"));
            chatMemory.add(sessionId, new UserMessage("How do I create them?"));
            chatMemory.add(sessionId, new AssistantMessage("You can create them via the UI"));

            request.performMvcRequest(get("/api/atlas/agent/courses/{courseId}/chat/history", course.getId())).andExpect(status().isOk()).andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(4)).andExpect(jsonPath("$[0].isUser").value(true)).andExpect(jsonPath("$[1].isUser").value(false))
                    .andExpect(jsonPath("$[2].isUser").value(true)).andExpect(jsonPath("$[3].isUser").value(false));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldExtractPreviewDataFromEmbeddedMessages() throws Exception {
            var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
            String sessionId = "course_%d_user_%d".formatted(course.getId(), instructor.getId());

            // Add clean messages to chat memory (no markers)
            chatMemory.add(sessionId, new UserMessage("Create a competency"));
            chatMemory.add(sessionId, new AssistantMessage("Here are the competencies"));

            // Store preview data in cache for the first assistant message (index 0)
            var competencyPreviews = List.of(new CompetencyPreviewDTO("Test Competency", "Test Description", "APPLY", null, false));
            atlasAgentSessionCacheService.storePreviewForMessage(sessionId, 0, new AtlasAgentSessionCacheService.MessagePreviewData(competencyPreviews, null, null, null));

            request.performMvcRequest(get("/api/atlas/agent/courses/{courseId}/chat/history", course.getId())).andExpect(status().isOk()).andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    // Verify the preview data was retrieved from cache and the message content is clean
                    .andExpect(jsonPath("$[1].content").value("Here are the competencies")).andExpect(jsonPath("$[1].competencyPreviews").isArray())
                    .andExpect(jsonPath("$[1].competencyPreviews[0].title").value("Test Competency")).andExpect(jsonPath("$[1].competencyPreviews[0].taxonomy").value("APPLY"));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldHandleMessagesWithoutPreviewData() throws Exception {
            var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
            String sessionId = "course_%d_user_%d".formatted(course.getId(), instructor.getId());

            // Test message without preview data (no cache entry)
            chatMemory.add(sessionId, new UserMessage("Simple question"));
            chatMemory.add(sessionId, new AssistantMessage("Simple response without any preview data"));

            request.performMvcRequest(get("/api/atlas/agent/courses/{courseId}/chat/history", course.getId())).andExpect(status().isOk()).andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[1].content").value("Simple response without any preview data"))
                    .andExpect(jsonPath("$[1].competencyPreviews").doesNotExist());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldReturnForbiddenForStudentAccessingHistory() throws Exception {
            request.performMvcRequest(get("/api/atlas/agent/courses/{courseId}/chat/history", course.getId())).andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void shouldReturnForbiddenForTutorAccessingHistory() throws Exception {
            request.performMvcRequest(get("/api/atlas/agent/courses/{courseId}/chat/history", course.getId())).andExpect(status().isForbidden());
        }
    }

    @Nested
    class StateManagement {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldTrackCompetencyModifiedState() {
            // Reset state before test
            AtlasAgentService.resetCompetencyModifiedFlag();
            assertThat(AtlasAgentService.wasCompetencyModified()).isFalse();

            // Mark as modified
            AtlasAgentService.markCompetencyModified();
            assertThat(AtlasAgentService.wasCompetencyModified()).isTrue();

            // Reset again
            AtlasAgentService.resetCompetencyModifiedFlag();
            assertThat(AtlasAgentService.wasCompetencyModified()).isFalse();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReturnTrueWhenCompetencyWasModified() {
            AtlasAgentService.resetCompetencyModifiedFlag();
            AtlasAgentService.markCompetencyModified();

            boolean wasModified = AtlasAgentService.wasCompetencyModified();

            assertThat(wasModified).isTrue();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReturnFalseWhenCompetencyWasNotModified() {
            AtlasAgentService.resetCompetencyModifiedFlag();

            boolean wasModified = AtlasAgentService.wasCompetencyModified();

            assertThat(wasModified).isFalse();
        }
    }

    @Nested
    class Authorization {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldReturnForbiddenForStudentAccessingChatEndpoint() throws Exception {
            AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO("Test message");

            request.performMvcRequest(
                    post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void shouldReturnForbiddenForTutorAccessingChatEndpoint() throws Exception {
            AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO("Test message");

            request.performMvcRequest(
                    post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldAllowInstructorAccessToChatEndpoint() throws Exception {
            AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO("Test message");

            request.performMvcRequest(
                    post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class JdbcChatMemorySchema {

        @Autowired
        private DataSource dataSource;

        @Autowired
        private PlatformTransactionManager transactionManager;

        /**
         * Round-trip through the real {@link JdbcChatMemoryRepository} (the bean is mocked in integration tests)
         * to verify that the Liquibase schema of {@code spring_ai_chat_memory} matches the SQL issued by the
         * Spring AI JDBC dialect — e.g. the {@code sequence_id} column introduced with Spring AI 2.0.0-RC1.
         * <p>
         * Note: the delete runs inside a managed transaction because the Hikari pool is configured with
         * {@code auto-commit: false} — saveAll commits via the repository's internal transaction template,
         * but deleteByConversationId issues a plain statement that would otherwise be rolled back on
         * connection return.
         */
        @Test
        void shouldSaveAndReadConversationWithRealJdbcRepository() {
            var repository = JdbcChatMemoryRepository.builder().jdbcTemplate(new JdbcTemplate(dataSource)).dialect(JdbcChatMemoryRepositoryDialect.from(dataSource)).build();
            var conversationId = "course_1_user_1_schema_test_" + java.util.UUID.randomUUID();

            repository.saveAll(conversationId, List.of(new UserMessage("What is a competency?"), new AssistantMessage("A competency is a learning objective.")));
            List<Message> messages = repository.findByConversationId(conversationId);

            assertThat(messages).hasSize(2);
            assertThat(messages.getFirst()).isInstanceOf(UserMessage.class);
            assertThat(messages.getFirst().getText()).isEqualTo("What is a competency?");
            assertThat(messages.getLast()).isInstanceOf(AssistantMessage.class);

            new TransactionTemplate(transactionManager).executeWithoutResult(status -> repository.deleteByConversationId(conversationId));
            assertThat(repository.findByConversationId(conversationId)).isEmpty();
        }
    }
}
