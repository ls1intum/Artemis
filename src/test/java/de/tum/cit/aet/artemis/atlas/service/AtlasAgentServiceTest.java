package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;

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

    @Test
    void testProcessChatMessage_WithCompetencyCreated() throws ExecutionException, InterruptedException {
        AtlasAgentToolsService mockToolsService = org.mockito.Mockito.mock(AtlasAgentToolsService.class);
        ChatClient chatClient = ChatClient.create(chatModel);
        AtlasAgentService serviceWithToolsService = new AtlasAgentService(chatClient, templateService, null, null, mockToolsService);

        String testMessage = "Create a competency";
        Long courseId = 123L;
        String sessionId = "session_create_comp";
        String expectedResponse = "Competency created";

        when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(expectedResponse)))));
        when(mockToolsService.wasCompetencyCreated()).thenReturn(true);

        CompletableFuture<AgentChatResult> result = serviceWithToolsService.processChatMessage(testMessage, courseId, sessionId);

        assertThat(result).isNotNull();
        AgentChatResult chatResult = result.get();
        assertThat(chatResult.message()).isEqualTo(expectedResponse);
        assertThat(chatResult.competenciesModified()).isTrue();
    }

    @Test
    void testProcessChatMessage_WithCompetencyNotCreated() throws ExecutionException, InterruptedException {
        AtlasAgentToolsService mockToolsService = org.mockito.Mockito.mock(AtlasAgentToolsService.class);
        ChatClient chatClient = ChatClient.create(chatModel);
        AtlasAgentService serviceWithToolsService = new AtlasAgentService(chatClient, templateService, null, null, mockToolsService);

        String testMessage = "Show me competencies";
        Long courseId = 123L;
        String sessionId = "session_no_comp_created";
        String expectedResponse = "Here are the competencies";

        when(templateService.render(anyString(), anyMap())).thenReturn("Test system prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(expectedResponse)))));
        when(mockToolsService.wasCompetencyCreated()).thenReturn(false);

        CompletableFuture<AgentChatResult> result = serviceWithToolsService.processChatMessage(testMessage, courseId, sessionId);

        assertThat(result).isNotNull();
        AgentChatResult chatResult = result.get();
        assertThat(chatResult.message()).isEqualTo(expectedResponse);
        assertThat(chatResult.competenciesModified()).isFalse();
    }

    @Nested
    class AtlasAgentToolsServiceTests {

        @Mock
        private CompetencyRepository competencyRepository;

        @Mock
        private CourseTestRepository courseRepository;

        @Mock
        private ExerciseTestRepository exerciseRepository;

        private AtlasAgentToolsService toolsService;

        @BeforeEach
        void setUp() {
            ObjectMapper objectMapper = new ObjectMapper();
            toolsService = new AtlasAgentToolsService(objectMapper, competencyRepository, courseRepository, exerciseRepository);
        }

        @Test
        void testGetCourseDescription_Success() {
            Long courseId = 123L;
            Course course = new Course();
            course.setDescription("Software Engineering Course");

            when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

            String result = toolsService.getCourseDescription(courseId);

            assertThat(result).isEqualTo("Software Engineering Course");
        }

        @Test
        void testGetCourseDescription_NotFound() {
            Long courseId = 999L;

            when(courseRepository.findById(courseId)).thenReturn(Optional.empty());

            String result = toolsService.getCourseDescription(courseId);

            assertThat(result).isEmpty();
        }

        @Test
        void testGetCourseCompetencies_Success() {
            Long courseId = 123L;
            Course course = new Course();
            course.setId(courseId);

            Competency competency = new Competency();
            competency.setId(1L);
            competency.setTitle("Java Programming");
            competency.setDescription("Learn Java basics");
            competency.setTaxonomy(CompetencyTaxonomy.APPLY);

            when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
            when(competencyRepository.findAllByCourseId(courseId)).thenReturn(Set.of(competency));

            String result = toolsService.getCourseCompetencies(courseId);

            assertThat(result).contains("Java Programming");
            assertThat(result).contains("Learn Java basics");
            assertThat(result).contains("APPLY");
        }

        @Test
        void testGetCourseCompetencies_CourseNotFound() {
            Long courseId = 999L;

            when(courseRepository.findById(courseId)).thenReturn(Optional.empty());

            String result = toolsService.getCourseCompetencies(courseId);

            assertThat(result).contains("error");
            assertThat(result).contains("Course not found");
        }

        @Test
        void testCreateCompetency_Success() {
            Long courseId = 123L;
            Course course = new Course();
            course.setId(courseId);

            Competency savedCompetency = new Competency();
            savedCompetency.setId(1L);
            savedCompetency.setTitle("Database Design");
            savedCompetency.setDescription("Design relational databases");
            savedCompetency.setTaxonomy(CompetencyTaxonomy.CREATE);

            when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
            when(competencyRepository.save(any(Competency.class))).thenReturn(savedCompetency);

            String result = toolsService.createCompetency(courseId, "Database Design", "Design relational databases", CompetencyTaxonomy.CREATE);

            assertThat(result).contains("success");
            assertThat(result).contains("Database Design");
            assertThat(result).contains("CREATE");
            assertThat(toolsService.wasCompetencyCreated()).isTrue();
        }

        @Test
        void testCreateCompetency_CourseNotFound() {
            Long courseId = 999L;

            when(courseRepository.findById(courseId)).thenReturn(Optional.empty());

            String result = toolsService.createCompetency(courseId, "Test", "Test desc", CompetencyTaxonomy.REMEMBER);

            assertThat(result).contains("error");
            assertThat(result).contains("Course not found");
            assertThat(toolsService.wasCompetencyCreated()).isFalse();
        }

        @Test
        void testGetExercisesListed_Success() {
            Long courseId = 123L;
            Course course = new Course();
            course.setId(courseId);

            when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
            when(exerciseRepository.findByCourseIds(Set.of(courseId))).thenReturn(Set.of());

            String result = toolsService.getExercisesListed(courseId);

            assertThat(result).contains("courseId");
            assertThat(result).contains("exercises");
        }

        @Test
        void testGetExercisesListed_CourseNotFound() {
            Long courseId = 999L;

            when(courseRepository.findById(courseId)).thenReturn(Optional.empty());

            String result = toolsService.getExercisesListed(courseId);

            assertThat(result).contains("error");
            assertThat(result).contains("Course not found");
        }

        @Test
        void testWasCompetencyCreated_InitiallyFalse() {
            assertThat(toolsService.wasCompetencyCreated()).isFalse();
        }

        @Test
        void testGetCourseCompetencies_WithNullTaxonomy() {
            Long courseId = 123L;
            Course course = new Course();
            course.setId(courseId);

            Competency competency = new Competency();
            competency.setId(1L);
            competency.setTitle("No Taxonomy");
            competency.setDescription("Competency without taxonomy");
            competency.setTaxonomy(null);

            when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
            when(competencyRepository.findAllByCourseId(courseId)).thenReturn(Set.of(competency));

            String result = toolsService.getCourseCompetencies(courseId);

            assertThat(result).contains("No Taxonomy");
            assertThat(result).contains("Competency without taxonomy");
            assertThat(result).contains("\"taxonomy\":\"\"");
        }

        @Test
        void testCreateCompetency_WithNullTaxonomy() {
            Long courseId = 123L;
            Course course = new Course();
            course.setId(courseId);

            Competency savedCompetency = new Competency();
            savedCompetency.setId(1L);
            savedCompetency.setTitle("No Taxonomy");
            savedCompetency.setDescription("Test");
            savedCompetency.setTaxonomy(null);

            when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
            when(competencyRepository.save(any(Competency.class))).thenReturn(savedCompetency);

            String result = toolsService.createCompetency(courseId, "No Taxonomy", "Test", CompetencyTaxonomy.APPLY);

            assertThat(result).contains("success");
            assertThat(result).contains("\"taxonomy\":\"\"");
        }

        @Test
        void testCreateCompetency_WithException() {
            Long courseId = 123L;
            Course course = new Course();
            course.setId(courseId);

            when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
            when(competencyRepository.save(any(Competency.class))).thenThrow(new RuntimeException("Database error"));

            String result = toolsService.createCompetency(courseId, "Test", "Test desc", CompetencyTaxonomy.APPLY);

            assertThat(result).contains("error");
            assertThat(result).contains("Failed to create competency");
            assertThat(toolsService.wasCompetencyCreated()).isFalse();
        }
    }

}
