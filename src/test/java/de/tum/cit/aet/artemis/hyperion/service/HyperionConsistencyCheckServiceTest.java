package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.config.LLMModelCostConfiguration;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.test_repository.LLMTokenUsageRequestTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.LLMTokenUsageTraceTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.exercise.domain.review.Comment;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentType;
import de.tum.cit.aet.artemis.exercise.dto.review.ConsistencyIssueCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.UserCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.repository.review.CommentThreadRepository;
import de.tum.cit.aet.artemis.hyperion.domain.ConsistencyIssueCategory;
import de.tum.cit.aet.artemis.hyperion.domain.Severity;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyCheckResponseDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import io.micrometer.observation.ObservationRegistry;

class HyperionConsistencyCheckServiceTest {

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private ChatModel chatModel;

    @Mock
    private LLMTokenUsageTraceTestRepository llmTokenUsageTraceRepository;

    @Mock
    private LLMTokenUsageRequestTestRepository llmTokenUsageRequestRepository;

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private CommentThreadRepository commentThreadRepository;

    private HyperionConsistencyCheckService hyperionConsistencyCheckService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        ChatClient chatClient = ChatClient.create(chatModel);
        var templateService = new HyperionPromptTemplateService();
        // Wire minimal renderer with mocked dependencies
        HyperionProgrammingExerciseContextRendererService exerciseContextRenderer = new HyperionProgrammingExerciseContextRendererService(repositoryService,
                new HyperionProgrammingLanguageContextFilterService());

        // Create configuration with test model costs
        var costConfiguration = createTestConfiguration();
        var llmTokenUsageService = new LLMTokenUsageService(llmTokenUsageTraceRepository, llmTokenUsageRequestRepository, costConfiguration);
        var observationRegistry = ObservationRegistry.create();
        var reviewCommentContextRenderer = new HyperionReviewCommentContextRendererService(commentThreadRepository, new ObjectMapper());
        this.hyperionConsistencyCheckService = new HyperionConsistencyCheckService(programmingExerciseRepository, chatClient, templateService, exerciseContextRenderer,
                reviewCommentContextRenderer, observationRegistry, llmTokenUsageService, userRepository);
    }

    @Test
    void checkConsistency_mapsStructuredIssues() throws Exception {
        final var exercise = getProgrammingExercise();

        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(42L)).thenReturn(exercise);
        when(repositoryService.getFilesContentFromBareRepositoryForLastCommit(any(LocalVCRepositoryUri.class)))
                .thenReturn(Map.of("src/main/java/App.java", "class App { int sum(int a,int b){return a+b;} }"));

        String json = "{\n  \"issues\": [\n    {\n      \"severity\": \"HIGH\",\n      \"category\": \"METHOD_PARAMETER_MISMATCH\",\n      \"description\": \"Parameters differ\",\n      \"suggestedFix\": \"Align parameter names\",\n      \"relatedLocations\": [{\"type\": \"TEMPLATE_REPOSITORY\", \"filePath\": \"src/main/java/App.java\", \"startLine\": 1, \"endLine\": 1}]\n    }\n  ]\n}";

        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> {
            AssistantMessage msg = new AssistantMessage(json);
            return new ChatResponse(List.of(new Generation(msg)));
        });

        ConsistencyCheckResponseDTO resp = hyperionConsistencyCheckService.checkConsistency(exercise);

        assertThat(resp).isNotNull();
        assertThat(resp.issues()).isNotEmpty();
        assertThat(resp.issues().getFirst().category()).isEqualTo(ConsistencyIssueCategory.METHOD_PARAMETER_MISMATCH);
    }

    @Test
    void checkConsistency_tracksTokenUsageAndCosts() throws Exception {
        final var exercise = getProgrammingExercise();

        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(42L)).thenReturn(exercise);
        when(repositoryService.getFilesContentFromBareRepositoryForLastCommit(any(LocalVCRepositoryUri.class)))
                .thenReturn(Map.of("src/main/java/App.java", "class App { int sum(int a,int b){return a+b;} }"));

        String json = "{ \"issues\": [] }";

        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> {
            AssistantMessage msg = new AssistantMessage(json);
            var usage = new DefaultUsage(100, 50, 150);
            var metadata = ChatResponseMetadata.builder().model("gpt-5-mini-2024-07-18").usage(usage).build();
            return new ChatResponse(List.of(new Generation(msg)), metadata);
        });

        ConsistencyCheckResponseDTO resp = hyperionConsistencyCheckService.checkConsistency(exercise);

        assertThat(resp).isNotNull();
        assertThat(resp.timestamp()).isNotNull();
        assertThat(resp.timing()).isNotNull();
        assertThat(resp.timing().durationS()).isGreaterThanOrEqualTo(0);

        // Two parallel checks, each with 100 prompt and 50 completion tokens
        assertThat(resp.tokens()).isNotNull();
        assertThat(resp.tokens().prompt()).isEqualTo(200L);
        assertThat(resp.tokens().completion()).isEqualTo(100L);
        assertThat(resp.tokens().total()).isEqualTo(300L);

        assertThat(resp.costs()).isNotNull();
        // Costs should be calculated based on configured rates (EUR)
        assertThat(resp.costs().totalEur()).isGreaterThan(0);
    }

    @Test
    void checkConsistency_includesExistingConsistencyCheckThreadsInPrompt() throws Exception {
        final var exercise = getProgrammingExercise();
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(42L)).thenReturn(exercise);
        when(repositoryService.getFilesContentFromBareRepositoryForLastCommit(any(LocalVCRepositoryUri.class)))
                .thenReturn(Map.of("src/main/java/App.java", "class App { int sum(int a,int b){return a+b;} }"));

        CommentThread existingThread = new CommentThread();
        existingThread.setId(7L);
        existingThread.setTargetType(CommentThreadLocationType.PROBLEM_STATEMENT);
        existingThread.setInitialLineNumber(4);
        existingThread.setLineNumber(4);
        existingThread.setOutdated(false);
        existingThread.setResolved(false);

        User author = new User();
        author.setFirstName("Ada");
        author.setLastName("Lovelace");

        Comment existingComment = new Comment();
        existingComment.setType(CommentType.CONSISTENCY_CHECK);
        existingComment.setAuthor(author);
        existingComment
                .setContent(new ConsistencyIssueCommentContentDTO(Severity.HIGH, ConsistencyIssueCategory.METHOD_PARAMETER_MISMATCH, "Already discussed naming mismatch", null));
        existingThread.getComments().add(existingComment);

        Comment replyComment = new Comment();
        replyComment.setType(CommentType.USER);
        replyComment.setAuthor(author);
        replyComment.setContent(new UserCommentContentDTO("This user reply should not be part of prompt context"));
        existingThread.getComments().add(replyComment);
        when(commentThreadRepository.findWithCommentsByExerciseId(42L)).thenReturn(Set.of(existingThread));

        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage("{\"issues\":[]}")))));

        hyperionConsistencyCheckService.checkConsistency(exercise);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, atLeastOnce()).call(promptCaptor.capture());

        String promptText = promptCaptor.getAllValues().stream().flatMap(prompt -> prompt.getInstructions().stream())
                .map(content -> Objects.toString(content.getText(), content.toString())).collect(Collectors.joining("\n"));
        assertThat(promptText).contains("Already discussed naming mismatch");
        assertThat(promptText).doesNotContain("This user reply should not be part of prompt context");
        assertThat(promptText).contains("threads");
    }

    private static LLMModelCostConfiguration createTestConfiguration() {
        var config = new LLMModelCostConfiguration();
        var modelCosts = Map.of("gpt-5-mini", createModelCostProperties(0.23f, 1.84f));
        config.setModelCosts(new java.util.HashMap<>(modelCosts));
        return config;
    }

    private static LLMModelCostConfiguration.ModelCostProperties createModelCostProperties(float inputEur, float outputEur) {
        var props = new LLMModelCostConfiguration.ModelCostProperties();
        props.setInputCostPerMillionEur(inputEur);
        props.setOutputCostPerMillionEur(outputEur);
        return props;
    }

    private static @NonNull ProgrammingExercise getProgrammingExercise() {
        var exercise = new ProgrammingExercise();
        exercise.setId(42L);
        exercise.setProblemStatement("Compute sum");
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);

        var template = new TemplateProgrammingExerciseParticipation();
        template.setRepositoryUri("file://template");
        template.setProgrammingExercise(exercise);
        exercise.setTemplateParticipation(template);

        var solution = new SolutionProgrammingExerciseParticipation();
        solution.setRepositoryUri("file://solution");
        solution.setProgrammingExercise(exercise);
        exercise.setSolutionParticipation(solution);
        return exercise;
    }
}
