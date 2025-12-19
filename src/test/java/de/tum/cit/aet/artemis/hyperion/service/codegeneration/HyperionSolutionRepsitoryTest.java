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
import de.tum.cit.aet.artemis.hyperion.service.HyperionPromptTemplateService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

class HyperionSolutionRepositoryServiceTest {

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private ChatModel chatModel;

    @Mock
    private HyperionPromptTemplateService templates;

    private ChatClient chatClient;

    private HyperionSolutionRepositoryService solutionRepository;

    private User user;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        this.chatClient = ChatClient.create(chatModel);
        this.solutionRepository = new HyperionSolutionRepositoryService(programmingExerciseRepository, chatClient, templates);

        this.user = new User();
        user.setLogin("testuser");

        this.exercise = new ProgrammingExercise();
        exercise.setId(1L);
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setProblemStatement("Create a sorting algorithm");
    }

    @Test
    void generateSolutionPlan_withValidInput_returnsCodeGenerationResponse() throws Exception {
        String expectedPlan = "1. Create sorting class 2. Implement quicksort 3. Add validation";
        String renderedPrompt = "Rendered prompt for solution plan";
        String jsonResponse = "{\"solutionPlan\":\"" + expectedPlan + "\",\"files\":[]}";

        when(templates.renderObject(eq("/prompts/hyperion/solution/1_plan.st"), any(Map.class))).thenReturn(renderedPrompt);
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(jsonResponse));

        CodeGenerationResponseDTO result = solutionRepository.generateSolutionPlan(user, exercise, "build logs", "repo structure");

        assertThat(result).isNotNull();
        assertThat(result.getSolutionPlan()).isEqualTo(expectedPlan);
        verify(templates).renderObject(eq("/prompts/hyperion/solution/1_plan.st"), any(Map.class));
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void defineFileStructure_withValidInput_returnsFileStructure() throws Exception {
        List<GeneratedFileDTO> expectedFiles = List.of(new GeneratedFileDTO("Sort.java", "class Sort {}"));
        String jsonResponse = "{\"solutionPlan\":\"plan\",\"files\":[{\"path\":\"Sort.java\",\"content\":\"class Sort {}\"}]}";

        when(templates.renderObject(eq("/prompts/hyperion/solution/2_file_structure.st"), any(Map.class))).thenReturn("rendered");
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(jsonResponse));

        CodeGenerationResponseDTO result = solutionRepository.defineFileStructure(user, exercise, "solution plan", "repo structure");

        assertThat(result).isNotNull();
        assertThat(result.getFiles()).hasSize(1);
        assertThat(result.getFiles().get(0).path()).isEqualTo("Sort.java");
        assertThat(result.getFiles().get(0).content()).isEqualTo("class Sort {}");
    }

    @Test
    void generateClassAndMethodHeaders_callsDefineFileStructureAndUsesResult() throws Exception {
        String fileStructureJson = "{\"solutionPlan\":\"plan\",\"files\":[{\"path\":\"Sort.java\",\"content\":\"stub\"}]}";
        String headersJson = "{\"solutionPlan\":\"plan\",\"files\":[{\"path\":\"Sort.java\",\"content\":\"class Sort { void sort(); }\"}]}";

        when(templates.renderObject(eq("/prompts/hyperion/solution/2_file_structure.st"), any(Map.class))).thenReturn("rendered");
        when(templates.renderObject(eq("/prompts/hyperion/solution/3_headers.st"), any(Map.class))).thenReturn("rendered");
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(fileStructureJson)).thenReturn(createChatResponse(headersJson));

        CodeGenerationResponseDTO result = solutionRepository.generateClassAndMethodHeaders(user, exercise, "solution plan", "repo structure");

        assertThat(result).isNotNull();
        assertThat(result.getFiles().get(0).content()).contains("void sort()");
        verify(chatModel, org.mockito.Mockito.times(2)).call(any(Prompt.class));
    }

    @Test
    void generateCoreLogic_callsHeadersAndUsesResult() throws Exception {
        String fileStructureJson = "{\"solutionPlan\":\"plan\",\"files\":[{\"path\":\"Sort.java\",\"content\":\"stub\"}]}";
        String headersJson = "{\"solutionPlan\":\"plan\",\"files\":[{\"path\":\"Sort.java\",\"content\":\"class Sort { void sort(); }\"}]}";
        String coreLogicJson = "{\"solutionPlan\":\"plan\",\"files\":[{\"path\":\"Sort.java\",\"content\":\"class Sort { void sort() { /* implementation */ } }\"}]}";

        when(templates.renderObject(any(String.class), any(Map.class))).thenReturn("rendered");
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(fileStructureJson)).thenReturn(createChatResponse(headersJson))
                .thenReturn(createChatResponse(coreLogicJson));

        CodeGenerationResponseDTO result = solutionRepository.generateCoreLogic(user, exercise, "solution plan", "repo structure");

        assertThat(result).isNotNull();
        assertThat(result.getFiles().get(0).content()).contains("implementation");
        verify(chatModel, org.mockito.Mockito.times(3)).call(any(Prompt.class));
    }

    @Test
    void getRepositoryType_returnsSolution() {
        RepositoryType result = solutionRepository.getRepositoryType();

        assertThat(result).isEqualTo(RepositoryType.SOLUTION);
    }

    @Test
    void generateSolutionPlan_withNonTransientAiException_throwsNetworkingException() {
        when(templates.renderObject(any(String.class), any(Map.class))).thenReturn("rendered");
        when(chatModel.call(any(Prompt.class))).thenThrow(new NonTransientAiException("AI service error"));

        assertThatThrownBy(() -> solutionRepository.generateSolutionPlan(user, exercise, "logs", "structure")).isInstanceOf(NetworkingException.class)
                .hasMessageContaining("AI request failed");
    }

    private ChatResponse createChatResponse(String content) {
        AssistantMessage message = new AssistantMessage(content);
        Generation generation = new Generation(message);
        return new ChatResponse(List.of(generation));
    }
}
