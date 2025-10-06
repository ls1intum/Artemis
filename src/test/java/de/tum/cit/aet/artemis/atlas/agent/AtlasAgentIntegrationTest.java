package de.tum.cit.aet.artemis.atlas.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
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

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testServiceAvailability() {
        boolean available = atlasAgentService.isAvailable();
        assertThat(available).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testBasicEndToEndFlow() throws Exception {
        // Given
        String competencyMessage = "Help me create competencies for a software engineering course covering OOP, design patterns, and testing";
        String sessionId = "e2e-test-session";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(competencyMessage, sessionId);

        // When & Then - Test the HTTP endpoint with a realistic request
        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value(sessionId)).andExpect(jsonPath("$.success").exists())
                .andExpect(jsonPath("$.timestamp").exists()).andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSessionIdConsistency() throws Exception {
        // Given
        String sessionId = "session-consistency-test";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO("Test session consistency", sessionId);

        // When & Then - Verify session ID is returned correctly
        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value(sessionId));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDifferentCourseContexts() throws Exception {
        // Given
        Course secondCourse = courseUtilService.createCourse();
        String message = "Help with competencies for Computer Science basics";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message, "course-context-session");

        // When & Then - Test with first course
        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk());

        // When & Then - Test with second course
        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", secondCourse.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDatabaseRelatedMessage() throws Exception {
        // Given
        String message = "What competencies should I create for a database course?";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message, "database-session");

        // When & Then
        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value("database-session")).andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExerciseMappingMessage() throws Exception {
        // Given
        String message = "Help me map exercises to competencies";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message, "exercise-mapping-session");

        // When & Then
        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value("exercise-mapping-session")).andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSpecificCompetencyListMessage() throws Exception {
        // Given
        String message = "I want to create competencies for: SQL, NoSQL, Database Design, Query Optimization";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message, "competency-list-session");

        // When & Then
        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value("competency-list-session")).andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testBloomsTaxonomyMessage() throws Exception {
        // Given
        String message = "Generate competencies based on Bloom's taxonomy for my machine learning course";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message, "blooms-taxonomy-session");

        // When & Then
        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value("blooms-taxonomy-session")).andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testPrerequisiteRelationshipsMessage() throws Exception {
        // Given
        String message = "Can you suggest prerequisite relationships between competencies?";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message, "prerequisites-session");

        // When & Then
        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value("prerequisites-session")).andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAssessmentFocusedMessage() throws Exception {
        // Given
        String message = "Help me create assessment-focused competencies for programming exercises";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message, "assessment-session");

        // When & Then
        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value("assessment-session")).andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testWebDevelopmentMessage() throws Exception {
        // Given
        String message = "I'm designing a new course on web development. Can you help me create competencies?";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message, "web-dev-session");

        // When & Then
        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value("web-dev-session")).andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCourseContentMessage() throws Exception {
        // Given
        String message = "The course covers HTML, CSS, JavaScript, React, Node.js, and databases.";
        AtlasAgentChatRequestDTO requestDTO = new AtlasAgentChatRequestDTO(message, "course-content-session");

        // When & Then
        request.performMvcRequest(
                post("/api/atlas/agent/courses/{courseId}/chat", course.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value("course-content-session")).andExpect(jsonPath("$.message").exists());
    }
}
