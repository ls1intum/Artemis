package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import com.openai.core.JsonValue;
import com.openai.models.completions.CompletionUsage;

import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.domain.LLMTokenUsageRequest;
import de.tum.cit.aet.artemis.admin.domain.LLMTokenUsageTrace;
import de.tum.cit.aet.artemis.admin.repository.LLMTokenUsageRequestRepository;
import de.tum.cit.aet.artemis.admin.repository.LLMTokenUsageTraceRepository;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.config.llm.LlmTestCatalog;

class AtlasTokenUsageAdvisorTest {

    @ParameterizedTest
    @ValueSource(doubles = { 0.0, 0.003 })
    void persistsEachProviderAmountBeforeRealToolLoopAccumulation(double lastCost) {
        var repository = mock(LLMTokenUsageTraceRepository.class);
        var accounting = accounting(repository);
        ChatModel model = model();
        when(model.call(any(Prompt.class))).thenReturn(response("first-model", true, 100, 20, 0.01), response("last-model", false, 50, 5, lastCost));

        ChatResponse result = client(model, record(accounting)).prompt().user("work").options(OpenAiChatOptions.builder().model("test")).tools(tool(() -> "found")).call()
                .chatResponse();

        var traces = ArgumentCaptor.forClass(LLMTokenUsageTrace.class);
        verify(repository, times(2)).save(traces.capture());
        var requests = traces.getAllValues().stream().map(trace -> trace.getLLMRequests().iterator().next()).toList();
        assertThat(requests).extracting(LLMTokenUsageRequest::getModel).containsExactly("first-model", "last-model");
        assertThat(requests).extracting(LLMTokenUsageRequest::getTotalCostUsd).containsExactly(0.01, lastCost);
        assertThat(requests).extracting(LLMTokenUsageRequest::getNumInputTokens).containsExactly(100, 50);
        assertThat(traces.getAllValues()).allSatisfy(trace -> {
            assertThat(trace.getCourseId()).isEqualTo(42L);
            assertThat(trace.getExerciseId()).isEqualTo(7L);
        });
        assertThat(result.getMetadata().getUsage().getPromptTokens()).isEqualTo(150);
        assertThat(result.getMetadata().getUsage().getNativeUsage()).isNull();
    }

    @Test
    void missingEarlierUsageCreatesUnknownRowWithoutDiscardingLaterProviderAmount() {
        var repository = mock(LLMTokenUsageTraceRepository.class);
        var accounting = accounting(repository);
        ChatModel model = model();
        var unknown = new ChatResponse(List.of(new Generation(assistant(true))), ChatResponseMetadata.builder().model("test").build());
        var priced = response("test", false, 50, 5, 0.003);
        when(model.call(any(Prompt.class))).thenReturn(unknown, priced);

        client(model, record(accounting)).prompt().user("work").options(OpenAiChatOptions.builder().model("test")).tools(tool(() -> "found")).call().chatResponse();

        var traces = ArgumentCaptor.forClass(LLMTokenUsageTrace.class);
        verify(repository, times(2)).save(traces.capture());
        var requests = traces.getAllValues().stream().map(trace -> trace.getLLMRequests().iterator().next()).toList();
        assertThat(requests.getFirst().getTotalCostUsd()).isNull();
        assertThat(requests.getLast().getTotalCostUsd()).isEqualTo(0.003);
    }

    @Test
    void nestedToolLoopsRecordEveryResponseOnceInTheirOwnInvocation() {
        ChatModel outer = model();
        ChatModel inner = model();
        when(outer.call(any(Prompt.class))).thenReturn(response("outer", true, 10, 2, 0.01), response("outer", false, 20, 3, 0.02));
        when(inner.call(any(Prompt.class))).thenReturn(response("inner", true, 30, 4, 0.03), response("inner", false, 40, 5, 0.04));
        List<ChatResponse> outerCalls = new ArrayList<>();
        List<ChatResponse> innerCalls = new ArrayList<>();
        var worker = client(inner, innerCalls::add);
        client(outer, outerCalls::add).prompt().user("outer").options(OpenAiChatOptions.builder().model("outer"))
                .tools(tool(() -> worker.prompt().user("inner").options(OpenAiChatOptions.builder().model("inner")).tools(tool(() -> "found")).call().content())).call()
                .chatResponse();

        assertThat(outerCalls).extracting(call -> call.getMetadata().getUsage().getPromptTokens()).containsExactly(10, 20);
        assertThat(innerCalls).extracting(call -> call.getMetadata().getUsage().getPromptTokens()).containsExactly(30, 40);
    }

    @Test
    void laterProviderFailureKeepsEarlierRecordedCost() {
        var repository = mock(LLMTokenUsageTraceRepository.class);
        ChatModel model = model();
        when(model.call(any(Prompt.class))).thenReturn(response("test", true, 100, 20, 0.01)).thenThrow(new IllegalStateException("provider unavailable"));

        assertThatThrownBy(() -> client(model, record(accounting(repository))).prompt().user("work").options(OpenAiChatOptions.builder().model("test")).tools(tool(() -> "found"))
                .call().chatResponse()).isInstanceOf(IllegalStateException.class).hasMessageContaining("provider unavailable");

        var trace = ArgumentCaptor.forClass(LLMTokenUsageTrace.class);
        verify(repository).save(trace.capture());
        assertThat(trace.getValue().getLLMRequests()).singleElement().extracting(LLMTokenUsageRequest::getTotalCostUsd).isEqualTo(0.01);
    }

    @Test
    void recordsBeforeToolFailure() {
        List<ChatResponse> captured = new ArrayList<>();
        ChatModel model = model();
        when(model.call(any(Prompt.class))).thenReturn(response("test", true, 100, 20, 0.01));
        ToolCallback callback = new ToolCallback() {

            @Override
            public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return org.springframework.ai.tool.definition.ToolDefinition.builder().name("lookup").description("lookup").inputSchema("{\"type\":\"object\"}").build();
            }

            @Override
            public String call(String arguments) {
                throw new IllegalStateException("tool unavailable");
            }
        };
        assertThatThrownBy(() -> client(model, captured::add).prompt().user("work").options(OpenAiChatOptions.builder().model("test")).tools(callback).call().chatResponse())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("tool unavailable");
        assertThat(captured).hasSize(1);
    }

    @Test
    void recordingFailureDoesNotFailTheModelResponse() {
        var toolAdvisor = ToolCallingAdvisor.builder().build();
        var advisor = new AtlasTokenUsageAdvisor(response -> {
            throw new IllegalStateException("storage unavailable");
        }, toolAdvisor.getOrder());
        var chain = mock(CallAdvisorChain.class);
        var request = mock(ChatClientRequest.class);
        var response = new ChatClientResponse(response("test", false, 10, 2, 0.01), Map.of());
        when(chain.nextCall(request)).thenReturn(response);
        assertThat(advisor.adviseCall(request, chain)).isSameAs(response);
        assertThat(advisor.getOrder()).isGreaterThan(toolAdvisor.getOrder());
        assertThat(advisor.getName()).isEqualTo("AtlasTokenUsageAdvisor");
    }

    @Test
    void missingChatResponseIsNotInvented() {
        List<ChatResponse> captured = new ArrayList<>();
        var advisor = new AtlasTokenUsageAdvisor(captured::add, 0);
        var chain = mock(CallAdvisorChain.class);
        var request = mock(ChatClientRequest.class);
        var response = new ChatClientResponse(null, Map.of());
        when(chain.nextCall(request)).thenReturn(response);
        assertThat(advisor.adviseCall(request, chain)).isSameAs(response);
        assertThat(captured).isEmpty();
    }

    private static ChatClient client(ChatModel model, Consumer<ChatResponse> recorder) {
        var tools = ToolCallingAdvisor.builder().build();
        return ChatClient.builder(model).defaultAdvisors(tools, new AtlasTokenUsageAdvisor(recorder, tools.getOrder())).build();
    }

    private static ChatModel model() {
        var model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().model("test").build());
        return model;
    }

    private static ToolCallback tool(java.util.function.Supplier<String> action) {
        return FunctionToolCallback.<Map<String, Object>, String>builder("lookup", input -> action.get()).inputType(Map.class).build();
    }

    private static AssistantMessage assistant(boolean tools) {
        return AssistantMessage.builder().content(tools ? "" : "done").toolCalls(tools ? List.of(new AssistantMessage.ToolCall("call", "function", "lookup", "{}")) : List.of())
                .build();
    }

    private static ChatResponse response(String model, boolean tools, int input, int output, double usd) {
        var nativeUsage = CompletionUsage.builder().promptTokens(input).completionTokens(output).totalTokens(input + output).putAdditionalProperty("cost", JsonValue.from(usd))
                .putAdditionalProperty("cost_currency", JsonValue.from("USD")).build();
        return new ChatResponse(List.of(new Generation(assistant(tools))),
                ChatResponseMetadata.builder().model(model).usage(new DefaultUsage(input, output, input + output, nativeUsage)).build());
    }

    private static LLMTokenUsageService accounting(LLMTokenUsageTraceRepository repository) {
        return new LLMTokenUsageService(repository, mock(LLMTokenUsageRequestRepository.class), LlmTestCatalog.empty());
    }

    private static Consumer<ChatResponse> record(LLMTokenUsageService service) {
        return response -> service.trackModelCallTokenUsage(response, LLMServiceType.ATLAS, "ATLAS_ORCHESTRATION", scope -> scope.withCourse(42L).withExercise(7L));
    }
}
