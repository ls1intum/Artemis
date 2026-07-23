package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementGenerationResponseDTO;

class HyperionProblemStatementGenerationServiceTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private LLMTokenUsageService llmTokenUsageService;

    @Mock
    private UserTestRepository userRepository;

    private HyperionProblemStatementGenerationService hyperionProblemStatementGenerationService;

    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        // Since Spring AI 2.0 the ChatClient merges request options into the model's options (getOptions since RC1, getDefaultOptions before), which must be non-null
        lenient().when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
        lenient().when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        ChatClient chatClient = ChatClient.create(chatModel);
        var templateService = new HyperionPromptTemplateService();
        this.hyperionProblemStatementGenerationService = new HyperionProblemStatementGenerationService(chatClient, templateService, llmTokenUsageService, userRepository);
    }

    @Test
    void generateProblemStatement_returnsGeneratedDraft() {
        String generatedDraft = "Generated draft problem statement";
        ChatResponse chatResponse = org.mockito.Mockito.mock(ChatResponse.class);
        var generation = new Generation(new AssistantMessage(generatedDraft));
        when(chatResponse.getResult()).thenReturn(generation);

        var metadata = org.mockito.Mockito.mock(org.springframework.ai.chat.metadata.ChatResponseMetadata.class);
        var usage = org.mockito.Mockito.mock(org.springframework.ai.chat.metadata.Usage.class);
        when(usage.getPromptTokens()).thenReturn(10);
        when(usage.getCompletionTokens()).thenReturn(20);
        when(metadata.getUsage()).thenReturn(usage);
        when(chatResponse.getMetadata()).thenReturn(metadata);

        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> chatResponse);

        var course = new Course();
        course.setId(123L);
        course.setTitle("Test Course");
        course.setDescription("Test Description");
        ProblemStatementGenerationResponseDTO resp = hyperionProblemStatementGenerationService.generateProblemStatement(course, "Prompt");
        assertThat(resp).isNotNull();
        assertThat(resp.draftProblemStatement()).isEqualTo(generatedDraft);
        verify(llmTokenUsageService).trackChatResponseTokenUsage(eq(chatResponse), eq(LLMServiceType.HYPERION), eq("HYPERION_PROBLEM_GENERATION"), any());
    }

    @Test
    void generateProblemStatement_promptAvoidsPrematureTaskBindingsAndImplementationDetails() {
        String generatedDraft = "Generated draft problem statement";
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(generatedDraft)))));

        var course = new Course();
        course.setId(123L);
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        hyperionProblemStatementGenerationService.generateProblemStatement(course, "Create a Java exercise about rover movement");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        String promptText = promptCaptor.getValue().getInstructions().stream().map(message -> message.getText()).reduce("", (left, right) -> left + "\n" + right);

        assertThat(promptText).contains("Do not include Artemis task bindings or raw task markers");
        assertThat(promptText).contains("Do not invent test method names");
        assertThat(promptText).contains("Do not prescribe a solution architecture unless the instructor explicitly asked for one");
        assertThat(promptText).contains("Do not include an implementation-guidance or implementation-strategy section");
        assertThat(promptText).contains("typically 300–700 words");
        assertThat(promptText).contains("Do not invent delivery mechanisms");
        assertThat(promptText).contains("Do not add submission or deliverable sections");
        assertThat(promptText).contains("Do not invent boundary or invalid-input behavior");
        assertThat(promptText).contains("A normal domain case must not become invalid merely because it exercises a boundary");
        assertThat(promptText).contains("Check every worked example against every stated rule before returning the draft");
        assertThat(promptText).contains("deliberately avoid the domains that textbooks and tutorials most often use");
        assertThat(promptText).contains("Do not end with a summary or recap section");
        assertThat(promptText).doesNotContain("instructor-decisions section", "Instructor Decisions Before Final Generation");
        assertThat(promptText).contains("## STYLE GUIDE", "One `#` title", "2-4 requirement sections");
        assertThat(promptText).doesNotContain("# Loyalty Points", "LoyaltyAccount", "RewardStrategy", "ShippingCalculator", "FeeStrategy");
    }

    @Test
    void generateProblemStatement_usesElevatedSamplingTemperatureForTheCreativeDraftStage() {
        String draft = "# Rover\n\nA fine draft.";
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(draft)))));

        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        hyperionProblemStatementGenerationService.generateProblemStatement(course, "Create a Java exercise about rover movement");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        // The draft is the creative stage: elevated temperature counters domain mode collapse; the agent loop and critic keep the deployment default.
        // The ChatClient merges the request options into the model's options type, so assert the temperature on the ChatOptions contract rather than a concrete class.
        assertThat(promptCaptor.getValue().getOptions().getTemperature()).isEqualTo(0.7);
    }

    @Test
    void generateProblemStatement_flagsAuthoringProcessSectionsAsAdvisoryWithoutBlocking() {
        String artifactDraft = """
                # Library Checkout

                Students summarize checkout events.

                ## Instructor Decisions (if needed)
                Confirm whether fees should use cents or decimal dollars.
                """;
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(artifactDraft)))));

        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        // Authoring-process headings are a semantic heuristic, not a mechanically-certain artifact: the draft is
        // still returned and applied, with an advisory warning for the instructor to review.
        ProblemStatementGenerationResponseDTO resp = hyperionProblemStatementGenerationService.generateProblemStatement(course, "Create a compact library exercise");
        assertThat(resp.draftProblemStatement()).isEqualTo(artifactDraft.trim());
        assertThat(resp.hygieneWarnings()).isNotEmpty();
    }

    @Test
    void generateProblemStatement_rejectsDraftWithGenerationArtifacts() {
        String artifactDraft = """
                # Library Rules

                [task][Implement checkout summaries](testOverdueFees)

                @startuml
                class LibraryProcessor
                @enduml
                """;
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(artifactDraft)))));

        var course = new Course();
        course.setId(123L);
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementGenerationService.generateProblemStatement(course, "Create a compact library exercise"))
                .isInstanceOf(InternalServerErrorAlertException.class).hasMessageContaining("generation-only artifacts");
    }

    @Test
    void generateProblemStatement_rejectsDraftWithLegacyStructuralTestNames() {
        String artifactDraft = """
                # Sorting Design

                Students implement sorting behavior. The structural tests use testClass[Sorter] and testMethods[Sorter].
                """;
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(artifactDraft)))));

        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementGenerationService.generateProblemStatement(course, "Create a sorting exercise"))
                .isInstanceOf(InternalServerErrorAlertException.class).hasMessageContaining("generation-only artifacts");
    }

    @Test
    void generateProblemStatement_flagsUnrequestedJsonExportAsAdvisoryEvenWhenUserAskedForGenericExport() {
        String artifactDraft = """
                # Data Export

                Students should implement CSV summaries and a JSON export.
                """;
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(artifactDraft)))));

        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        ProblemStatementGenerationResponseDTO resp = hyperionProblemStatementGenerationService.generateProblemStatement(course, "Create a CSV export exercise");
        assertThat(resp.draftProblemStatement()).isEqualTo(artifactDraft.trim());
        assertThat(resp.hygieneWarnings()).isNotEmpty();
    }

    @Test
    void generateProblemStatement_flagsPublicApiDetailsAsAdvisoryWhenInstructorAvoidsExactNames() {
        String artifactDraft = """
                # Event Scheduler

                ## Public API
                | Method | Purpose |
                | --- | --- |
                | `boolean addEvent(String id)` | Adds an event. |
                """;
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(artifactDraft)))));

        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        ProblemStatementGenerationResponseDTO resp = hyperionProblemStatementGenerationService.generateProblemStatement(course,
                "Avoid prescribing exact class names and method names");
        assertThat(resp.draftProblemStatement()).isEqualTo(artifactDraft.trim());
        assertThat(resp.hygieneWarnings()).isNotEmpty();
    }

    @Test
    void generateProblemStatement_repairsDraftOnceWhenFirstAttemptContainsArtifacts() {
        String artifactDraft = """
                # Event Scheduler

                [task][Add events](testAddEvent)

                @startuml
                class Scheduler
                @enduml
                """;
        String repairedDraft = """
                # Event Scheduler

                Students reason about intervals, recurring events, conflicts, and invalid ranges.

                ## Example
                | Existing interval | Candidate interval | Conflict? |
                | --- | --- | --- |
                | 10:00-11:00 | 11:00-12:00 | No, when the end is exclusive. |
                """;
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(artifactDraft)))))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(repairedDraft)))));

        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        ProblemStatementGenerationResponseDTO resp = hyperionProblemStatementGenerationService.generateProblemStatement(course,
                "Avoid prescribing an implementation strategy or exact class names");

        assertThat(resp.draftProblemStatement()).isEqualTo(repairedDraft.trim());
    }

    @Test
    void generateProblemStatement_allowsPublicApiDetailsWhenInstructorExplicitlyPermitsThem() {
        String apiDraft = """
                # Rover Movement

                ## Public API
                Students implement `boolean execute(String commands)`.
                """;
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(apiDraft)))));

        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        ProblemStatementGenerationResponseDTO resp = hyperionProblemStatementGenerationService.generateProblemStatement(course, "You may choose the public API for this exercise");

        assertThat(resp.draftProblemStatement()).isEqualTo(apiDraft.trim());
    }

    @Test
    void generateProblemStatement_allowsPublicApiDetailsWhenInstructorAsksForStructuralDesign() {
        String apiDraft = """
                # Playlist Player

                ## Public API
                Students implement `List<Track> order(Playlist playlist)` behind a strategy interface.
                """;
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(apiDraft)))));

        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        // A design-oriented brief (pattern, UML, students define an interface) is asking the draft to talk about design — API details are on-topic, not invented.
        ProblemStatementGenerationResponseDTO resp = hyperionProblemStatementGenerationService.generateProblemStatement(course,
                "Create an exercise that teaches the strategy design pattern; students define the playback-order interface themselves and the statement should include a UML class diagram");

        assertThat(resp.draftProblemStatement()).isEqualTo(apiDraft.trim());
    }

    @Test
    void generateProblemStatement_flagsUnrequestedStudentTestingRequirementsAsAdvisory() {
        String artifactDraft = """
                # Scheduler

                Students must write unit tests covering the provided test suite scenarios.
                """;
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(artifactDraft)))));

        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        ProblemStatementGenerationResponseDTO resp = hyperionProblemStatementGenerationService.generateProblemStatement(course, "Create a scheduler exercise");
        assertThat(resp.draftProblemStatement()).isEqualTo(artifactDraft.trim());
        assertThat(resp.hygieneWarnings()).isNotEmpty();
    }

    @Test
    void generateProblemStatement_flagsUnrequestedUnitTestPromisesAsAdvisory() {
        String artifactDraft = """
                # Rover

                Implementations that follow these rules will pass a suite of deterministic unit tests.
                """;
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(artifactDraft)))));

        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        ProblemStatementGenerationResponseDTO resp = hyperionProblemStatementGenerationService.generateProblemStatement(course, "Create a rover exercise");
        assertThat(resp.draftProblemStatement()).isEqualTo(artifactDraft.trim());
        assertThat(resp.hygieneWarnings()).isNotEmpty();
    }

    @Test
    void generateProblemStatement_flagsUnrequestedScopeCreepAndContradictoryExamplesAsAdvisory() {
        String artifactDraft = """
                # Scheduler

                - **(Optional) Remove an event**
                - Define a maximum recurrence limit to prevent resource exhaustion.

                | Action | Result |
                | --- | --- |
                | Add event E | Conflict with D because they do not overlap; therefore no conflict. |
                """;
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(artifactDraft)))));

        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        // Both the unrequested optional scope and the contradictory conflict/no-conflict example are semantic
        // heuristics: the draft is returned with (at least) two advisory warnings instead of being blocked.
        ProblemStatementGenerationResponseDTO resp = hyperionProblemStatementGenerationService.generateProblemStatement(course, "Create a scheduler exercise");
        assertThat(resp.draftProblemStatement()).isEqualTo(artifactDraft.trim());
        assertThat(resp.hygieneWarnings()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void generateProblemStatement_flagsUnrequestedDeliverableAndDeliveryMechanismAsAdvisory() {
        String artifactDraft = """
                # Library Checkout

                Students summarize checkout events.

                ## Deliverable Expectations
                Print a JSON-like summary.
                """;
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(artifactDraft)))));

        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        ProblemStatementGenerationResponseDTO resp = hyperionProblemStatementGenerationService.generateProblemStatement(course, "Create a compact library exercise");
        assertThat(resp.draftProblemStatement()).isEqualTo(artifactDraft.trim());
        assertThat(resp.hygieneWarnings()).isNotEmpty();
    }

    @Test
    void generateProblemStatement_allowsJsonLikeNotationUsedOnlyToExplainAnExample() {
        String generatedDraft = """
                # Library Checkout

                Students summarize checkout events.

                ## Example
                The input list is shown as JSON-like records for clarity:

                ```text
                { member: "Ada", returned: true }
                ```
                """;
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(generatedDraft)))));

        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThat(hyperionProblemStatementGenerationService.generateProblemStatement(course, "Create a compact library exercise").draftProblemStatement())
                .isEqualTo(generatedDraft.strip());
    }

    @Test
    void generateProblemStatement_flagsJsonAsAnUnrequestedInputFormatAsAdvisory() {
        String artifactDraft = """
                # Library Checkout

                The input must be provided as JSON.
                """;
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(artifactDraft)))));

        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        ProblemStatementGenerationResponseDTO resp = hyperionProblemStatementGenerationService.generateProblemStatement(course, "Create a compact library exercise");
        assertThat(resp.draftProblemStatement()).isEqualTo(artifactDraft.trim());
        assertThat(resp.hygieneWarnings()).isNotEmpty();
    }

    @Test
    void generateProblemStatement_doesNotTreatANegatedOptionalRequestAsPermissionButOnlyFlagsAdvisory() {
        String artifactDraft = """
                # Library Checkout

                Students summarize checkout events.

                ## Submission checklist (optional)
                - [ ] Handle empty input.
                """;
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(artifactDraft)))));

        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        ProblemStatementGenerationResponseDTO resp = hyperionProblemStatementGenerationService.generateProblemStatement(course, "Do not include an optional submission checklist");
        assertThat(resp.draftProblemStatement()).isEqualTo(artifactDraft.trim());
        assertThat(resp.hygieneWarnings()).isNotEmpty();
    }

    @Test
    void generateProblemStatement_allowsExplicitlyRequestedOptionalJsonOrPerformanceContent() {
        String requestedContentDraft = """
                # Data Export Benchmark

                Students implement a JSON export for measured records.

                ## Optional Challenge
                Compare the performance benchmark for two input sizes.
                """;
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(requestedContentDraft)))));

        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        ProblemStatementGenerationResponseDTO resp = hyperionProblemStatementGenerationService.generateProblemStatement(course,
                "Create an optional challenge with JSON export and a performance benchmark");

        assertThat(resp.draftProblemStatement()).isEqualTo(requestedContentDraft.trim());
    }

    @Test
    void generateProblemStatement_throwsExceptionOnAIFailure() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("AI service unavailable"));

        var course = new Course();
        course.setId(123L);
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementGenerationService.generateProblemStatement(course, "Prompt")).isInstanceOf(InternalServerErrorAlertException.class)
                .hasMessageContaining("Failed to generate problem statement");
    }

    @Test
    void generateProblemStatement_throwsExceptionOnExcessivelyLongResponse() {
        // Generate a string longer than MAX_PROBLEM_STATEMENT_LENGTH (50,000
        // characters)
        String excessivelyLongDraft = "a".repeat(50_001);
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(excessivelyLongDraft)))));

        var course = new Course();
        course.setId(456L);
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementGenerationService.generateProblemStatement(course, "Prompt")).isInstanceOf(InternalServerErrorAlertException.class)
                .hasMessageContaining("exceeds the maximum allowed length");
    }

    @Test
    void generateProblemStatement_throwsExceptionWhenUserPromptIsNull() {
        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        // Should throw exception when userPrompt is null (sanitized to empty string)
        assertThatThrownBy(() -> hyperionProblemStatementGenerationService.generateProblemStatement(course, null)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("User prompt cannot be empty");
    }

    @Test
    void generateProblemStatement_throwsExceptionWhenUserPromptIsBlank() {
        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        // Should throw exception when userPrompt is whitespace-only (sanitized to empty string)
        assertThatThrownBy(() -> hyperionProblemStatementGenerationService.generateProblemStatement(course, "   ")).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("User prompt cannot be empty");
    }

    @Test
    void generateProblemStatement_handlesNullCourseFields() {
        String generatedDraft = "Generated draft with default course info";
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(generatedDraft)))));

        var course = new Course();
        // Leave title and description null

        // Should use default values when course fields are null
        ProblemStatementGenerationResponseDTO resp = hyperionProblemStatementGenerationService.generateProblemStatement(course, "Prompt");
        assertThat(resp).isNotNull();
        assertThat(resp.draftProblemStatement()).isEqualTo(generatedDraft);
    }

    @Test
    void generateProblemStatement_acceptsMaximumLengthResponse() {
        // Generate a string exactly at MAX_PROBLEM_STATEMENT_LENGTH (50,000 characters)
        String maxLengthDraft = "a".repeat(50_000);
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(maxLengthDraft)))));

        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        // Should succeed with exactly 50,000 characters
        ProblemStatementGenerationResponseDTO resp = hyperionProblemStatementGenerationService.generateProblemStatement(course, "Prompt");
        assertThat(resp).isNotNull();
        assertThat(resp.draftProblemStatement()).hasSize(50_000);
    }

    @Test
    void generateProblemStatement_throwsExceptionWhenChatClientIsNull() {
        var serviceWithNullClient = new HyperionProblemStatementGenerationService(null, new HyperionPromptTemplateService(), llmTokenUsageService, userRepository);
        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> serviceWithNullClient.generateProblemStatement(course, "Prompt")).isInstanceOf(InternalServerErrorAlertException.class)
                .hasMessageContaining("AI chat client is not configured");
    }

    @Test
    void generateProblemStatement_throwsExceptionWhenResponseIsNull() {
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(null)))));

        var course = new Course();
        course.setId(999L);
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementGenerationService.generateProblemStatement(course, "Prompt")).isInstanceOf(InternalServerErrorAlertException.class)
                .hasMessageContaining("Generated problem statement is null or empty");
    }

    @Test
    void generateProblemStatement_throwsExceptionWhenResponseIsBlank() {
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage("   ")))));

        var course = new Course();
        course.setId(999L);
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementGenerationService.generateProblemStatement(course, "Prompt")).isInstanceOf(InternalServerErrorAlertException.class)
                .hasMessageContaining("Generated problem statement is null or empty");
    }

    @Test
    void generateProblemStatement_throwsExceptionWhenUserPromptTooLong() {
        // 1001 characters exceeds MAX_USER_PROMPT_LENGTH (1000)
        String tooLongPrompt = "a".repeat(1001);
        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementGenerationService.generateProblemStatement(course, tooLongPrompt)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("exceeds maximum length");
    }
}
