package de.tum.cit.aet.artemis.hyperion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.hyperion.config.HyperionTestConfiguration;
import de.tum.cit.aet.artemis.hyperion.config.HyperionTestReviewAndRefineService;
import de.tum.cit.aet.artemis.hyperion.proto.Priority;
import de.tum.cit.aet.artemis.hyperion.proto.SuggestionItem;
import de.tum.cit.aet.artemis.hyperion.service.HyperionReviewAndRefineService;
import de.tum.cit.aet.artemis.hyperion.service.websocket.HyperionWebsocketService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import io.grpc.Status;

/**
 * Comprehensive test suite for Hyperion suggestions WebSocket functionality.
 * Uses setSuggestBehavior to simulate various gRPC streaming scenarios.
 */
@Import(HyperionTestConfiguration.class)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class HyperionSuggestionsWebsocketTest extends AbstractHyperionTest {

    private static final String TEST_PREFIX = "hyperion-websocket";

    @Autowired
    private HyperionReviewAndRefineService hyperionReviewAndRefineService;

    @Autowired
    private WebsocketMessagingService websocketMessagingService;

    @Autowired
    private HyperionTestReviewAndRefineService testReviewAndRefineService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private HyperionWebsocketService hyperionWebsocketService;

    private Course course;

    private User instructor;

    private User editor;

    @BeforeEach
    void setUp() {
        // Always reset to default behavior before each test
        testReviewAndRefineService.reset();

        // Create users with different roles
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        editor = userUtilService.getUserByLogin(TEST_PREFIX + "editor1");

        // Create course
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
    }

    // ========== Default Behavior Tests ==========

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSuggestImprovements_defaultBehavior_sendsCorrectMessages() {
        // Given - Default behavior (3 suggestions + completion)
        String problemStatement = "Create a Java program that sorts an array of integers using bubble sort algorithm.";

        // When
        hyperionReviewAndRefineService.suggestImprovements(instructor, course.getId(), problemStatement);

        // Then - Verify 4 messages: 3 suggestions + 1 completion
        verify(websocketMessagingService, times(4)).sendMessageToUser(eq(TEST_PREFIX + "instructor1"), eq("/topic/hyperion/suggestions/" + course.getId()), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testSuggestImprovements_defaultBehavior_asEditor() {
        // Given - Default behavior with editor user
        String problemStatement = "Implement a calculator in Java.";

        // When
        hyperionReviewAndRefineService.suggestImprovements(editor, course.getId(), problemStatement);

        // Then - Verify messages sent to editor
        verify(websocketMessagingService, times(4)).sendMessageToUser(eq(TEST_PREFIX + "editor1"), eq("/topic/hyperion/suggestions/" + course.getId()), any());
    }

    // ========== No Suggestions Scenario ==========

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSuggestImprovements_noSuggestions_onlyCompletionMessage() {
        // Given - Configure to return no suggestions (perfect problem statement)
        testReviewAndRefineService.setSuggestBehavior((request, responseObserver) -> {
            // No suggestions, just complete immediately
            responseObserver.onCompleted();
            return null;
        });
        String problemStatement = "Perfect problem statement requiring no improvements.";

        // When
        hyperionReviewAndRefineService.suggestImprovements(instructor, course.getId(), problemStatement);

        // Then - Only completion message should be sent
        verify(websocketMessagingService, times(1)).sendMessageToUser(eq(TEST_PREFIX + "instructor1"), eq("/topic/hyperion/suggestions/" + course.getId()), any());
    }

    // ========== Many Suggestions Scenario ==========

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSuggestImprovements_manySuggestions_allPriorities() {
        // Given - Configure to return 15 suggestions with all priority levels
        testReviewAndRefineService.setSuggestBehavior((request, responseObserver) -> {
            for (int i = 0; i < 15; i++) {
                Priority priority = switch (i % 3) {
                    case 0 -> Priority.HIGH;
                    case 1 -> Priority.MEDIUM;
                    default -> Priority.LOW;
                };

                responseObserver.onNext(SuggestionItem.newBuilder().setDescription("Improvement " + (i + 1) + ": " + priority.name() + " priority suggestion").setIndexStart(i * 10)
                        .setIndexEnd((i * 10) + 9).setPriority(priority).build());
            }
            responseObserver.onCompleted();
            return null;
        });
        String problemStatement = "Complex problem statement with many potential improvements.";

        // When
        hyperionReviewAndRefineService.suggestImprovements(instructor, course.getId(), problemStatement);

        // Then - Verify 16 messages: 15 suggestions + 1 completion
        verify(websocketMessagingService, times(16)).sendMessageToUser(eq(TEST_PREFIX + "instructor1"), eq("/topic/hyperion/suggestions/" + course.getId()), any());
    }

    // ========== High Priority Only Scenario ==========

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSuggestImprovements_highPriorityOnly_criticalIssues() {
        // Given - Configure to return only critical high-priority suggestions
        testReviewAndRefineService.setSuggestBehavior((request, responseObserver) -> {
            responseObserver.onNext(
                    SuggestionItem.newBuilder().setDescription("CRITICAL: Missing clear learning objectives").setIndexStart(0).setIndexEnd(30).setPriority(Priority.HIGH).build());

            responseObserver.onNext(
                    SuggestionItem.newBuilder().setDescription("CRITICAL: No example input/output provided").setIndexStart(35).setIndexEnd(65).setPriority(Priority.HIGH).build());

            responseObserver
                    .onNext(SuggestionItem.newBuilder().setDescription("CRITICAL: Unclear success criteria").setIndexStart(70).setIndexEnd(95).setPriority(Priority.HIGH).build());

            responseObserver.onCompleted();
            return null;
        });
        String problemStatement = "Implement a sorting algorithm.";

        // When
        hyperionReviewAndRefineService.suggestImprovements(instructor, course.getId(), problemStatement);

        // Then - Verify 4 messages: 3 critical suggestions + 1 completion
        verify(websocketMessagingService, times(4)).sendMessageToUser(eq(TEST_PREFIX + "instructor1"), eq("/topic/hyperion/suggestions/" + course.getId()), any());
    }

    // ========== Streaming Progress Scenario ==========

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSuggestImprovements_streamingProgress_incrementalSuggestions() {
        // Given - Configure to simulate incremental streaming of suggestions
        testReviewAndRefineService.setSuggestBehavior((request, responseObserver) -> {
            // Simulate AI analyzing and providing suggestions progressively
            String[] suggestions = { "Add clear problem statement title", "Specify input format requirements", "Define expected output format", "Include complexity requirements",
                    "Add example test cases", "Clarify edge case handling", "Specify performance constraints" };

            for (int i = 0; i < suggestions.length; i++) {
                Priority priority = i < 3 ? Priority.HIGH : (i < 5 ? Priority.MEDIUM : Priority.LOW);
                responseObserver.onNext(SuggestionItem.newBuilder().setDescription(suggestions[i]).setIndexStart(i * 20).setIndexEnd((i * 20) + 19).setPriority(priority).build());
            }
            responseObserver.onCompleted();
            return null;
        });
        String problemStatement = "Write a program that processes data.";

        // When
        hyperionReviewAndRefineService.suggestImprovements(instructor, course.getId(), problemStatement);

        // Then - Verify 8 messages: 7 suggestions + 1 completion
        verify(websocketMessagingService, times(8)).sendMessageToUser(eq(TEST_PREFIX + "instructor1"), eq("/topic/hyperion/suggestions/" + course.getId()), any());
    }

    // ========== Error Handling Scenarios ==========

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSuggestImprovements_immediateGrpcError_errorMessage() {
        // Given - Configure to fail immediately with gRPC error
        testReviewAndRefineService.setSuggestBehavior((request, responseObserver) -> {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Problem statement is too short or invalid").asRuntimeException());
            return null;
        });
        String problemStatement = "Bad.";

        // When
        hyperionReviewAndRefineService.suggestImprovements(instructor, course.getId(), problemStatement);

        // Then - Verify error message sent via WebSocket
        verify(websocketMessagingService, times(1)).sendMessageToUser(eq(TEST_PREFIX + "instructor1"), eq("/topic/hyperion/suggestions/" + course.getId()), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSuggestImprovements_partialStreamWithError_partialResults() {
        // Given - Configure to send some suggestions then fail
        testReviewAndRefineService.setSuggestBehavior((request, responseObserver) -> {
            // Send 3 successful suggestions
            responseObserver.onNext(
                    SuggestionItem.newBuilder().setDescription("Successfully analyzed: Add problem context").setIndexStart(0).setIndexEnd(15).setPriority(Priority.HIGH).build());

            responseObserver.onNext(SuggestionItem.newBuilder().setDescription("Successfully analyzed: Clarify requirements").setIndexStart(20).setIndexEnd(35)
                    .setPriority(Priority.MEDIUM).build());

            responseObserver
                    .onNext(SuggestionItem.newBuilder().setDescription("Successfully analyzed: Add examples").setIndexStart(40).setIndexEnd(55).setPriority(Priority.LOW).build());

            // Then encounter an error during processing
            responseObserver.onError(Status.RESOURCE_EXHAUSTED.withDescription("AI service quota exceeded during analysis").asRuntimeException());
            return null;
        });
        String problemStatement = "Complex problem requiring detailed analysis.";

        // When
        hyperionReviewAndRefineService.suggestImprovements(instructor, course.getId(), problemStatement);

        // Then - Verify 4 messages: 3 suggestions + 1 error message
        verify(websocketMessagingService, times(4)).sendMessageToUser(eq(TEST_PREFIX + "instructor1"), eq("/topic/hyperion/suggestions/" + course.getId()), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSuggestImprovements_serviceUnavailableError() {
        // Given - Configure to simulate service unavailable
        testReviewAndRefineService.setSuggestBehavior((request, responseObserver) -> {
            responseObserver.onError(Status.UNAVAILABLE.withDescription("Hyperion AI service is temporarily unavailable").asRuntimeException());
            return null;
        });
        String problemStatement = "Test problem statement.";

        // When
        hyperionReviewAndRefineService.suggestImprovements(instructor, course.getId(), problemStatement);

        // Then - Verify service unavailable error handled gracefully
        verify(websocketMessagingService, times(1)).sendMessageToUser(eq(TEST_PREFIX + "instructor1"), eq("/topic/hyperion/suggestions/" + course.getId()), any());
    }

    // ========== Edge Case Scenarios ==========

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSuggestImprovements_singleSuggestion_minimalResponse() {
        // Given - Configure to return exactly one suggestion
        testReviewAndRefineService.setSuggestBehavior((request, responseObserver) -> {
            responseObserver.onNext(
                    SuggestionItem.newBuilder().setDescription("Single improvement: Add more detail").setIndexStart(10).setIndexEnd(25).setPriority(Priority.MEDIUM).build());
            responseObserver.onCompleted();
            return null;
        });
        String problemStatement = "Almost perfect problem statement with minor issue.";

        // When
        hyperionReviewAndRefineService.suggestImprovements(instructor, course.getId(), problemStatement);

        // Then - Verify 2 messages: 1 suggestion + 1 completion
        verify(websocketMessagingService, times(2)).sendMessageToUser(eq(TEST_PREFIX + "instructor1"), eq("/topic/hyperion/suggestions/" + course.getId()), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSuggestImprovements_veryLongProblemStatement_manyDetailedSuggestions() {
        // Given - Configure detailed analysis for long problem statement
        testReviewAndRefineService.setSuggestBehavior((request, responseObserver) -> {
            // Simulate detailed analysis of a comprehensive problem statement
            for (int section = 1; section <= 5; section++) {
                responseObserver.onNext(SuggestionItem.newBuilder().setDescription("Section " + section + " improvement: Enhance clarity and precision").setIndexStart(section * 50)
                        .setIndexEnd((section * 50) + 49).setPriority(section <= 2 ? Priority.HIGH : Priority.MEDIUM).build());
            }
            responseObserver.onCompleted();
            return null;
        });
        String problemStatement = "Very comprehensive problem statement with multiple sections, detailed requirements, complex constraints, and extensive background information that needs thorough analysis and improvement suggestions.";

        // When
        hyperionReviewAndRefineService.suggestImprovements(instructor, course.getId(), problemStatement);

        // Then - Verify 6 messages: 5 detailed suggestions + 1 completion
        verify(websocketMessagingService, times(6)).sendMessageToUser(eq(TEST_PREFIX + "instructor1"), eq("/topic/hyperion/suggestions/" + course.getId()), any());
    }

    // ========== Direct WebSocket Service Tests ==========

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDirectWebSocketService_sendMessage() {
        // Given - Create a test suggestion status update
        var suggestionItem = new de.tum.cit.aet.artemis.hyperion.dto.HyperionSuggestionItemDTO("Direct WebSocket test suggestion", 0, 20, "HIGH");
        var statusUpdate = de.tum.cit.aet.artemis.hyperion.dto.HyperionSuggestionStatusUpdateDTO.ofSuggestion(suggestionItem);

        // When - Send message directly through WebSocket service
        hyperionWebsocketService.send(instructor.getLogin(), "suggestions/" + course.getId(), statusUpdate);

        // Then - Verify the WebSocket message was sent correctly
        verify(websocketMessagingService, times(1)).sendMessageToUser(eq(TEST_PREFIX + "instructor1"), eq("/topic/hyperion/suggestions/" + course.getId()), eq(statusUpdate));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDirectWebSocketService_completionMessage() {
        // Given - Create completion status update
        var completionUpdate = de.tum.cit.aet.artemis.hyperion.dto.HyperionSuggestionStatusUpdateDTO.ofCompletion();

        // When - Send completion message
        hyperionWebsocketService.send(instructor.getLogin(), "suggestions/" + course.getId(), completionUpdate);

        // Then - Verify completion message sent
        verify(websocketMessagingService, times(1)).sendMessageToUser(eq(TEST_PREFIX + "instructor1"), eq("/topic/hyperion/suggestions/" + course.getId()), eq(completionUpdate));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDirectWebSocketService_errorMessage() {
        // Given - Create error status update
        var errorUpdate = de.tum.cit.aet.artemis.hyperion.dto.HyperionSuggestionStatusUpdateDTO.ofError("Test error message");

        // When - Send error message
        hyperionWebsocketService.send(instructor.getLogin(), "suggestions/" + course.getId(), errorUpdate);

        // Then - Verify error message sent
        verify(websocketMessagingService, times(1)).sendMessageToUser(eq(TEST_PREFIX + "instructor1"), eq("/topic/hyperion/suggestions/" + course.getId()), eq(errorUpdate));
    }
}
