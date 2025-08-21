package de.tum.cit.aet.artemis.jenkins.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.jenkins.connector.dto.BuildTriggerRequestDTO;
import de.tum.cit.aet.artemis.jenkins.connector.dto.RepositoryInfoDTO;

/**
 * Integration tests for the Jenkins connector REST API.
 */
@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
class BuildControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testTriggerBuild() throws Exception {
        // Arrange
        var request = createTestBuildTriggerRequest();
        String requestJson = objectMapper.writeValueAsString(request);

        // Act
        MvcResult result = mockMvc.perform(post("/api/builds/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        String responseJson = result.getResponse().getContentAsString();
        UUID buildId = objectMapper.readValue(responseJson, UUID.class);
        assertThat(buildId).isNotNull();
    }

    @Test
    void testGetBuildStatus() throws Exception {
        // Arrange - First trigger a build
        var request = createTestBuildTriggerRequest();
        String requestJson = objectMapper.writeValueAsString(request);

        MvcResult triggerResult = mockMvc.perform(post("/api/builds/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andReturn();

        UUID buildId = objectMapper.readValue(triggerResult.getResponse().getContentAsString(), UUID.class);

        // Act - Get status
        mockMvc.perform(get("/api/builds/status/" + buildId))
                .andExpect(status().isOk());
    }

    @Test
    void testGetBuildStatusNotFound() throws Exception {
        // Act
        UUID randomId = UUID.randomUUID();
        mockMvc.perform(get("/api/builds/status/" + randomId))
                .andExpect(status().isNotFound());
    }

    @Test
    void testHealthEndpoint() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetBuildScriptTemplate() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/builds/template/java"))
                .andExpect(status().isNotFound()); // Expected as templates are not implemented yet
    }

    private BuildTriggerRequestDTO createTestBuildTriggerRequest() {
        var exerciseRepo = new RepositoryInfoDTO(
            "https://example.com/exercise.git",
            "abc123",
            null,
            "test-token",
            "main"
        );

        var testRepo = new RepositoryInfoDTO(
            "https://example.com/tests.git",
            "def456",
            null,
            "test-token",
            "main"
        );

        return new BuildTriggerRequestDTO(
            1L, // exerciseId
            1L, // participationId
            exerciseRepo,
            testRepo,
            null, // solutionRepository
            List.of(), // auxiliaryRepositories
            "node('docker') { stage('Test') { echo 'Hello World' } }", // buildScript
            "ASSIGNMENT", // triggeredByPushTo
            "java", // programmingLanguage
            null // additionalProperties
        );
    }
}