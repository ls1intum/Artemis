package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

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
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyCheckResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteResponseDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

class HyperionReviewAndRefineServiceTest {

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Mock
    private ChatModel chatModel;

    private ChatClient chatClient;

    private HyperionReviewAndRefineService service;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        this.chatClient = ChatClient.create(chatModel);
        var templateService = new PromptTemplateService();
        var checkService = new ConsistencyCheckService(repositoryService, programmingExerciseRepository, chatClient, templateService);
        var rewriteService = new ProblemStatementRewriteService(chatClient, templateService);
        this.service = new HyperionReviewAndRefineService(checkService, rewriteService);
    }

    @Test
    void checkConsistency_mapsStructuredIssues() throws Exception {
        // Arrange exercise with minimal repos
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

        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(42L)).thenReturn(exercise);
        when(repositoryService.getFilesContentFromBareRepositoryForLastCommit(any(VcsRepositoryUri.class)))
                .thenReturn(Map.of("src/main/java/App.java", "class App { int sum(int a,int b){return a+b;} }"));

        // Mock model to return one structural or semantic issue in both calls
        String json = "{\n  \"issues\": [\n    {\n      \"severity\": \"HIGH\",\n      \"category\": \"METHOD_PARAMETER_MISMATCH\",\n      \"description\": \"Parameters differ\",\n      \"suggestedFix\": \"Align parameter names\",\n      \"relatedLocations\": [{\"type\": \"TEMPLATE_REPOSITORY\", \"filePath\": \"src/main/java/App.java\", \"startLine\": 1, \"endLine\": 1}]\n    }\n  ]\n}";

        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            AssistantMessage msg = new AssistantMessage(json);
            return new ChatResponse(List.of(new Generation(msg)));
        });

        // Act
        var user = new User();
        user.setLogin("instructor");
        ConsistencyCheckResponseDTO resp = service.checkConsistency(user, exercise);

        // Assert
        assertThat(resp).isNotNull();
        assertThat(resp.issues()).isNotEmpty();
        assertThat(resp.issues().get(0).category()).isEqualTo("METHOD_PARAMETER_MISMATCH");
    }

    @Test
    void rewriteProblemStatement_returnsText() throws Exception {
        String rewritten = "Rewritten statement";
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(rewritten)))));

        var user = new User();
        var course = new Course();

        ProblemStatementRewriteResponseDTO resp = service.rewriteProblemStatement(user, course, "Original");
        assertThat(resp).isNotNull();
        assertThat(resp.improved()).isTrue();
        assertThat(resp.rewrittenText()).isEqualTo(rewritten);
    }
}
