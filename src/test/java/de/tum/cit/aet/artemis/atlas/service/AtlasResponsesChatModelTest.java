package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.ChatModel;
import com.openai.models.ResponsesModel;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.ResponseReasoningItem;
import com.openai.models.responses.ResponseStatus;
import com.openai.services.blocking.ResponseService;

import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies the narrow Responses adapter and its native Spring AI tool-calling integration.
 */
@ExtendWith(MockitoExtension.class)
class AtlasResponsesChatModelTest {

    @Mock
    private OpenAIClient openAIClient;

    @Mock
    private ResponseService responseService;

    @BeforeEach
    void setUp() {
        lenient().when(openAIClient.responses()).thenReturn(responseService);
    }

    @Test
    void nativeToolCallingAdvisor_executesCallbackAndReplaysOrderedResponsesItems() {
        ResponseFunctionToolCall functionCall = functionCall();
        ResponseReasoningItem firstReasoning = reasoning("sealed-first");
        ResponseReasoningItem secondReasoning = reasoning("sealed-second");
        Response firstResponse = response(List.of(ResponseOutputItem.ofReasoning(firstReasoning), ResponseOutputItem.ofFunctionCall(functionCall)), "first");
        Response secondResponse = response(List.of(ResponseOutputItem.ofReasoning(secondReasoning), ResponseOutputItem.ofMessage(message("done"))), "second");
        when(responseService.create(any(ResponseCreateParams.class))).thenReturn(firstResponse, secondResponse);

        AtomicReference<String> contextMarker = new AtomicReference<>();
        ToolCallback callback = FunctionToolCallback
                .<Map<String, Object>, String>builder("lookup", (arguments, context) -> toolResult(arguments.get("value").toString(), context, contextMarker))
                .description("Looks up a value.").inputType(Map.class).inputSchema("{\"type\":\"object\",\"properties\":{\"value\":{\"type\":\"string\"}}}").build();
        ChatClient chatClient = ChatClient.builder(new AtlasResponsesChatModel(openAIClient, new JsonMapper(), "gpt-test"))
                .defaultAdvisors(org.springframework.ai.chat.client.advisor.ToolCallingAdvisor.builder().build()).build();

        ChatResponse result = chatClient.prompt().user("find it").options(OpenAiChatOptions.builder().deploymentName("gpt-test")).toolContext(Map.of("marker", "ctx"))
                .toolCallbacks(callback).call().chatResponse();

        assertThat(result.getResult().getOutput().getText()).isEqualTo("done");
        assertThat(contextMarker).hasValue("ctx");

        ArgumentCaptor<ResponseCreateParams> requestCaptor = ArgumentCaptor.forClass(ResponseCreateParams.class);
        org.mockito.Mockito.verify(responseService, org.mockito.Mockito.times(2)).create(requestCaptor.capture());
        List<ResponseInputItem> secondInput = requestCaptor.getAllValues().get(1).input().orElseThrow().asResponse();
        assertThat(secondInput).hasSize(4);
        assertThat(secondInput.get(0).easyInputMessage()).isPresent();
        assertThat(secondInput.get(1).reasoning()).hasValue(firstReasoning);
        assertThat(secondInput.get(2).functionCall()).hasValue(functionCall);
        assertThat(secondInput.get(3).functionCallOutput()).get().extracting(ResponseInputItem.FunctionCallOutput::callId).isEqualTo("call-1");
        assertThat(requestCaptor.getAllValues().get(0).store()).hasValue(false);
        assertThat(requestCaptor.getAllValues().get(0).include()).hasValue(List.of(com.openai.models.responses.ResponseIncludable.REASONING_ENCRYPTED_CONTENT));
    }

    @Test
    void missingUsageIsPreservedAsAbsent() {
        Response responseWithoutUsage = response(List.of(ResponseOutputItem.ofMessage(message("done"))), "missing-usage");
        when(responseService.create(any(ResponseCreateParams.class))).thenReturn(responseWithoutUsage);

        ChatResponse result = new AtlasResponsesChatModel(openAIClient, new JsonMapper(), "gpt-test")
                .call(new Prompt(List.of(new UserMessage("hello")), OpenAiChatOptions.builder().deploymentName("gpt-test").build()));

        // Spring AI supplies EmptyUsage when provider usage is absent; the HTTP ledger retains unknown cost.
        assertThat(result.getMetadata().getUsage()).isInstanceOf(org.springframework.ai.chat.metadata.EmptyUsage.class);
    }

    @ParameterizedTest
    @MethodSource("responseModels")
    void preservesResponseModelIdentity(ResponsesModel responseModel, String expectedModel) {
        Response responseWithModel = response(List.of(ResponseOutputItem.ofMessage(message("done"))), "model-identity");
        when(responseWithModel.model()).thenReturn(responseModel);
        when(responseService.create(any(ResponseCreateParams.class))).thenReturn(responseWithModel);

        ChatResponse result = new AtlasResponsesChatModel(openAIClient, new JsonMapper(), "gpt-test")
                .call(new Prompt(new UserMessage("hello"), OpenAiChatOptions.builder().deploymentName("gpt-test").build()));

        assertThat(result.getMetadata().getModel()).isEqualTo(expectedModel);
    }

    private static Stream<Arguments> responseModels() {
        return Stream.of(Arguments.of(ResponsesModel.ofString("custom-string"), "custom-string"),
                Arguments.of(ResponsesModel.ofChat(ChatModel.GPT_5_6_LUNA), ChatModel.GPT_5_6_LUNA.asString()),
                Arguments.of(ResponsesModel.ofOnly(ResponsesModel.ResponsesOnlyModel.O3_PRO), ResponsesModel.ResponsesOnlyModel.O3_PRO.asString()));
    }

    @Test
    void failedAndIncompleteResponsesAreRejected() {
        Response incomplete = response(List.of(ResponseOutputItem.ofMessage(message("partial"))), "incomplete");
        when(incomplete.status()).thenReturn(Optional.of(ResponseStatus.INCOMPLETE));
        when(responseService.create(any(ResponseCreateParams.class))).thenReturn(incomplete);

        ChatResponse result = new AtlasResponsesChatModel(openAIClient, new JsonMapper(), "gpt-test")
                .call(new Prompt(new UserMessage("hello"), OpenAiChatOptions.builder().deploymentName("gpt-test").build()));
        assertThat(result.getResult().getMetadata().getFinishReason()).isEqualTo("incomplete");
        assertThat(result.getResult().getOutput().hasToolCalls()).isFalse();
    }

    @Test
    void terminalToolRetainsModelAndAccumulatedUsageWithoutAnotherProviderRound() {
        runTerminalRound(true);
    }

    @Test
    void missingEarlierUsageRemainsUnknownAfterTerminalTool() {
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
        }
        withUsage(last, 20, 3);
        when(responseService.create(any(ResponseCreateParams.class))).thenReturn(first, last);
        var adapter = new AtlasResponsesChatModel(openAIClient, new JsonMapper(), "gpt-5.6-luna");
        var properties = new de.tum.cit.aet.artemis.atlas.config.AtlasOrchestratorProperties("gpt-5.6-luna", 1.0, "xhigh", true, 300, 10, 30000L, 10);
        var service = new AtlasAgentDelegationService(null, mock(AtlasPromptTemplateService.class), null, new de.tum.cit.aet.artemis.atlas.config.AtlasAgentProperties("chat", 1.0),
                properties, new de.tum.cit.aet.artemis.atlas.config.AtlasResponsesApiConfiguration.AtlasResponsesChatClient(ChatClient.create(adapter)));
        var read = FunctionToolCallback.<Map<String, Object>, String>builder("listCompetencyIndex", input -> "{}").inputType(Map.class).build();
        var terminal = org.springframework.ai.tool.method.MethodToolCallbackProvider.builder().toolObjects(new AtlasOrchestratorTerminalToolService()).build();
        Map<String, Object> context = new java.util.HashMap<>(Map.of("courseId", 42L));
        var result = service.delegateOrchestratorRound("system", "work", OpenAiChatOptions.builder().deploymentName("gpt-5.6-luna").reasoningEffort("xhigh"), context,
                org.springframework.ai.tool.ToolCallbackProvider.from(read), terminal);
        assertThat(AtlasToolCallBudget.existingBudget(context).completion().message()).isEqualTo("Verified the mappings.");
        assertThat(result.getMetadata().getModel()).isEqualTo("gpt-test");
        assertThat(result.getMetadata().<Boolean>get(AtlasResponsesChatModel.USAGE_COMPLETE_METADATA_KEY)).isEqualTo(firstUsageKnown);
        if (firstUsageKnown) {
            assertThat(result.getMetadata().getUsage().getPromptTokens()).isEqualTo(30);
            assertThat(result.getMetadata().getUsage().getCompletionTokens()).isEqualTo(5);
        }
        var requests = ArgumentCaptor.forClass(ResponseCreateParams.class);
        org.mockito.Mockito.verify(responseService, org.mockito.Mockito.times(2)).create(requests.capture());
        assertThat(requests.getValue().reasoning().orElseThrow().effort().orElseThrow().asString()).isEqualTo("xhigh");
        assertThat(requests.getValue().temperature()).isEmpty();
    }

    @Test
    void flavorEditEntityIsParsedThroughResponsesWithLunaHigh() {
        Response result = response(List.of(ResponseOutputItem.ofMessage(message("{\"edits\":[{\"reason\":\"flavor\",\"search\":\"Alice. \",\"replace\":\"\"}]}"))), "edits");
        when(responseService.create(any(ResponseCreateParams.class))).thenReturn(result);
        var parsed = ChatClient.create(new AtlasResponsesChatModel(openAIClient, new JsonMapper(), "gpt-5.6-luna")).prompt().user("Alice. Calculate 2+3.")
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
    void incompleteResponseRetainsUsageAndNeverExecutesItsToolCalls() {
        var incomplete = response(List.of(ResponseOutputItem.ofFunctionCall(functionCall())), "incomplete-usage");
        when(incomplete.status()).thenReturn(Optional.of(ResponseStatus.INCOMPLETE));
        withUsage(incomplete, 10, 2);
        when(responseService.create(any(ResponseCreateParams.class))).thenReturn(incomplete);
        var result = new AtlasResponsesChatModel(openAIClient, new JsonMapper(), "gpt-test").call(new Prompt("work"));
        assertThat(result.getResult().getOutput().hasToolCalls()).isFalse();
        assertThat(result.getResult().getMetadata().getFinishReason()).isEqualTo("incomplete");
        assertThat(result.getMetadata().getUsage().getTotalTokens()).isEqualTo(12);
    }

    @Test
    void cancelledDispatchNeverContactsProvider() {
        var adapter = new AtlasResponsesChatModel(openAIClient, new JsonMapper(), "luna");
        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> adapter.call(new Prompt("work"))).isInstanceOf(java.util.concurrent.CancellationException.class);
            org.mockito.Mockito.verifyNoInteractions(responseService);
        }
        finally {
            Thread.interrupted();
        }
    }

    @Test
    void rejectsIncompatibleOptionsAndMissingModelBeforeDispatch() {
        var adapter = new AtlasResponsesChatModel(openAIClient, new JsonMapper(), null);
        assertThatThrownBy(() -> adapter.call(new Prompt("work", org.springframework.ai.chat.prompt.ChatOptions.builder().model("luna").build())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requires OpenAiChatOptions");
        assertThatThrownBy(() -> adapter.call(new Prompt("work", OpenAiChatOptions.builder().model("").build()))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a model");
        org.mockito.Mockito.verifyNoInteractions(responseService);
    }

    @ParameterizedTest
    @MethodSource("invalidReplayMessages")
    void rejectsInvalidReplayBeforeDispatch(org.springframework.ai.chat.messages.AssistantMessage message, String error) {
        var adapter = new AtlasResponsesChatModel(openAIClient, new JsonMapper(), "luna");
        assertThatThrownBy(() -> adapter.call(new Prompt(List.of(message)))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(error);
        org.mockito.Mockito.verifyNoInteractions(responseService);
    }

    private static Stream<Arguments> invalidReplayMessages() {
        return Stream.of(
                Arguments.of(org.springframework.ai.chat.messages.AssistantMessage.builder().content("")
                        .properties(Map.of(AtlasResponsesChatModel.RESPONSES_OUTPUT_ITEMS_METADATA_KEY, "broken")).build(), "list of output items"),
                Arguments.of(org.springframework.ai.chat.messages.AssistantMessage.builder().content("")
                        .properties(Map.of(AtlasResponsesChatModel.RESPONSES_OUTPUT_ITEMS_METADATA_KEY, List.of("broken"))).build(), "unsupported output item"),
                Arguments.of(
                        org.springframework.ai.chat.messages.AssistantMessage.builder().content("")
                                .toolCalls(List.of(new org.springframework.ai.chat.messages.AssistantMessage.ToolCall("call", "function", "lookup", "{}"))).build(),
                        "preserved Responses output metadata"));
    }

    @Test
    void replaysPlainAssistantAndPreservedOutputMessageInOrder() {
        var preserved = message("original output");
        var response = response(List.of(ResponseOutputItem.ofMessage(message("done"))), "replay");
        when(responseService.create(any(ResponseCreateParams.class))).thenReturn(response);
        var adapter = new AtlasResponsesChatModel(openAIClient, new JsonMapper(), "default-model");
        adapter.call(new Prompt(
                List.of(new org.springframework.ai.chat.messages.AssistantMessage("plain text"),
                        org.springframework.ai.chat.messages.AssistantMessage.builder().content("ignored duplicate")
                                .properties(Map.of(AtlasResponsesChatModel.RESPONSES_OUTPUT_ITEMS_METADATA_KEY, List.of(ResponseOutputItem.ofMessage(preserved)))).build()),
                OpenAiChatOptions.builder().model("").temperature(0.4).parallelToolCalls(false).build()));
        var request = ArgumentCaptor.forClass(ResponseCreateParams.class);
        org.mockito.Mockito.verify(responseService).create(request.capture());
        assertThat(request.getValue().model().orElseThrow().asString()).isEqualTo("default-model");
        assertThat(request.getValue().temperature()).hasValue(0.4);
        assertThat(request.getValue().parallelToolCalls()).hasValue(false);
        assertThat(request.getValue().reasoning()).isEmpty();
        var input = request.getValue().input().orElseThrow().asResponse();
        assertThat(input).hasSize(2);
        assertThat(input.getFirst().asEasyInputMessage().content().asTextInput()).isEqualTo("plain text");
        assertThat(input.get(1).asResponseOutputMessage()).isEqualTo(preserved);
    }

    @Test
    void malformedToolSchemaNeverContactsProvider() {
        var callback = mock(ToolCallback.class);
        when(callback.getToolDefinition())
                .thenReturn(org.springframework.ai.tool.definition.ToolDefinition.builder().name("broken").description("broken schema").inputSchema("{").build());
        var adapter = new AtlasResponsesChatModel(openAIClient, new JsonMapper(), "luna");
        assertThatThrownBy(() -> adapter.call(new Prompt("work", OpenAiChatOptions.builder().toolCallbacks(callback).build()))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JSON schema for Atlas tool broken");
        org.mockito.Mockito.verifyNoInteractions(responseService);
    }

    private static void withUsage(Response response, long input, long output) {
        var usage = mock(com.openai.models.responses.ResponseUsage.class);
        when(usage.inputTokens()).thenReturn(input);
        when(usage.outputTokens()).thenReturn(output);
        when(usage.totalTokens()).thenReturn(input + output);
        when(response.usage()).thenReturn(Optional.of(usage));
    }

    private static String toolResult(String arguments, ToolContext context, AtomicReference<String> marker) {
        marker.set((String) context.getContext().get("marker"));
        return "result:" + arguments;
    }

    private static Response response(List<ResponseOutputItem> output, String id) {
        Response response = mock(Response.class);
        lenient().when(response.id()).thenReturn(id);
        lenient().when(response.model()).thenReturn(ResponsesModel.ofString("gpt-test"));
        lenient().when(response.output()).thenReturn(output);
        lenient().when(response.status()).thenReturn(Optional.of(ResponseStatus.COMPLETED));
        lenient().when(response.error()).thenReturn(Optional.empty());
        lenient().when(response.incompleteDetails()).thenReturn(Optional.empty());
        lenient().when(response.usage()).thenReturn(Optional.empty());
        return response;
    }

    private static ResponseFunctionToolCall functionCall() {
        return ResponseFunctionToolCall.builder().id("fc-1").type(JsonValue.from("function_call")).callId("call-1").name("lookup").arguments("{\"value\":\"x\"}")
                .status(ResponseFunctionToolCall.Status.COMPLETED).build();
    }

    private static ResponseReasoningItem reasoning(String encryptedContent) {
        return ResponseReasoningItem.builder().id("rs-1").type(JsonValue.from("reasoning")).summary(List.of()).encryptedContent(encryptedContent)
                .status(ResponseReasoningItem.Status.COMPLETED).build();
    }

    private static ResponseOutputMessage message(String text) {
        ResponseOutputText outputText = ResponseOutputText.builder().type(JsonValue.from("output_text")).text(text).annotations(List.of()).build();
        return ResponseOutputMessage.builder().id("msg-1").type(JsonValue.from("message")).role(JsonValue.from("assistant")).status(ResponseOutputMessage.Status.COMPLETED)
                .content(List.of(ResponseOutputMessage.Content.ofOutputText(outputText))).build();
    }
}
