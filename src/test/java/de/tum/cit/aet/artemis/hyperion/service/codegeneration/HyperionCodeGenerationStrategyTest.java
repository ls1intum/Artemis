package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
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
import org.springframework.ai.retry.TransientAiException;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.hyperion.dto.CodeGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedFileDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionPromptTemplateService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

class HyperionCodeGenerationServiceTest {

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private ChatModel chatModel;

    @Mock
    private HyperionPromptTemplateService templates;

    private TestCodeGenerationStrategy strategy;

    private User user;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        ChatClient chatClient = ChatClient.create(chatModel);
        this.strategy = new TestCodeGenerationStrategy(programmingExerciseRepository, chatClient, templates);

        this.user = new User();
        user.setLogin("testuser");

        this.exercise = new ProgrammingExercise();
        exercise.setId(1L);
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setProblemStatement("Implement sorting algorithm");
    }

    @Test
    void generateCode_withValidInput_orchestratesAllStepsAndReturnsFiles() throws Exception {
        String coreLogicJson = "{\"solutionPlan\":\"plan\",\"files\":[{\"path\":\"Sort.java\",\"content\":\"class Sort { void sort() { /* implementation */ } }\"},{\"path\":\"SortTest.java\",\"content\":\"@Test void testSort() { /* test */ }\"}]}";

        setupMockTemplateAndChatResponses(coreLogicJson);

        List<GeneratedFileDTO> result = strategy.generateCode(user, exercise, "build logs", "repo structure", "consistency issues");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).path()).isEqualTo("Sort.java");
        assertThat(result.get(1).path()).isEqualTo("SortTest.java");

        // Verify all 4 steps were called in correct order
        verify(chatModel, times(4)).call(any(Prompt.class));
        verify(templates).renderObject(eq("test-plan-template"), anyMap());
        verify(templates).renderObject(eq("test-structure-template"), anyMap());
        verify(templates).renderObject(eq("test-headers-template"), anyMap());
        verify(templates).renderObject(eq("test-logic-template"), anyMap());
    }

    @Test
    void generateCode_withNullPreviousLogs_handlesGracefully() throws Exception {
        String coreLogicJson = "{\"solutionPlan\":\"plan\",\"files\":[{\"path\":\"Test.java\",\"content\":\"class Test {}\"}]}";
        setupMockTemplateAndChatResponses(coreLogicJson);

        List<GeneratedFileDTO> result = strategy.generateCode(user, exercise, null, "repo structure", "consistency issues");

        assertThat(result).hasSize(1);
        verify(chatModel, times(4)).call(any(Prompt.class));
    }

    @Test
    void generateCode_withNullRepositoryStructure_handlesGracefully() throws Exception {
        String coreLogicJson = "{\"solutionPlan\":\"plan\",\"files\":[{\"path\":\"Test.java\",\"content\":\"class Test {}\"}]}";
        setupMockTemplateAndChatResponses(coreLogicJson);

        List<GeneratedFileDTO> result = strategy.generateCode(user, exercise, "logs", null, "consistency issues");

        assertThat(result).hasSize(1);
        verify(chatModel, times(4)).call(any(Prompt.class));
    }

    @Test
    void callChatClient_withValidInput_returnsResponse() throws Exception {
        String expectedPlan = "Generated solution plan";
        String jsonResponse = "{\"solutionPlan\":\"" + expectedPlan + "\",\"files\":[]}";
        Map<String, Object> templateVariables = Map.of("key", "value");

        when(templates.renderObject("test-template", templateVariables)).thenReturn("rendered prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(jsonResponse));

        CodeGenerationResponseDTO result = strategy.testCallChatClient("test-template", templateVariables);

        assertThat(result).isNotNull();
        assertThat(result.getSolutionPlan()).isEqualTo(expectedPlan);
        verify(templates).renderObject("test-template", templateVariables);
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void callChatClient_withTransientAiException_throwsNetworkingException() {
        Map<String, Object> templateVariables = Map.of("key", "value");
        when(templates.renderObject("test-template", templateVariables)).thenReturn("rendered prompt");
        when(chatModel.call(any(Prompt.class))).thenThrow(new TransientAiException("Temporary AI service issue"));

        assertThatThrownBy(() -> strategy.testCallChatClient("test-template", templateVariables)).isInstanceOf(NetworkingException.class)
                .hasMessageContaining("Temporary AI service issue. Please retry.");
    }

    @Test
    void callChatClient_withNonTransientAiException_throwsNetworkingException() {
        Map<String, Object> templateVariables = Map.of("key", "value");
        when(templates.renderObject("test-template", templateVariables)).thenReturn("rendered prompt");
        when(chatModel.call(any(Prompt.class))).thenThrow(new NonTransientAiException("AI configuration error"));

        assertThatThrownBy(() -> strategy.testCallChatClient("test-template", templateVariables)).isInstanceOf(NetworkingException.class)
                .hasMessageContaining("AI request failed due to configuration or input. Check model and request.");
    }

    private void setupMockTemplateAndChatResponses(String finalResponse) {
        String planResponse = "{\"solutionPlan\":\"Generated plan\",\"files\":[]}";
        String structureResponse = "{\"solutionPlan\":\"plan\",\"files\":[{\"path\":\"stub\",\"content\":\"stub\"}]}";
        String headersResponse = "{\"solutionPlan\":\"plan\",\"files\":[{\"path\":\"headers\",\"content\":\"headers\"}]}";

        when(templates.renderObject(eq("test-plan-template"), anyMap())).thenReturn("rendered plan");
        when(templates.renderObject(eq("test-structure-template"), anyMap())).thenReturn("rendered structure");
        when(templates.renderObject(eq("test-headers-template"), anyMap())).thenReturn("rendered headers");
        when(templates.renderObject(eq("test-logic-template"), anyMap())).thenReturn("rendered logic");

        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(planResponse)).thenReturn(createChatResponse(structureResponse))
                .thenReturn(createChatResponse(headersResponse)).thenReturn(createChatResponse(finalResponse));
    }

    private ChatResponse createChatResponse(String content) {
        AssistantMessage message = new AssistantMessage(content);
        Generation generation = new Generation(message);
        return new ChatResponse(List.of(generation));
    }

    private static class TestCodeGenerationStrategy extends HyperionCodeGenerationService {

        public TestCodeGenerationStrategy(ProgrammingExerciseTestRepository programmingExerciseRepository, ChatClient chatClient, HyperionPromptTemplateService templates) {
            super(programmingExerciseRepository, chatClient, templates);
        }

        @Override
        protected CodeGenerationResponseDTO generateSolutionPlan(User user, ProgrammingExercise exercise, String previousBuildLogs, String repositoryStructure,
                String consistencyIssues) throws NetworkingException {
            Map<String, Object> variables = Map.of("test", "plan");
            return callChatClient("test-plan-template", variables);
        }

        @Override
        protected CodeGenerationResponseDTO defineFileStructure(User user, ProgrammingExercise exercise, String solutionPlan, String repositoryStructure, String consistencyIssues)
                throws NetworkingException {
            Map<String, Object> variables = Map.of("test", "structure");
            return callChatClient("test-structure-template", variables);
        }

        @Override
        protected CodeGenerationResponseDTO generateClassAndMethodHeaders(User user, ProgrammingExercise exercise, String solutionPlan, String repositoryStructure,
                String consistencyIssues) throws NetworkingException {
            Map<String, Object> variables = Map.of("test", "headers");
            return callChatClient("test-headers-template", variables);
        }

        @Override
        protected CodeGenerationResponseDTO generateCoreLogic(User user, ProgrammingExercise exercise, String solutionPlan, String repositoryStructure, String consistencyIssues)
                throws NetworkingException {
            Map<String, Object> variables = Map.of("test", "logic");
            return callChatClient("test-logic-template", variables);
        }

        @Override
        protected RepositoryType getRepositoryType() {
            return RepositoryType.SOLUTION;
        }

        // Expose protected method for testing
        public CodeGenerationResponseDTO testCallChatClient(String prompt, Map<String, Object> templateVariables) throws NetworkingException {
            return callChatClient(prompt, templateVariables);
        }
    }
}
