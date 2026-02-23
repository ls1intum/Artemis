package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRefinementResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementTargetedRefinementRequestDTO;

class HyperionProblemStatementRefinementServiceTest {

    @Mock
    private ChatModel chatModel;

    private HyperionProblemStatementRefinementService hyperionProblemStatementRefinementService;

    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        ChatClient chatClient = ChatClient.create(chatModel);
        var templateService = new HyperionPromptTemplateService();
        this.hyperionProblemStatementRefinementService = new HyperionProblemStatementRefinementService(chatClient, templateService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void refineProblemStatement_returnsRefinedStatement() {
        String originalStatement = "Original problem statement";
        String refinedStatement = "Refined problem statement with improvements";
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(refinedStatement)))));

        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");
        ProblemStatementRefinementResponseDTO resp = hyperionProblemStatementRefinementService.refineProblemStatement(course, originalStatement, "Make it better");
        assertThat(resp).isNotNull();
        assertThat(resp.refinedProblemStatement()).isEqualTo(refinedStatement);
    }

    @Test
    void refineProblemStatement_throwsExceptionOnAIFailure() {
        String originalStatement = "Original problem statement";
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("AI service unavailable"));

        var course = new Course();
        course.setId(123L);
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementRefinementService.refineProblemStatement(course, originalStatement, "Refine this"))
                .isInstanceOf(InternalServerErrorAlertException.class).hasMessageContaining("Failed to refine problem statement");
    }

    @Test
    void refineProblemStatement_throwsExceptionOnExcessivelyLongResponse() {
        String originalStatement = "Original problem statement";
        // Generate a string longer than MAX_PROBLEM_STATEMENT_LENGTH (50,000 characters)
        String excessivelyLongRefinement = "a".repeat(50_001);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(excessivelyLongRefinement)))));

        var course = new Course();
        course.setId(456L);
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementRefinementService.refineProblemStatement(course, originalStatement, "Refine this"))
                .isInstanceOf(InternalServerErrorAlertException.class).hasMessageContaining("exceeds the maximum allowed length");
    }

    @Test
    void refineProblemStatement_throwsExceptionWhenUserPromptIsNull() {
        String originalStatement = "Original problem statement";
        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        // Should throw exception when userPrompt is null
        assertThatThrownBy(() -> hyperionProblemStatementRefinementService.refineProblemStatement(course, originalStatement, null)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("User prompt cannot be empty");
    }

    @Test
    void refineProblemStatement_handlesNullCourseFields() {
        String originalStatement = "Original problem statement";
        String refinedStatement = "Refined with default course info";
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(refinedStatement)))));

        var course = new Course();
        // Leave title and description null

        // Should use default values when course fields are null
        ProblemStatementRefinementResponseDTO resp = hyperionProblemStatementRefinementService.refineProblemStatement(course, originalStatement, "Refine this");
        assertThat(resp).isNotNull();
        assertThat(resp.refinedProblemStatement()).isEqualTo(refinedStatement);
    }

    @Test
    void refineProblemStatement_acceptsMaximumLengthResponse() {
        String originalStatement = "Original problem statement";
        // Generate a string exactly at MAX_PROBLEM_STATEMENT_LENGTH (50,000 characters)
        String maxLengthRefinement = "a".repeat(50_000);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(maxLengthRefinement)))));

        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        // Should succeed with exactly 50,000 characters
        ProblemStatementRefinementResponseDTO resp = hyperionProblemStatementRefinementService.refineProblemStatement(course, originalStatement, "Refine this");
        assertThat(resp).isNotNull();
        assertThat(resp.refinedProblemStatement()).hasSize(50_000);
    }

    @Test
    void refineProblemStatement_throwsExceptionWhenInputIsNull() {
        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        // Should throw exception when input is null
        assertThatThrownBy(() -> hyperionProblemStatementRefinementService.refineProblemStatement(course, null, "Refine this")).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Cannot refine empty problem statement");
    }

    @Test
    void refineProblemStatement_throwsExceptionWhenInputIsBlank() {
        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        // Should throw exception when input is blank
        assertThatThrownBy(() -> hyperionProblemStatementRefinementService.refineProblemStatement(course, "   ", "Refine this")).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Cannot refine empty problem statement");
    }

    @Test
    void refineProblemStatement_throwsExceptionWhenChatClientIsNull() {
        var serviceWithNullClient = new HyperionProblemStatementRefinementService(null, new HyperionPromptTemplateService());
        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> serviceWithNullClient.refineProblemStatement(course, "Original problem statement", "Refine this"))
                .isInstanceOf(InternalServerErrorAlertException.class).hasMessageContaining("AI chat client is not configured");
    }

    @Test
    void refineProblemStatement_throwsExceptionWhenResponseIsNull() {
        String originalStatement = "Original problem statement";
        // AI returns null content
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(null)))));

        var course = new Course();
        course.setId(999L);
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementRefinementService.refineProblemStatement(course, originalStatement, "Refine this"))
                .isInstanceOf(InternalServerErrorAlertException.class).hasMessageContaining("Refined problem statement is null or empty");
    }

    @Test
    void refineProblemStatement_throwsExceptionWhenResponseIsBlank() {
        String originalStatement = "Original problem statement";
        // AI returns blank content
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage("   ")))));

        var course = new Course();
        course.setId(999L);
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementRefinementService.refineProblemStatement(course, originalStatement, "Refine this"))
                .isInstanceOf(InternalServerErrorAlertException.class).hasMessageContaining("Refined problem statement is null or empty");
    }

    @Test
    void refineProblemStatement_throwsExceptionWhenRefinementUnchanged() {
        String originalStatement = "Original problem statement";
        // AI returns the exact same content
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(originalStatement)))));

        var course = new Course();
        course.setId(789L);
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementRefinementService.refineProblemStatement(course, originalStatement, "Refine this"))
                .isInstanceOf(BadRequestAlertException.class).hasMessageContaining("same after refinement");
    }

    @Test
    void refineProblemStatement_throwsExceptionWhenUserPromptTooLong() {
        String originalStatement = "Original problem statement";
        // 1001 characters exceeds MAX_USER_PROMPT_LENGTH (1000)
        String tooLongPrompt = "a".repeat(1001);
        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementRefinementService.refineProblemStatement(course, originalStatement, tooLongPrompt))
                .isInstanceOf(BadRequestAlertException.class).hasMessageContaining("exceeds maximum length");
    }

    @Test
    void refineProblemStatement_throwsExceptionWhenProblemStatementTooLong() {
        // 50001 characters exceeds MAX_PROBLEM_STATEMENT_LENGTH (50000)
        String tooLongProblemStatement = "a".repeat(50_001);
        var course = createTestCourse();

        assertThatThrownBy(() -> hyperionProblemStatementRefinementService.refineProblemStatement(course, tooLongProblemStatement, "Refine this"))
                .isInstanceOf(BadRequestAlertException.class).hasMessageContaining("exceeds maximum length");
    }

    // Targeted refinement tests

    private Course createTestCourse() {
        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");
        return course;
    }

    @Test
    void refineProblemStatementTargeted_returnsRefinedStatement() {
        String originalText = "Line one\nLine two\nLine three";
        String refinedText = "Line one\nImproved line two\nLine three";
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(refinedText)))));

        var request = new ProblemStatementTargetedRefinementRequestDTO(originalText, 2, 2, null, null, "Improve this line");
        ProblemStatementRefinementResponseDTO resp = hyperionProblemStatementRefinementService.refineProblemStatementTargeted(createTestCourse(), request);
        assertThat(resp).isNotNull();
        assertThat(resp.refinedProblemStatement()).isEqualTo(refinedText);
    }

    @Test
    void refineProblemStatementTargeted_withColumnRange_returnsRefinedStatement() {
        String originalText = "Hello world example";
        String refinedText = "Hello universe example";
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(refinedText)))));

        var request = new ProblemStatementTargetedRefinementRequestDTO(originalText, 1, 1, 7, 12, "Replace world");
        ProblemStatementRefinementResponseDTO resp = hyperionProblemStatementRefinementService.refineProblemStatementTargeted(createTestCourse(), request);
        assertThat(resp).isNotNull();
        assertThat(resp.refinedProblemStatement()).isEqualTo(refinedText);
    }

    @Test
    void refineProblemStatementTargeted_multiLineWithColumnRange_returnsRefinedStatement() {
        String originalText = "First line content\nSecond line content\nThird line content";
        String refinedText = "First line content\nModified content\nThird line content";
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(refinedText)))));

        var request = new ProblemStatementTargetedRefinementRequestDTO(originalText, 1, 2, 7, 12, "Merge these lines");
        ProblemStatementRefinementResponseDTO resp = hyperionProblemStatementRefinementService.refineProblemStatementTargeted(createTestCourse(), request);
        assertThat(resp).isNotNull();
        assertThat(resp.refinedProblemStatement()).isEqualTo(refinedText);
    }

    @Test
    void refineProblemStatementTargeted_throwsExceptionOnAIFailure() {
        String originalText = "Line one\nLine two";
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("AI service unavailable"));

        var request = new ProblemStatementTargetedRefinementRequestDTO(originalText, 1, 2, null, null, "Improve this");
        assertThatThrownBy(() -> hyperionProblemStatementRefinementService.refineProblemStatementTargeted(createTestCourse(), request))
                .isInstanceOf(InternalServerErrorAlertException.class).hasMessageContaining("Failed to refine problem statement");
    }

    @Test
    void refineProblemStatementTargeted_throwsExceptionWhenResponseIsNull() {
        String originalText = "Line one\nLine two";
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(null)))));

        var request = new ProblemStatementTargetedRefinementRequestDTO(originalText, 1, 1, null, null, "Improve this");
        assertThatThrownBy(() -> hyperionProblemStatementRefinementService.refineProblemStatementTargeted(createTestCourse(), request))
                .isInstanceOf(InternalServerErrorAlertException.class).hasMessageContaining("null");
    }

    @Test
    void refineProblemStatementTargeted_throwsExceptionWhenResponseUnchanged() {
        String originalText = "Unchanged problem statement";
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(originalText)))));

        var request = new ProblemStatementTargetedRefinementRequestDTO(originalText, 1, 1, null, null, "Change something");
        assertThatThrownBy(() -> hyperionProblemStatementRefinementService.refineProblemStatementTargeted(createTestCourse(), request)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("same after refinement");
    }

    @Test
    void refineProblemStatementTargeted_throwsExceptionWhenChatClientIsNull() {
        var serviceWithNullClient = new HyperionProblemStatementRefinementService(null, new HyperionPromptTemplateService());
        String originalText = "Some text";

        var request = new ProblemStatementTargetedRefinementRequestDTO(originalText, 1, 1, null, null, "Fix this");
        assertThatThrownBy(() -> serviceWithNullClient.refineProblemStatementTargeted(createTestCourse(), request)).isInstanceOf(InternalServerErrorAlertException.class)
                .hasMessageContaining("AI chat client is not configured");
    }

    @Test
    void refineProblemStatementTargeted_throwsExceptionWhenProblemStatementIsEmpty() {
        var request = new ProblemStatementTargetedRefinementRequestDTO("   ", 1, 1, null, null, "Fix this");
        assertThatThrownBy(() -> hyperionProblemStatementRefinementService.refineProblemStatementTargeted(createTestCourse(), request)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Cannot refine empty problem statement");
    }

    @Test
    void refineProblemStatementTargeted_throwsExceptionWhenLineRangeOutOfBounds() {
        // Only 1 line but requesting line 5
        String originalText = "Single line";

        var request = new ProblemStatementTargetedRefinementRequestDTO(originalText, 5, 5, null, null, "Fix this");
        assertThatThrownBy(() -> hyperionProblemStatementRefinementService.refineProblemStatementTargeted(createTestCourse(), request)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Invalid line range");
    }

    @Test
    void refineProblemStatementTargeted_throwsExceptionWhenInstructionIsEmpty() {
        assertThatThrownBy(() -> new ProblemStatementTargetedRefinementRequestDTO("Some text", 1, 1, null, null, "   ")).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("instruction must not be empty after trimming");
    }

    @Test
    void refineProblemStatementTargeted_withEndColumnAtEndOfLine_succeeds() {
        // Monaco sends endColumn = line.length() + 1 for "select to end of line" (1-indexed, exclusive)
        String originalText = "Hello";
        String refinedText = "World";
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(refinedText)))));

        // endColumn=6 on a 5-char line ("Hello"): 1-indexed exclusive → selects chars 1-5
        var request = new ProblemStatementTargetedRefinementRequestDTO(originalText, 1, 1, 1, 6, "Replace entire line");
        ProblemStatementRefinementResponseDTO resp = hyperionProblemStatementRefinementService.refineProblemStatementTargeted(createTestCourse(), request);
        assertThat(resp).isNotNull();
        assertThat(resp.refinedProblemStatement()).isEqualTo(refinedText);
    }

    @Test
    void refineProblemStatementTargeted_withEndColumnBeyondLineLength_clamps() {
        // Monaco may send endColumn beyond the line length in edge cases
        String originalText = "Hi";
        String refinedText = "Bye";
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(refinedText)))));

        // endColumn=100 on a 2-char line: should clamp to line length
        var request = new ProblemStatementTargetedRefinementRequestDTO(originalText, 1, 1, 1, 100, "Replace it");
        ProblemStatementRefinementResponseDTO resp = hyperionProblemStatementRefinementService.refineProblemStatementTargeted(createTestCourse(), request);
        assertThat(resp).isNotNull();
        assertThat(resp.refinedProblemStatement()).isEqualTo(refinedText);
    }

    @Test
    void refineProblemStatementTargeted_throwsExceptionWhenResponseIsBlank() {
        String originalText = "Line one\nLine two";
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage("   ")))));

        var request = new ProblemStatementTargetedRefinementRequestDTO(originalText, 1, 1, null, null, "Improve this");
        assertThatThrownBy(() -> hyperionProblemStatementRefinementService.refineProblemStatementTargeted(createTestCourse(), request))
                .isInstanceOf(InternalServerErrorAlertException.class).hasMessageContaining("null or empty");
    }

    @Test
    void refineProblemStatementTargeted_stripsLineNumbersFromResponse() {
        String originalText = "Line one\nLine two\nLine three";
        // LLM returns content with line-number prefixes despite being told not to
        String llmResponse = "1: Line one\n2: Improved line two\n3: Line three";
        String expectedStripped = "Line one\nImproved line two\nLine three";
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(llmResponse)))));

        var request = new ProblemStatementTargetedRefinementRequestDTO(originalText, 2, 2, null, null, "Improve line two");
        ProblemStatementRefinementResponseDTO resp = hyperionProblemStatementRefinementService.refineProblemStatementTargeted(createTestCourse(), request);
        assertThat(resp).isNotNull();
        assertThat(resp.refinedProblemStatement()).isEqualTo(expectedStripped);
    }

    @Test
    void refineProblemStatementTargeted_doesNotStripNonSequentialLineNumbers() {
        String originalText = "Some text";
        // Content starting with digits-colon but not sequential line numbers — should not be stripped
        String llmResponse = "3: First item\n5: Second item";
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(llmResponse)))));

        var request = new ProblemStatementTargetedRefinementRequestDTO(originalText, 1, 1, null, null, "Transform this");
        ProblemStatementRefinementResponseDTO resp = hyperionProblemStatementRefinementService.refineProblemStatementTargeted(createTestCourse(), request);
        assertThat(resp).isNotNull();
        // Non-sequential numbers should be preserved as-is
        assertThat(resp.refinedProblemStatement()).isEqualTo(llmResponse);
    }

    @Test
    void refineProblemStatementTargeted_preservesContentWithLeadingDigitsColon() {
        String originalText = "Some text";
        // Legitimate content that happens to start with "1: " on first line but not on second
        String llmResponse = "1: Introduction\nNo prefix here";
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(llmResponse)))));

        var request = new ProblemStatementTargetedRefinementRequestDTO(originalText, 1, 1, null, null, "Rewrite");
        ProblemStatementRefinementResponseDTO resp = hyperionProblemStatementRefinementService.refineProblemStatementTargeted(createTestCourse(), request);
        assertThat(resp).isNotNull();
        // Because the second non-empty line doesn't have "2: " prefix, content should be preserved
        assertThat(resp.refinedProblemStatement()).isEqualTo(llmResponse);
    }
}
