package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ChatModelCallAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.LLMRequest;
import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.hyperion.dto.CodeGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedFileDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionPromptTemplateService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

class HyperionCodeGenerationServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private ChatModel chatModel;

    @Mock
    private HyperionPromptTemplateService templates;

    @Mock
    private LLMTokenUsageService llmTokenUsageService;

    private TestCodeGenerationStrategy strategy;

    private User user;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        ChatClient chatClient = ChatClient.builder(chatModel).defaultAdvisors(ChatModelCallAdvisor.builder().chatModel(chatModel).build()).build();
        this.strategy = new TestCodeGenerationStrategy(chatClient, templates, llmTokenUsageService);

        this.user = new User();
        user.setLogin("testuser");

        this.exercise = new ProgrammingExercise();
        exercise.setId(1L);
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setProblemStatement("Implement sorting algorithm");
    }

    @Test
    void generateCode_withValidInput_returnsFilesWithoutDuplicateStageCalls() throws Exception {
        String coreLogicJson = "{\"solutionPlan\":\"plan\",\"files\":[{\"path\":\"Sort.java\",\"content\":\"class Sort { void sort() { /* implementation */ } }\"},{\"path\":\"SortTest.java\",\"content\":\"@Test void testSort() { /* test */ }\"}]}";

        setupMockTemplateAndChatResponses(coreLogicJson);

        List<GeneratedFileDTO> result = strategy.generateCode(user, exercise, 1L, "build logs", "repo structure", "consistency issues", "{\"threads\":[]}");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).path()).isEqualTo("Sort.java");
        assertThat(result.get(1).path()).isEqualTo("SortTest.java");

        // Base orchestration triggers planning + final logic stage once for this test strategy.
        verify(chatModel, times(2)).call(any(Prompt.class));
        verify(templates).renderObject(eq("test-plan-template"), anyMap());
        verify(templates).renderObject(eq("test-logic-template"), anyMap());
    }

    @Test
    void generateCode_withNullPreviousLogs_handlesGracefully() throws Exception {
        String coreLogicJson = "{\"solutionPlan\":\"plan\",\"files\":[{\"path\":\"Test.java\",\"content\":\"class Test {}\"}]}";
        setupMockTemplateAndChatResponses(coreLogicJson);

        List<GeneratedFileDTO> result = strategy.generateCode(user, exercise, 1L, null, "repo structure", "consistency issues", "{\"threads\":[]}");

        assertThat(result).hasSize(1);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void generateCode_withNullRepositoryStructure_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> strategy.generateCode(user, exercise, 1L, "logs", null, "consistency issues", "{\"threads\":[]}")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repositoryStructure must not be null");
    }

    @Test
    void generateCode_withNullConsistencyIssues_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> strategy.generateCode(user, exercise, 1L, "logs", "repo structure", null, "{\"threads\":[]}")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consistencyIssues must not be null");
    }

    @Test
    void normalizeSelectedFeedbackThreads_withOversizedValidJson_trimsThreadsAndReturnsValidJson() throws Exception {
        int maxLength = getMaxSelectedFeedbackThreadsLength();
        String oversizedPayload = createOversizedThreadsPayload(80, 300);

        String normalized = ReflectionTestUtils.invokeMethod(strategy, "normalizeSelectedFeedbackThreads", oversizedPayload);

        assertThat(normalized).isNotNull();
        assertThat(normalized.length()).isLessThanOrEqualTo(maxLength);

        JsonNode normalizedJson = OBJECT_MAPPER.readTree(normalized);
        assertThat(normalizedJson.path("repositoryType").asText()).isEqualTo("SOLUTION");
        assertThat(normalizedJson.path("threads").isArray()).isTrue();
        assertThat(normalizedJson.path("threads").size()).isLessThan(80);
        assertThat(normalizedJson.path("threads")).isNotEmpty();
    }

    @Test
    void normalizeSelectedFeedbackThreads_withOversizedInvalidJson_returnsLastValidObject() throws Exception {
        int maxLength = getMaxSelectedFeedbackThreadsLength();
        String validPayload = createOversizedThreadsPayload(2, 120);
        assertThat(validPayload.length()).isLessThan(maxLength);

        String oversizedInvalidPayload = validPayload + " {\"threads\":[" + "x".repeat(maxLength);

        String normalized = ReflectionTestUtils.invokeMethod(strategy, "normalizeSelectedFeedbackThreads", oversizedInvalidPayload);

        assertThat(normalized).isEqualTo(OBJECT_MAPPER.writeValueAsString(OBJECT_MAPPER.readTree(validPayload)));
        assertThat(OBJECT_MAPPER.readTree(normalized).path("threads").size()).isEqualTo(2);
    }

    @Test
    void callChatClient_withValidInput_returnsResponse() throws Exception {
        String expectedPlan = "Generated solution plan";
        String jsonResponse = "{\"solutionPlan\":\"" + expectedPlan + "\",\"files\":[]}";
        Map<String, Object> templateVariables = Map.of("key", "value");

        when(templates.renderObject("test-template", templateVariables)).thenReturn("rendered prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(jsonResponse));

        CodeGenerationResponseDTO result = strategy.testCallChatClient(user, exercise, "test-template", templateVariables);

        assertThat(result).isNotNull();
        assertThat(result.getSolutionPlan()).isEqualTo(expectedPlan);
        verify(templates).renderObject("test-template", templateVariables);
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void callChatClient_withUsageMetadata_tracksTokenUsage() throws Exception {
        String expectedPlan = "Generated solution plan";
        String jsonResponse = "{\"solutionPlan\":\"" + expectedPlan + "\",\"files\":[]}";
        Map<String, Object> templateVariables = Map.of("key", "value");
        String modelName = "gpt-5-mini-2025-08-07";
        String pipelineId = "HYPERION_CODE_GENERATION_SOLUTION_TEST_TEMPLATE";
        LLMRequest llmRequest = new LLMRequest(modelName, 11, 0.23f, 7, 1.84f, pipelineId);

        when(templates.renderObject("test-template", templateVariables)).thenReturn("rendered prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(jsonResponse, modelName, 11, 7));
        when(llmTokenUsageService.buildLLMRequest(modelName, 11, 7, pipelineId)).thenReturn(llmRequest);

        CodeGenerationResponseDTO result = strategy.testCallChatClient(user, exercise, "test-template", templateVariables);

        assertThat(result.getSolutionPlan()).isEqualTo(expectedPlan);
        verify(llmTokenUsageService).buildLLMRequest(modelName, 11, 7, pipelineId);
        verify(llmTokenUsageService).saveLLMTokenUsage(eq(List.of(llmRequest)), eq(LLMServiceType.HYPERION), any());
    }

    @Test
    void callChatClient_withUsageMetadataAndMissingCourseId_doesNotTrackTokenUsage() throws Exception {
        String expectedPlan = "Generated solution plan";
        String jsonResponse = "{\"solutionPlan\":\"" + expectedPlan + "\",\"files\":[]}";
        Map<String, Object> templateVariables = Map.of("key", "value");
        String modelName = "gpt-5-mini-2025-08-07";

        when(templates.renderObject("test-template", templateVariables)).thenReturn("rendered prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(jsonResponse, modelName, 11, 7));

        CodeGenerationResponseDTO result = strategy.testCallChatClient(user, exercise, null, "test-template", templateVariables);

        assertThat(result.getSolutionPlan()).isEqualTo(expectedPlan);
        verifyNoInteractions(llmTokenUsageService);
    }

    @Test
    void callChatClient_withoutUsageMetadata_doesNotTrackTokenUsage() throws Exception {
        String expectedPlan = "Generated solution plan";
        String jsonResponse = "{\"solutionPlan\":\"" + expectedPlan + "\",\"files\":[]}";
        Map<String, Object> templateVariables = Map.of("key", "value");

        when(templates.renderObject("test-template", templateVariables)).thenReturn("rendered prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(jsonResponse));

        CodeGenerationResponseDTO result = strategy.testCallChatClient(user, exercise, "test-template", templateVariables);

        assertThat(result.getSolutionPlan()).isEqualTo(expectedPlan);
        verifyNoInteractions(llmTokenUsageService);
    }

    @Test
    void callChatClient_whenTokenUsagePersistenceFails_returnsResponse() throws Exception {
        String expectedPlan = "Generated solution plan";
        String jsonResponse = "{\"solutionPlan\":\"" + expectedPlan + "\",\"files\":[]}";
        Map<String, Object> templateVariables = Map.of("key", "value");
        String modelName = "gpt-5-mini-2025-08-07";
        String pipelineId = "HYPERION_CODE_GENERATION_SOLUTION_TEST_TEMPLATE";
        LLMRequest llmRequest = new LLMRequest(modelName, 11, 0.23f, 7, 1.84f, pipelineId);

        when(templates.renderObject("test-template", templateVariables)).thenReturn("rendered prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(jsonResponse, modelName, 11, 7));
        when(llmTokenUsageService.buildLLMRequest(modelName, 11, 7, pipelineId)).thenReturn(llmRequest);
        doThrow(new IllegalStateException("database unavailable")).when(llmTokenUsageService).saveLLMTokenUsage(any(), eq(LLMServiceType.HYPERION), any());

        CodeGenerationResponseDTO result = strategy.testCallChatClient(user, exercise, "test-template", templateVariables);

        assertThat(result.getSolutionPlan()).isEqualTo(expectedPlan);
        verify(llmTokenUsageService).saveLLMTokenUsage(eq(List.of(llmRequest)), eq(LLMServiceType.HYPERION), any());
    }

    @Test
    void callChatClient_withNegativeUsageMetadata_clampsTokenUsageToZeroBeforeTracking() throws Exception {
        String expectedPlan = "Generated solution plan";
        String jsonResponse = "{\"solutionPlan\":\"" + expectedPlan + "\",\"files\":[]}";
        Map<String, Object> templateVariables = Map.of("key", "value");
        String modelName = "gpt-5-mini-2025-08-07";
        String pipelineId = "HYPERION_CODE_GENERATION_SOLUTION_TEST_TEMPLATE";
        LLMRequest llmRequest = new LLMRequest(modelName, 0, 0.23f, 7, 1.84f, pipelineId);

        when(templates.renderObject("test-template", templateVariables)).thenReturn("rendered prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(jsonResponse, modelName, -11, 7));
        when(llmTokenUsageService.buildLLMRequest(modelName, 0, 7, pipelineId)).thenReturn(llmRequest);

        CodeGenerationResponseDTO result = strategy.testCallChatClient(user, exercise, "test-template", templateVariables);

        assertThat(result.getSolutionPlan()).isEqualTo(expectedPlan);
        verify(llmTokenUsageService).buildLLMRequest(modelName, 0, 7, pipelineId);
        verify(llmTokenUsageService).saveLLMTokenUsage(eq(List.of(llmRequest)), eq(LLMServiceType.HYPERION), any());
    }

    @Test
    void callChatClient_withPromptPath_buildsPipelineIdFromFilenameOnly() throws Exception {
        String expectedPlan = "Generated solution plan";
        String jsonResponse = "{\"solutionPlan\":\"" + expectedPlan + "\",\"files\":[]}";
        Map<String, Object> templateVariables = Map.of("key", "value");
        String modelName = "gpt-5-mini-2025-08-07";
        String promptPath = "/prompts/hyperion/solution/1_plan.st";
        String pipelineId = "HYPERION_CODE_GENERATION_SOLUTION_1_PLAN";
        LLMRequest llmRequest = new LLMRequest(modelName, 11, 0.23f, 7, 1.84f, pipelineId);

        when(templates.renderObject(promptPath, templateVariables)).thenReturn("rendered prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(jsonResponse, modelName, 11, 7));
        when(llmTokenUsageService.buildLLMRequest(modelName, 11, 7, pipelineId)).thenReturn(llmRequest);

        CodeGenerationResponseDTO result = strategy.testCallChatClient(user, exercise, promptPath, templateVariables);

        assertThat(result.getSolutionPlan()).isEqualTo(expectedPlan);
        verify(llmTokenUsageService).buildLLMRequest(modelName, 11, 7, pipelineId);
        verify(llmTokenUsageService).saveLLMTokenUsage(eq(List.of(llmRequest)), eq(LLMServiceType.HYPERION), any());
    }

    @Test
    void callChatClient_withInvalidJson_throwsNetworkingException() {
        String invalidJsonResponse = "{\"solutionPlan\":\"broken json\"";
        Map<String, Object> templateVariables = Map.of("key", "value");
        String modelName = "gpt-5-mini-2025-08-07";

        when(templates.renderObject("test-template", templateVariables)).thenReturn("rendered prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(invalidJsonResponse, modelName, 11, 7));

        assertThatThrownBy(() -> strategy.testCallChatClient(user, exercise, "test-template", templateVariables)).isInstanceOf(NetworkingException.class)
                .hasMessageContaining("AI response processing failed due to illegal argument. Please retry.");
        verifyNoInteractions(llmTokenUsageService);
    }

    @Test
    void callChatClient_withMarkdownWrappedJson_returnsResponse() throws Exception {
        String expectedPlan = "Generated solution plan";
        String wrappedJsonResponse = """
                ```json
                {"solutionPlan":"%s","files":[]}
                ```
                """.formatted(expectedPlan);
        Map<String, Object> templateVariables = Map.of("key", "value");

        when(templates.renderObject("test-template", templateVariables)).thenReturn("rendered prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(wrappedJsonResponse));

        CodeGenerationResponseDTO result = strategy.testCallChatClient(user, exercise, "test-template", templateVariables);

        assertThat(result).isNotNull();
        assertThat(result.getSolutionPlan()).isEqualTo(expectedPlan);
    }

    @Test
    void callChatClient_withSurroundingTextAndEmbeddedJson_returnsResponse() throws Exception {
        String expectedPlan = "Generated solution plan";
        String wrappedJsonResponse = """
                Here is the generated output:
                {"solutionPlan":"%s","files":[]}
                """.formatted(expectedPlan);
        Map<String, Object> templateVariables = Map.of("key", "value");

        when(templates.renderObject("test-template", templateVariables)).thenReturn("rendered prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(wrappedJsonResponse));

        CodeGenerationResponseDTO result = strategy.testCallChatClient(user, exercise, "test-template", templateVariables);

        assertThat(result).isNotNull();
        assertThat(result.getSolutionPlan()).isEqualTo(expectedPlan);
    }

    @Test
    void callChatClient_withTransientAiException_throwsNetworkingException() {
        Map<String, Object> templateVariables = Map.of("key", "value");
        when(templates.renderObject("test-template", templateVariables)).thenReturn("rendered prompt");
        when(chatModel.call(any(Prompt.class))).thenThrow(new TransientAiException("Temporary AI service issue"));

        assertThatThrownBy(() -> strategy.testCallChatClient(user, exercise, "test-template", templateVariables)).isInstanceOf(NetworkingException.class)
                .hasMessageContaining("Temporary AI service issue. Please retry.");
    }

    @Test
    void callChatClient_withNonTransientAiException_throwsNetworkingException() {
        Map<String, Object> templateVariables = Map.of("key", "value");
        when(templates.renderObject("test-template", templateVariables)).thenReturn("rendered prompt");
        when(chatModel.call(any(Prompt.class))).thenThrow(new NonTransientAiException("AI configuration error"));

        assertThatThrownBy(() -> strategy.testCallChatClient(user, exercise, "test-template", templateVariables)).isInstanceOf(NetworkingException.class)
                .hasMessageContaining("AI request failed due to configuration or input. Check model and request.");
    }

    @Test
    void callChatClient_withRuntimeException_throwsNetworkingException() {
        Map<String, Object> templateVariables = Map.of("key", "value");
        when(templates.renderObject("test-template", templateVariables)).thenReturn("rendered prompt");
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("JSON parse failed"));

        assertThatThrownBy(() -> strategy.testCallChatClient(user, exercise, "test-template", templateVariables)).isInstanceOf(NetworkingException.class)
                .hasMessageContaining("AI request failed due to an internal processing error.").hasRootCauseMessage("JSON parse failed");
    }

    @Test
    void callChatClient_withChannelTimeout_throwsUserFriendlyNetworkingException() {
        Map<String, Object> templateVariables = Map.of("key", "value");
        when(templates.renderObject("test-template", templateVariables)).thenReturn("rendered prompt");
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException(new TimeoutException("Channel response timed out after 60000 milliseconds.")));

        assertThatThrownBy(() -> strategy.testCallChatClient(user, exercise, "test-template", templateVariables)).isInstanceOf(NetworkingException.class).hasMessageContaining(
                "The AI took too long to respond and this generation request timed out after 5 minutes. Please refresh first to check whether any files were already created or updated. If nothing changed, start the generation again.");
    }

    private void setupMockTemplateAndChatResponses(String finalResponse) {
        String planResponse = "{\"solutionPlan\":\"Generated plan\",\"files\":[]}";

        when(templates.renderObject(eq("test-plan-template"), anyMap())).thenReturn("rendered plan");
        when(templates.renderObject(eq("test-logic-template"), anyMap())).thenReturn("rendered logic");

        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(planResponse)).thenReturn(createChatResponse(finalResponse));
    }

    private ChatResponse createChatResponse(String content) {
        AssistantMessage message = new AssistantMessage(content);
        Generation generation = new Generation(message);
        ChatResponseMetadata metadata = ChatResponseMetadata.builder().build();
        return new ChatResponse(List.of(generation), metadata);
    }

    private ChatResponse createChatResponse(String content, String modelName, int promptTokens, int completionTokens) {
        AssistantMessage message = new AssistantMessage(content);
        Generation generation = new Generation(message);
        ChatResponseMetadata metadata = ChatResponseMetadata.builder().model(modelName).usage(new DefaultUsage(promptTokens, completionTokens)).build();
        return new ChatResponse(List.of(generation), metadata);
    }

    private int getMaxSelectedFeedbackThreadsLength() {
        return (int) ReflectionTestUtils.getField(HyperionCodeGenerationService.class, "MAX_SELECTED_FEEDBACK_THREADS_LENGTH");
    }

    private String createOversizedThreadsPayload(int threadCount, int commentLength) throws Exception {
        StringBuilder payload = new StringBuilder("{\"repositoryType\":\"SOLUTION\",\"threads\":[");
        for (int index = 0; index < threadCount; index++) {
            if (index > 0) {
                payload.append(',');
            }
            payload.append("{\"id\":").append(index).append(",\"comments\":[{\"type\":\"USER\",\"text\":\"").append("x".repeat(commentLength)).append("\"}]}");
        }
        payload.append("]}");
        return OBJECT_MAPPER.writeValueAsString(OBJECT_MAPPER.readTree(payload.toString()));
    }

    private static class TestCodeGenerationStrategy extends HyperionCodeGenerationService {

        public TestCodeGenerationStrategy(ChatClient chatClient, HyperionPromptTemplateService templates, LLMTokenUsageService llmTokenUsageService) {
            super(chatClient, templates, llmTokenUsageService);
        }

        @Override
        protected CodeGenerationResponseDTO generateSolutionPlan(User user, ProgrammingExercise exercise, Long courseId, String previousBuildLogs, String repositoryStructure,
                String consistencyIssues, String selectedFeedbackThreads) throws NetworkingException {
            Map<String, Object> variables = Map.of("test", "plan");
            return callChatClient(user, exercise, courseId, "test-plan-template", variables);
        }

        @Override
        protected CodeGenerationResponseDTO defineFileStructure(User user, ProgrammingExercise exercise, Long courseId, String solutionPlan, String repositoryStructure,
                String consistencyIssues, String selectedFeedbackThreads) throws NetworkingException {
            Map<String, Object> variables = Map.of("test", "structure");
            return callChatClient(user, exercise, courseId, "test-structure-template", variables);
        }

        @Override
        protected CodeGenerationResponseDTO generateClassAndMethodHeaders(User user, ProgrammingExercise exercise, Long courseId, String solutionPlan, String repositoryStructure,
                String consistencyIssues, String selectedFeedbackThreads) throws NetworkingException {
            Map<String, Object> variables = Map.of("test", "headers");
            return callChatClient(user, exercise, courseId, "test-headers-template", variables);
        }

        @Override
        protected CodeGenerationResponseDTO generateCoreLogic(User user, ProgrammingExercise exercise, Long courseId, String solutionPlan, String repositoryStructure,
                String consistencyIssues, String selectedFeedbackThreads) throws NetworkingException {
            Map<String, Object> variables = Map.of("test", "logic");
            return callChatClient(user, exercise, courseId, "test-logic-template", variables);
        }

        @Override
        protected RepositoryType getRepositoryType() {
            return RepositoryType.SOLUTION;
        }

        // Expose protected method for testing
        public CodeGenerationResponseDTO testCallChatClient(User user, ProgrammingExercise exercise, Long courseId, String prompt, Map<String, Object> templateVariables)
                throws NetworkingException {
            return callChatClient(user, exercise, courseId, prompt, templateVariables);
        }

        public CodeGenerationResponseDTO testCallChatClient(User user, ProgrammingExercise exercise, String prompt, Map<String, Object> templateVariables)
                throws NetworkingException {
            return testCallChatClient(user, exercise, 1L, prompt, templateVariables);
        }
    }
}
