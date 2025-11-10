package de.tum.cit.aet.artemis.atlas.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.atlas.dto.AtlasAgentChatRequestDTO;
import de.tum.cit.aet.artemis.atlas.service.AtlasAgentService;
import de.tum.cit.aet.artemis.core.domain.Course;

class AtlasAgentIntegrationTest extends AbstractAtlasIntegrationTest {

    private static final String TEST_PREFIX = "atlasagentintegration";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AtlasAgentService atlasAgentService;

    private Course course;

    @BeforeEach
    void setupTestScenario() {
        course = courseUtilService.createCourseWithUserPrefix(TEST_PREFIX);
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
    }
    // if you are using another model other than gpt-4o change the default
    // value in the parameter of AtlasAgentService constructor accordingly in order for tests to pass

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
        String sessionId = "e2e-test-session";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(competencyMessage, sessionId);

        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value(sessionId)).andExpect(jsonPath("$.success").exists())
                .andExpect(jsonPath("$.timestamp").exists()).andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSessionIdConsistency() throws Exception {
        String sessionId = "session-consistency-test";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO("Test session consistency", sessionId);

        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value(sessionId));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDifferentCourseContexts() throws Exception {
        Course secondCourse = courseUtilService.createCourse();
        String message = "Help with competencies for Computer Science basics";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message, "course-context-session");

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
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message, "database-session");

        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value("database-session")).andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExerciseMappingMessage() throws Exception {
        String message = "Help me map exercises to competencies";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message, "exercise-mapping-session");

        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value("exercise-mapping-session")).andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSpecificCompetencyListMessage() throws Exception {
        String message = "I want to create competencies for: SQL, NoSQL, Database Design, Query Optimization";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message, "competency-list-session");

        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value("competency-list-session")).andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testBloomsTaxonomyMessage() throws Exception {
        String message = "Generate competencies based on Bloom's taxonomy for my machine learning course";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message, "blooms-taxonomy-session");

        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value("blooms-taxonomy-session")).andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testPrerequisiteRelationshipsMessage() throws Exception {
        String message = "Can you suggest prerequisite relationships between competencies?";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message, "prerequisites-session");

        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value("prerequisites-session")).andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAssessmentFocusedMessage() throws Exception {
        String message = "Help me create assessment-focused competencies for programming exercises";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message, "assessment-session");

        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value("assessment-session")).andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testWebDevelopmentMessage() throws Exception {
        String message = "I'm designing a new course on web development. Can you help me create competencies?";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message, "web-dev-session");

        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value("web-dev-session")).andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCourseContentMessage() throws Exception {
        String message = "The course covers HTML, CSS, JavaScript, React, Node.js, and databases.";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message, "course-content-session");

        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value("course-content-session")).andExpect(jsonPath("$.message").exists());
    }

    @Nested
    class ToolIntegration {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldSetCompetenciesModifiedFlagWhenToolCalled() throws Exception {
            String competencyCreationMessage = "Create a competency called 'Object-Oriented Programming' with description 'Understanding OOP principles'";
            String sessionId = "tool-test-session";

            request.performMvcRequest(post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new AtlasAgentChatRequestDTO(competencyCreationMessage, sessionId)))).andExpect(status().isOk())
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
    class Authorization {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldReturnForbiddenForStudentAccessingChatEndpoint() throws Exception {
            AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO("Test message", "test-session");

            request.performMvcRequest(
                    post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void shouldReturnForbiddenForTutorAccessingChatEndpoint() throws Exception {
            AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO("Test message", "test-session");

            request.performMvcRequest(
                    post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldAllowInstructorAccessToChatEndpoint() throws Exception {
            AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO("Test message", "test-session");

            request.performMvcRequest(
                    post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                    .andExpect(status().isOk());
        }
    }
}
