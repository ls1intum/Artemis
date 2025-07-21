package de.tum.cit.aet.artemis.programming;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.hyperion.dto.HyperionRepositoryFile;
import de.tum.cit.aet.artemis.programming.service.hyperion.dto.HyperionSolutionGenerationResponse;

@TestPropertySource(properties = { "artemis.hyperion.enabled=true", "artemis.hyperion.url=http://localhost:8080" })
class SolutionGenerationIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "solutiongeneration";

    @MockBean(name = "hyperionRestTemplate")
    private RestTemplate hyperionRestTemplate;

    @MockBean
    private GitService gitService;

    private Course course;

    private ProgrammingExercise programmingExercise;

    private User instructor;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");

        course = courseUtilService.createCourse();
        programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course, false, ProgrammingLanguage.JAVA);
        programmingExercise.setTitle("Binary Search Implementation");
        programmingExercise
                .setProblemStatement("Implement a binary search algorithm that finds the index of a target value in a sorted array. Return -1 if the target is not found.");
        programmingExercise.setMaxPoints(10.0);
        programmingExerciseRepository.save(programmingExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateSolutionRepository_Success() throws Exception {
        // Arrange
        var mockResponse = createMockHyperionResponse();
        when(hyperionRestTemplate.postForEntity(any(String.class), any(), eq(HyperionSolutionGenerationResponse.class)))
                .thenReturn(new ResponseEntity<HyperionSolutionGenerationResponse>(mockResponse, HttpStatus.OK));

        // Mock Git operations to avoid actual repository access
        Repository mockRepo = mock(Repository.class);
        Path tempDir = java.nio.file.Files.createTempDirectory("test-repo");
        when(mockRepo.getLocalPath()).thenReturn(tempDir);
        when(gitService.getOrCheckoutRepository(any(), anyBoolean())).thenReturn(mockRepo);
        doNothing().when(gitService).resetToOriginHead(any());
        doNothing().when(gitService).pullIgnoreConflicts(any());
        doNothing().when(gitService).commitAndPush(any(), anyString(), anyBoolean(), any());

        // Act & Assert
        request.performMvcRequest(
                put("/api/programming/programming-exercises/{exerciseId}/generate-solution-repository", programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Verify Hyperion API was called
        verify(hyperionRestTemplate).postForEntity(eq("http://localhost:8080/api/generate-solution"), any(), eq(HyperionSolutionGenerationResponse.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGenerateSolutionRepository_Forbidden_Student() throws Exception {
        // Act & Assert
        request.performMvcRequest(
                put("/api/programming/programming-exercises/{exerciseId}/generate-solution-repository", programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateSolutionRepository_ExerciseNotFound() throws Exception {
        // Act & Assert
        request.performMvcRequest(put("/api/programming/programming-exercises/{exerciseId}/generate-solution-repository", 99999L).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateSolutionRepository_HyperionServiceError() throws Exception {
        // Arrange
        when(hyperionRestTemplate.postForEntity(any(String.class), any(), eq(HyperionSolutionGenerationResponse.class)))
                .thenThrow(new RuntimeException("Hyperion service unavailable"));

        // Mock Git operations to avoid actual repository access
        Repository mockRepo = mock(Repository.class);
        Path tempDir = java.nio.file.Files.createTempDirectory("test-repo");
        when(mockRepo.getLocalPath()).thenReturn(tempDir);
        when(gitService.getOrCheckoutRepository(any(), anyBoolean())).thenReturn(mockRepo);
        doNothing().when(gitService).resetToOriginHead(any());
        doNothing().when(gitService).pullIgnoreConflicts(any());
        doNothing().when(gitService).commitAndPush(any(), anyString(), anyBoolean(), any());

        // Act & Assert
        request.performMvcRequest(
                put("/api/programming/programming-exercises/{exerciseId}/generate-solution-repository", programmingExercise.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
    }

    private HyperionSolutionGenerationResponse createMockHyperionResponse() {
        var files = List.of(new HyperionRepositoryFile("src/binary_search.py",
                "def binary_search(arr, target):\n    \"\"\"Binary search implementation.\"\"\"\n    left, right = 0, len(arr) - 1\n    \n    while left <= right:\n        mid = (left + right) // 2\n        if arr[mid] == target:\n            return mid\n        elif arr[mid] < target:\n            left = mid + 1\n        else:\n            right = mid - 1\n    \n    return -1\n"),
                new HyperionRepositoryFile("tests/test_binary_search.py",
                        "import pytest\nfrom src.binary_search import binary_search\n\ndef test_binary_search_found():\n    arr = [1, 2, 3, 4, 5]\n    assert binary_search(arr, 3) == 2\n\ndef test_binary_search_not_found():\n    arr = [1, 2, 3, 4, 5]\n    assert binary_search(arr, 6) == -1\n"),
                new HyperionRepositoryFile("README.md", "# Binary Search Implementation\n\nThis project implements a binary search algorithm...\n"));

        var repository = new HyperionSolutionGenerationResponse.Repository(files);
        var metadata = new HyperionSolutionGenerationResponse.Metadata("550e8400-e29b-41d4-a716-446655440000");

        return new HyperionSolutionGenerationResponse(repository, metadata);
    }
}
