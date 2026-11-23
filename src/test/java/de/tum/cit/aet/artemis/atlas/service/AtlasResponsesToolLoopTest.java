package de.tum.cit.aet.artemis.atlas.service;

import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.functionCall;
import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.message;
import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.response;
import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.withUsage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;

import com.openai.client.OpenAIClient;
import com.openai.core.JsonField;
import com.openai.core.JsonValue;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.services.blocking.ResponseService;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesChatModel;

/**
 * Verifies Atlas orchestration, flavor stripping and per-response usage accounting over the Responses adapter.
 */
@ExtendWith(MockitoExtension.class)
class AtlasResponsesToolLoopTest {

    @Mock
    private OpenAIClient openAIClient;

    @Mock
    private ResponseService responseService;

    @BeforeEach
    void setUp() {
        lenient().when(openAIClient.responses()).thenReturn(responseService);
    }

    @Test
    void terminalToolRetainsModelAndAccumulatedUsageWithoutAnotherProviderRound() {
        runTerminalRound(true);
    }

    @Test
    void missingEarlierUsageIsRetainedSeparatelyFromKnownTerminalUsage() {
        runTerminalRound(false);
    }

    private void runTerminalRound(boolean firstUsageKnown) {
        ResponseFunctionToolCall readCall = ResponseFunctionToolCall.builder().id("fc-read").callId("call-read").name("listCompetencyIndex").arguments("{}")
                .status(ResponseFunctionToolCall.Status.COMPLETED).build();
        ResponseFunctionToolCall terminalCall = ResponseFunctionToolCall.builder().id("fc-terminal").callId("call-terminal").name("completeOrchestration")
                .arguments("{\"verified\":true,\"message\":\"Verified the mappings.\"}").status(ResponseFunctionToolCall.Status.COMPLETED).build();
        Response first = response(List.of(ResponseOutputItem.ofFunctionCall(readCall)), "read");
        Response last = response(List.of(ResponseOutputItem.ofFunctionCall(terminalCall)), "terminal");
        if (firstUsageKnown) {
            withUsage(first, 10, 2);
            when(first.usage().orElseThrow()._additionalProperties()).thenReturn(Map.of("cost", JsonValue.from(0.01), "cost_currency", JsonValue.from("USD")));
        }
        withUsage(last, 20, 3);
        when(last.usage().orElseThrow()._additionalProperties()).thenReturn(Map.of("cost", JsonValue.from(0.02), "cost_currency", JsonValue.from("USD")));
        when(responseService.create(any(ResponseCreateParams.class))).thenReturn(first, last);
        // The orchestrator preset carries the model and reasoning effort; the harness passes no per-call model options.
        var adapter = new OpenAiResponsesChatModel(openAIClient, new JsonMapper(), OpenAiChatOptions.builder().deploymentName("gpt-5.6-luna").reasoningEffort("xhigh").build());
        var client = ChatClient.create(adapter);
        var service = new AtlasAgentDelegationService(client, client, client, mock(AtlasPromptTemplateService.class), null);
        var read = FunctionToolCallback.<Map<String, Object>, String>builder("listCompetencyIndex", input -> "{}").inputType(Map.class).build();
        var terminal = org.springframework.ai.tool.method.MethodToolCallbackProvider.builder().toolObjects(new AtlasOrchestratorTerminalToolService()).build();
        Map<String, Object> context = new java.util.HashMap<>(Map.of("courseId", 42L));
        List<ChatResponse> providerResponses = new ArrayList<>();
        var result = service.delegateOrchestratorRound("system", "work", context, providerResponses::add, org.springframework.ai.tool.ToolCallbackProvider.from(read), terminal);
        assertThat(AtlasToolCallBudget.existingBudget(context).completion().message()).isEqualTo("Verified the mappings.");
        assertThat(result.getMetadata().getModel()).isEqualTo("gpt-test");
        assertThat(providerResponses).hasSize(2);
        if (firstUsageKnown) {
            assertThat(providerResponses.getFirst().getMetadata().getUsage().getPromptTokens()).isEqualTo(10);
            assertThat(providerResponses.getFirst().getMetadata().getUsage().getCompletionTokens()).isEqualTo(2);
        }
        else {
            assertThat(providerResponses.getFirst().getMetadata().getUsage()).isInstanceOf(org.springframework.ai.chat.metadata.EmptyUsage.class);
        }
        assertThat(providerResponses.get(1).getMetadata().getUsage().getPromptTokens()).isEqualTo(20);
        assertThat(providerResponses.get(1).getMetadata().getUsage().getCompletionTokens()).isEqualTo(3);
        if (firstUsageKnown) {
            assertThat(result.getMetadata().getUsage().getPromptTokens()).isEqualTo(30);
            assertThat(result.getMetadata().getUsage().getCompletionTokens()).isEqualTo(5);
            assertThat(result.getMetadata().getUsage().getNativeUsage()).isNull();
            var firstUsage = providerResponses.getFirst().getMetadata().getUsage();
            assertThat(de.tum.cit.aet.artemis.aiworker.api.LLMResponseCost.reportedUsd(firstUsage.getNativeUsage(), firstUsage.getPromptTokens(), firstUsage.getCompletionTokens()))
                    .isEqualTo(0.01);
        }
        var lastUsage = providerResponses.getLast().getMetadata().getUsage();
        assertThat(de.tum.cit.aet.artemis.aiworker.api.LLMResponseCost.reportedUsd(lastUsage.getNativeUsage(), lastUsage.getPromptTokens(), lastUsage.getCompletionTokens()))
                .isEqualTo(0.02);
        var requests = ArgumentCaptor.forClass(ResponseCreateParams.class);
        org.mockito.Mockito.verify(responseService, org.mockito.Mockito.times(2)).create(requests.capture());
        assertThat(requests.getValue().reasoning().orElseThrow().effort().orElseThrow().asString()).isEqualTo("xhigh");
        assertThat(requests.getValue().temperature()).isEmpty();
    }

    @Test
    void flavorEditEntityIsParsedThroughResponsesWithLunaHigh() {
        Response result = response(List.of(ResponseOutputItem.ofMessage(message("{\"edits\":[{\"reason\":\"flavor\",\"search\":\"Alice. \",\"replace\":\"\"}]}"))), "edits");
        when(responseService.create(any(ResponseCreateParams.class))).thenReturn(result);
        var parsed = ChatClient.create(new OpenAiResponsesChatModel(openAIClient, new JsonMapper(), "gpt-5.6-luna")).prompt().user("Alice. Calculate 2+3.")
                .options(OpenAiChatOptions.builder().deploymentName("gpt-5.6-luna").reasoningEffort("high")).call()
                .entity(de.tum.cit.aet.artemis.atlas.dto.FlavorStripEditsDTO.class);
        assertThat(parsed.edits()).hasSize(1);
        assertThat(parsed.edits().getFirst().search()).isEqualTo("Alice. ");
        var requests = ArgumentCaptor.forClass(ResponseCreateParams.class);
        org.mockito.Mockito.verify(responseService).create(requests.capture());
        assertThat(requests.getValue().model().orElseThrow().asString()).isEqualTo("gpt-5.6-luna");
        assertThat(requests.getValue().reasoning().orElseThrow().effort().orElseThrow().asString()).isEqualTo("high");
    }

    @Test
    void unsupportedOutputRetainsProviderCostAndNeverExecutesTools() {
        var unsupported = mock(ResponseOutputItem.class);
        var billed = response(List.of(ResponseOutputItem.ofFunctionCall(functionCall()), unsupported), "unsupported-billed");
        withUsage(billed, 10, 2);
        when(billed.usage().orElseThrow()._additionalProperties()).thenReturn(Map.of("cost", JsonValue.from(0.01), "cost_currency", JsonValue.from("USD")));
        when(responseService.create(any(ResponseCreateParams.class))).thenReturn(billed);
        List<ChatResponse> calls = new ArrayList<>();
        var tools = org.springframework.ai.chat.client.advisor.ToolCallingAdvisor.builder().build();
        var client = ChatClient.builder(new OpenAiResponsesChatModel(openAIClient, new JsonMapper(), "gpt-test"))
                .defaultAdvisors(tools, new AtlasTokenUsageAdvisor(calls::add, tools.getOrder())).build();
        var callback = FunctionToolCallback.<Map<String, Object>, String>builder("lookup", input -> {
            throw new AssertionError("Unsupported provider output must never execute a tool");
        }).inputType(Map.class).build();
        var result = client.prompt().user("work").toolCallbacks(callback).call().chatResponse();
        assertThat(result.getResult().getMetadata().getFinishReason()).isEqualTo("unsupported_output");
        assertThat(result.getResult().getOutput().hasToolCalls()).isFalse();
        assertThat(calls).hasSize(1);
        var usage = calls.getFirst().getMetadata().getUsage();
        assertThat(de.tum.cit.aet.artemis.aiworker.api.LLMResponseCost.reportedUsd(usage.getNativeUsage(), usage.getPromptTokens(), usage.getCompletionTokens())).isEqualTo(0.01);
    }

    @ParameterizedTest
    @ValueSource(strings = { "id", "model", "status", "output" })
    void malformedProviderFieldsRetainCostWithoutExecutingTools(String field) {
        var billed = response(List.of(ResponseOutputItem.ofFunctionCall(functionCall())), "malformed-billed");
        withUsage(billed, 10, 2);
        when(billed.usage().orElseThrow()._additionalProperties()).thenReturn(Map.of("cost", JsonValue.from(0.01), "cost_currency", JsonValue.from("USD")));
        var error = new OpenAIInvalidDataException("Missing required " + field);
        switch (field) {
            case "id" -> when(billed.id()).thenThrow(error);
            case "model" -> when(billed.model()).thenThrow(error);
            case "status" -> when(billed.status()).thenThrow(error);
            case "output" -> when(billed.output()).thenThrow(error);
            default -> throw new AssertionError("Unexpected field " + field);
        }
        when(responseService.create(any(ResponseCreateParams.class))).thenReturn(billed);
        List<ChatResponse> calls = new ArrayList<>();
        var tools = org.springframework.ai.chat.client.advisor.ToolCallingAdvisor.builder().build();
        var client = ChatClient.builder(new OpenAiResponsesChatModel(openAIClient, new JsonMapper(), "requested-model"))
                .defaultAdvisors(tools, new AtlasTokenUsageAdvisor(calls::add, tools.getOrder())).build();
        var result = client.prompt().user("work").call().chatResponse();
        assertThat(result.getResult().getMetadata().getFinishReason()).isEqualTo("unsupported_output");
        assertThat(result.getResult().getOutput().hasToolCalls()).isFalse();
        assertThat(result.getMetadata().getModel()).isEqualTo(field.equals("id") || field.equals("model") ? "requested-model" : "gpt-test");
        assertThat(calls).hasSize(1);
        var usage = calls.getFirst().getMetadata().getUsage();
        assertThat(de.tum.cit.aet.artemis.aiworker.api.LLMResponseCost.reportedUsd(usage.getNativeUsage(), usage.getPromptTokens(), usage.getCompletionTokens())).isEqualTo(0.01);
    }

    @ParameterizedTest
    @ValueSource(strings = { "input", "output", "total" })
    void missingRequiredUsageCounterReturnsUnknownTerminalResponse(String field) {
        var invalid = response(List.of(ResponseOutputItem.ofFunctionCall(functionCall())), "missing-counter");
        var usage = mock(com.openai.models.responses.ResponseUsage.class);
        lenient().when(usage._inputTokensDetails()).thenReturn(JsonField.ofNullable(null));
        lenient().when(usage.inputTokens()).thenReturn(10L);
        lenient().when(usage.outputTokens()).thenReturn(2L);
        lenient().when(usage.totalTokens()).thenReturn(12L);
        var error = new OpenAIInvalidDataException("Missing required " + field);
        switch (field) {
            case "input" -> when(usage.inputTokens()).thenThrow(error);
            case "output" -> when(usage.outputTokens()).thenThrow(error);
            case "total" -> when(usage.totalTokens()).thenThrow(error);
            default -> throw new AssertionError("Unexpected counter " + field);
        }
        when(invalid.usage()).thenReturn(Optional.of(usage));
        when(responseService.create(any(ResponseCreateParams.class))).thenReturn(invalid);
        List<ChatResponse> calls = new ArrayList<>();
        var tools = org.springframework.ai.chat.client.advisor.ToolCallingAdvisor.builder().build();
        var client = ChatClient.builder(new OpenAiResponsesChatModel(openAIClient, new JsonMapper(), "gpt-test"))
                .defaultAdvisors(tools, new AtlasTokenUsageAdvisor(calls::add, tools.getOrder())).build();
        var result = client.prompt().user("work").call().chatResponse();
        assertThat(result.getResult().getMetadata().getFinishReason()).isEqualTo("invalid_usage");
        assertThat(result.getResult().getOutput().hasToolCalls()).isFalse();
        assertThat(result.getMetadata().getUsage()).isInstanceOf(org.springframework.ai.chat.metadata.EmptyUsage.class);
        assertThat(calls).containsExactly(result);
    }

}
