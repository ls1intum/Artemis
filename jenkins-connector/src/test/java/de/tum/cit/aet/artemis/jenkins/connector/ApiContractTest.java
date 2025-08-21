package de.tum.cit.aet.artemis.jenkins.connector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.jenkins.connector.dto.BuildStatusResponseDTO;
import de.tum.cit.aet.artemis.jenkins.connector.dto.BuildTriggerRequestDTO;
import de.tum.cit.aet.artemis.jenkins.connector.dto.RepositoryInfoDTO;

/**
 * Test for API contract DTOs serialization/deserialization.
 */
class ApiContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testBuildTriggerRequestSerialization() throws Exception {
        // Arrange
        var exerciseRepo = new RepositoryInfoDTO(
            "https://example.com/exercise.git",
            "abc123",
            "/tmp/exercise",
            "token123",
            "main"
        );

        var request = new BuildTriggerRequestDTO(
            1L, // exerciseId
            1L, // participationId
            exerciseRepo,
            null, // testRepository
            null, // solutionRepository
            List.of(), // auxiliaryRepositories
            "pipeline { agent any; stages { stage('Test') { steps { echo 'Hello' } } } }", // buildScript
            "ASSIGNMENT", // triggeredByPushTo
            "java", // programmingLanguage
            null // additionalProperties
        );

        // Act
        String json = objectMapper.writeValueAsString(request);
        BuildTriggerRequestDTO deserialized = objectMapper.readValue(json, BuildTriggerRequestDTO.class);

        // Assert
        assertThat(deserialized).isEqualTo(request);
        assertThat(deserialized.exerciseId()).isEqualTo(1L);
        assertThat(deserialized.exerciseRepository().url()).isEqualTo("https://example.com/exercise.git");
        assertThat(deserialized.buildScript()).contains("pipeline");
    }

    @Test
    void testBuildStatusResponseSerialization() throws Exception {
        // Arrange
        var status = new BuildStatusResponseDTO(
            "build-123",
            BuildStatusResponseDTO.BuildStatus.RUNNING,
            null, // startTime
            null, // endTime
            "Build failed due to compile error"
        );

        // Act
        String json = objectMapper.writeValueAsString(status);
        BuildStatusResponseDTO deserialized = objectMapper.readValue(json, BuildStatusResponseDTO.class);

        // Assert
        assertThat(deserialized).isEqualTo(status);
        assertThat(deserialized.status()).isEqualTo(BuildStatusResponseDTO.BuildStatus.RUNNING);
        assertThat(deserialized.buildId()).isEqualTo("build-123");
    }

    @Test
    void testRepositoryInfoSerialization() throws Exception {
        // Arrange
        var repo = new RepositoryInfoDTO(
            "https://git.example.com/repo.git",
            "commit123",
            "/workspace/repo",
            "access-token",
            "develop"
        );

        // Act
        String json = objectMapper.writeValueAsString(repo);
        RepositoryInfoDTO deserialized = objectMapper.readValue(json, RepositoryInfoDTO.class);

        // Assert
        assertThat(deserialized).isEqualTo(repo);
        assertThat(deserialized.url()).isEqualTo("https://git.example.com/repo.git");
        assertThat(deserialized.commitHash()).isEqualTo("commit123");
    }

    @Test
    void testUUIDSerialization() throws Exception {
        // Arrange
        UUID testUuid = UUID.randomUUID();

        // Act
        String json = objectMapper.writeValueAsString(testUuid);
        UUID deserialized = objectMapper.readValue(json, UUID.class);

        // Assert
        assertThat(deserialized).isEqualTo(testUuid);
    }
}