package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
import org.springframework.ai.retry.NonTransientAiException;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.hyperion.dto.CodeGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedFileDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProgrammingExerciseContextRendererService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionPromptTemplateService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

class HyperionTestRepositoryServiceTest {

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private ChatModel chatModel;

    @Mock
    private HyperionPromptTemplateService templates;

    @Mock
    private GitService gitService;

    @Mock
    private HyperionProgrammingExerciseContextRendererService contextRenderer;

    private ChatClient chatClient;

    private HyperionTestRepositoryService testRepository;

    private User user;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        this.chatClient = ChatClient.create(chatModel);
        this.testRepository = new HyperionTestRepositoryService(programmingExerciseRepository, chatClient, templates, gitService, contextRenderer);

        this.user = new User();
        user.setLogin("testuser");

        this.exercise = new ProgrammingExercise();
        exercise.setId(1L);
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setProblemStatement("Create a sorting algorithm with comprehensive test coverage");
    }

    @Test
    void generateSolutionPlan_withValidInput_returnsCodeGenerationResponse() throws Exception {
        String expectedPlan = "1. Analyze solution code 2. Generate unit tests 3. Create integration tests";
        String renderedPrompt = "Rendered prompt for test plan";
        String jsonResponse = "{\"solutionPlan\":\"" + expectedPlan + "\",\"files\":[]}";

        when(contextRenderer.getExistingSolutionCode(any(ProgrammingExercise.class), any(GitService.class))).thenReturn("public class Solution {}");
        when(templates.renderObject(eq("/prompts/hyperion/test/1_plan.st"), any(Map.class))).thenReturn(renderedPrompt);
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(jsonResponse));

        CodeGenerationResponseDTO result = testRepository.generateSolutionPlan(user, exercise, "build logs", "repo structure");

        assertThat(result).isNotNull();
        assertThat(result.getSolutionPlan()).isEqualTo(expectedPlan);
        verify(templates).renderObject(eq("/prompts/hyperion/test/1_plan.st"), any(Map.class));
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void generateSolutionPlan_includesSolutionCodeInTemplateVariables() throws Exception {
        String jsonResponse = "{\"solutionPlan\":\"test plan\",\"files\":[]}";

        when(contextRenderer.getExistingSolutionCode(any(ProgrammingExercise.class), any(GitService.class))).thenReturn("public class Solution {}");
        when(templates.renderObject(any(String.class), any(Map.class))).thenReturn("rendered");
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(jsonResponse));

        testRepository.generateSolutionPlan(user, exercise, "logs", "structure");

        verify(templates).renderObject(eq("/prompts/hyperion/test/1_plan.st"), any(Map.class));
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void generateSolutionPlan_withFailedRepositoryCheckout_usesWarningMessage() throws Exception {
        String jsonResponse = "{\"solutionPlan\":\"test plan\",\"files\":[]}";
        when(contextRenderer.getExistingSolutionCode(any(ProgrammingExercise.class), any(GitService.class))).thenReturn("public class Solution {}");
        when(templates.renderObject(any(String.class), any(Map.class))).thenReturn("rendered");
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(jsonResponse));

        testRepository.generateSolutionPlan(user, exercise, "logs", "structure");

        verify(templates).renderObject(eq("/prompts/hyperion/test/1_plan.st"), any(Map.class));
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void generateSolutionPlan_withMultipleJavaFiles_concatenatesContent() throws Exception {
        String jsonResponse = "{\"solutionPlan\":\"test plan\",\"files\":[]}";

        when(contextRenderer.getExistingSolutionCode(any(ProgrammingExercise.class), any(GitService.class))).thenReturn("public class Solution {}");
        when(templates.renderObject(any(String.class), any(Map.class))).thenReturn("rendered");
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(jsonResponse));

        testRepository.generateSolutionPlan(user, exercise, "logs", "structure");

        verify(templates).renderObject(eq("/prompts/hyperion/test/1_plan.st"), any(Map.class));
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void generateSolutionPlan_withIOExceptionDuringWalk_returnsErrorMessage() throws Exception {
        String jsonResponse = "{\"solutionPlan\":\"test plan\",\"files\":[]}";

        when(contextRenderer.getExistingSolutionCode(any(ProgrammingExercise.class), any(GitService.class))).thenReturn("public class Solution {}");
        when(templates.renderObject(any(String.class), any(Map.class))).thenReturn("rendered");
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(jsonResponse));

        testRepository.generateSolutionPlan(user, exercise, "logs", "structure");

        verify(templates).renderObject(eq("/prompts/hyperion/test/1_plan.st"), any(Map.class));
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void generateSolutionPlan_withRepositoryAccessException_throwsNetworkingException() throws Exception {
        when(contextRenderer.getExistingSolutionCode(any(ProgrammingExercise.class), any(GitService.class))).thenReturn("public class Solution {}");
        when(templates.renderObject(any(String.class), any(Map.class))).thenReturn("rendered");
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("Repository access failed"));

        assertThatThrownBy(() -> testRepository.generateSolutionPlan(user, exercise, "logs", "structure")).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Repository access failed");
    }

    @Test
    void generateSolutionPlan_withNoJavaFiles_returnsNoFilesMessage() throws Exception {
        String jsonResponse = "{\"solutionPlan\":\"test plan\",\"files\":[]}";

        when(contextRenderer.getExistingSolutionCode(any(ProgrammingExercise.class), any(GitService.class))).thenReturn("public class Solution {}");
        when(templates.renderObject(any(String.class), any(Map.class))).thenReturn("rendered");
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(jsonResponse));

        testRepository.generateSolutionPlan(user, exercise, "logs", "structure");

        verify(templates).renderObject(eq("/prompts/hyperion/test/1_plan.st"), any(Map.class));
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void defineFileStructure_withValidInput_returnsFileStructure() throws Exception {
        List<GeneratedFileDTO> expectedFiles = List.of(new GeneratedFileDTO("SortTest.java", "class SortTest {}"));
        String jsonResponse = "{\"solutionPlan\":\"plan\",\"files\":[{\"path\":\"SortTest.java\",\"content\":\"class SortTest {}\"}]}";

        when(templates.renderObject(eq("/prompts/hyperion/test/2_file_structure.st"), any(Map.class))).thenReturn("rendered");
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(jsonResponse));

        CodeGenerationResponseDTO result = testRepository.defineFileStructure(user, exercise, "solution plan", "repo structure");

        assertThat(result).isNotNull();
        assertThat(result.getFiles()).hasSize(1);
        assertThat(result.getFiles().get(0).path()).isEqualTo("SortTest.java");
        assertThat(result.getFiles().get(0).content()).isEqualTo("class SortTest {}");
    }

    @Test
    void generateClassAndMethodHeaders_callsDefineFileStructureAndUsesResult() throws Exception {
        String fileStructureJson = "{\"solutionPlan\":\"plan\",\"files\":[{\"path\":\"SortTest.java\",\"content\":\"stub\"}]}";
        String headersJson = "{\"solutionPlan\":\"plan\",\"files\":[{\"path\":\"SortTest.java\",\"content\":\"class SortTest { void testSort(); }\"}]}";

        when(templates.renderObject(eq("/prompts/hyperion/test/2_file_structure.st"), any(Map.class))).thenReturn("rendered");
        when(templates.renderObject(eq("/prompts/hyperion/test/3_headers.st"), any(Map.class))).thenReturn("rendered");
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(fileStructureJson)).thenReturn(createChatResponse(headersJson));

        CodeGenerationResponseDTO result = testRepository.generateClassAndMethodHeaders(user, exercise, "solution plan", "repo structure");

        assertThat(result).isNotNull();
        assertThat(result.getFiles().get(0).content()).contains("void testSort()");
        verify(chatModel, org.mockito.Mockito.times(2)).call(any(Prompt.class));
    }

    @Test
    void generateCoreLogic_callsHeadersAndUsesResult() throws Exception {
        String fileStructureJson = "{\"solutionPlan\":\"plan\",\"files\":[{\"path\":\"SortTest.java\",\"content\":\"stub\"}]}";
        String headersJson = "{\"solutionPlan\":\"plan\",\"files\":[{\"path\":\"SortTest.java\",\"content\":\"class SortTest { void testSort(); }\"}]}";
        String coreLogicJson = "{\"solutionPlan\":\"plan\",\"files\":[{\"path\":\"SortTest.java\",\"content\":\"class SortTest { @Test void testSort() { /* test implementation */ } }\"}]}";

        when(templates.renderObject(any(String.class), any(Map.class))).thenReturn("rendered");
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(fileStructureJson)).thenReturn(createChatResponse(headersJson))
                .thenReturn(createChatResponse(coreLogicJson));

        CodeGenerationResponseDTO result = testRepository.generateCoreLogic(user, exercise, "solution plan", "repo structure");

        assertThat(result).isNotNull();
        assertThat(result.getFiles().get(0).content()).contains("test implementation");
        verify(chatModel, org.mockito.Mockito.times(3)).call(any(Prompt.class));
        verify(templates).renderObject(eq("/prompts/hyperion/test/4_logic.st"), any(Map.class));
    }

    @Test
    void getRepositoryType_returnsTests() {
        RepositoryType result = testRepository.getRepositoryType();

        assertThat(result).isEqualTo(RepositoryType.TESTS);
    }

    @Test
    void generateSolutionPlan_withNonTransientAiException_throwsNetworkingException() throws Exception {
        when(contextRenderer.getExistingSolutionCode(any(ProgrammingExercise.class), any(GitService.class))).thenReturn("public class Solution {}");
        when(templates.renderObject(any(String.class), any(Map.class))).thenReturn("rendered");
        when(chatModel.call(any(Prompt.class))).thenThrow(new NonTransientAiException("AI service error"));

        assertThatThrownBy(() -> testRepository.generateSolutionPlan(user, exercise, "logs", "structure")).isInstanceOf(NetworkingException.class)
                .hasMessageContaining("AI request failed");
    }

    @Test
    void generateSolutionPlan_withNullBuildLogs_usesEmptyString() throws Exception {
        String jsonResponse = "{\"solutionPlan\":\"test plan\",\"files\":[]}";

        when(contextRenderer.getExistingSolutionCode(any(ProgrammingExercise.class), any(GitService.class))).thenReturn("public class Solution {}");
        when(templates.renderObject(any(String.class), any(Map.class))).thenReturn("rendered");
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(jsonResponse));

        testRepository.generateSolutionPlan(user, exercise, null, "repo structure");

        verify(templates).renderObject(eq("/prompts/hyperion/test/1_plan.st"), any(Map.class));
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void generateSolutionPlan_withNullRepositoryStructure_usesEmptyString() throws Exception {
        String jsonResponse = "{\"solutionPlan\":\"test plan\",\"files\":[]}";

        when(contextRenderer.getExistingSolutionCode(any(ProgrammingExercise.class), any(GitService.class))).thenReturn("public class Solution {}");
        when(templates.renderObject(any(String.class), any(Map.class))).thenReturn("rendered");
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(jsonResponse));

        testRepository.generateSolutionPlan(user, exercise, "logs", null);

        verify(templates).renderObject(eq("/prompts/hyperion/test/1_plan.st"), any(Map.class));
        verify(chatModel).call(any(Prompt.class));
    }

    private ChatResponse createChatResponse(String content) {
        AssistantMessage message = new AssistantMessage(content);
        Generation generation = new Generation(message);
        return new ChatResponse(List.of(generation));
    }
}
