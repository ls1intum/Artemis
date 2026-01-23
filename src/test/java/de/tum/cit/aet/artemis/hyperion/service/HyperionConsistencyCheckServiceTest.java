package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import de.tum.cit.aet.artemis.core.config.LlmModelCostConfiguration;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.util.LlmUsageHelper;
import de.tum.cit.aet.artemis.hyperion.domain.ConsistencyIssueCategory;
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
    private LLMTokenUsageService llmTokenUsageService;

    @Mock
    private UserTestRepository userRepository;

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
        var llmUsageHelper = new LlmUsageHelper(llmTokenUsageService, userRepository, costConfiguration);
        var observationRegistry = ObservationRegistry.create();
        this.hyperionConsistencyCheckService = new HyperionConsistencyCheckService(programmingExerciseRepository, chatClient, templateService, exerciseContextRenderer,
                observationRegistry, llmUsageHelper);
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
    void normalizeModelName_removesDateSuffix() {
        var costConfiguration = createTestConfiguration();
        var llmUsageHelper = new LlmUsageHelper(llmTokenUsageService, userRepository, costConfiguration);

        assertThat(llmUsageHelper.normalizeModelName("gpt-5-mini-2024-07-18")).isEqualTo("gpt-5-mini");
        assertThat(llmUsageHelper.normalizeModelName("gpt-5-mini-2025-01-15")).isEqualTo("gpt-5-mini");
        assertThat(llmUsageHelper.normalizeModelName("gpt-5-mini")).isEqualTo("gpt-5-mini");
        assertThat(llmUsageHelper.normalizeModelName("claude-3-opus")).isEqualTo("claude-3-opus");
        assertThat(llmUsageHelper.normalizeModelName(null)).isEqualTo("");
    }

    private static LlmModelCostConfiguration createTestConfiguration() {
        var config = new LlmModelCostConfiguration();
        var modelCosts = Map.of("gpt-5-mini", createModelCostProperties(0.23f, 1.84f));
        config.setModelCosts(new java.util.HashMap<>(modelCosts));
        return config;
    }

    private static LlmModelCostConfiguration.ModelCostProperties createModelCostProperties(float inputEur, float outputEur) {
        var props = new LlmModelCostConfiguration.ModelCostProperties();
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
