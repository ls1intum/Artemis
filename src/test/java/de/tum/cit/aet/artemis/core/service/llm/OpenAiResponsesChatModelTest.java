package de.tum.cit.aet.artemis.core.service.llm;

import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.functionCall;
import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.message;
import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.reasoning;
import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.response;
import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.withUsage;
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
import com.openai.models.ChatModel;
import com.openai.models.ResponsesModel;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseReasoningItem;
import com.openai.models.responses.ResponseStatus;
import com.openai.services.blocking.ResponseService;

import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies the narrow Responses adapter and its native Spring AI tool-calling integration.
 */
@ExtendWith(MockitoExtension.class)
class OpenAiResponsesChatModelTest {

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
        ChatClient chatClient = ChatClient.builder(new OpenAiResponsesChatModel(openAIClient, new JsonMapper(), "gpt-test"))
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
        assertThat(secondInput.get(3).functionCallOutput()).get().extracting(ResponseInputItem.FunctionCallOutput::callId).isEqualTo(Optional.of("call-1"));
        assertThat(requestCaptor.getAllValues().get(0).store()).hasValue(false);
        assertThat(requestCaptor.getAllValues().get(0).include()).hasValue(List.of(com.openai.models.responses.ResponseIncludable.REASONING_ENCRYPTED_CONTENT));
    }

    @Test
    void missingUsageIsPreservedAsAbsent() {
        Response responseWithoutUsage = response(List.of(ResponseOutputItem.ofMessage(message("done"))), "missing-usage");
        when(responseService.create(any(ResponseCreateParams.class))).thenReturn(responseWithoutUsage);

        ChatResponse result = new OpenAiResponsesChatModel(openAIClient, new JsonMapper(), "gpt-test")
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

        ChatResponse result = new OpenAiResponsesChatModel(openAIClient, new JsonMapper(), "gpt-test")
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

        ChatResponse result = new OpenAiResponsesChatModel(openAIClient, new JsonMapper(), "gpt-test")
                .call(new Prompt(new UserMessage("hello"), OpenAiChatOptions.builder().deploymentName("gpt-test").build()));
        assertThat(result.getResult().getMetadata().getFinishReason()).isEqualTo("incomplete");
        assertThat(result.getResult().getOutput().hasToolCalls()).isFalse();
    }

    @Test
    void incompleteResponseRetainsUsageAndNeverExecutesItsToolCalls() {
        var incomplete = response(List.of(ResponseOutputItem.ofFunctionCall(functionCall())), "incomplete-usage");
        when(incomplete.status()).thenReturn(Optional.of(ResponseStatus.INCOMPLETE));
        withUsage(incomplete, 10, 2);
        when(responseService.create(any(ResponseCreateParams.class))).thenReturn(incomplete);
        var result = new OpenAiResponsesChatModel(openAIClient, new JsonMapper(), "gpt-test").call(new Prompt("work"));
        assertThat(result.getResult().getOutput().hasToolCalls()).isFalse();
        assertThat(result.getResult().getMetadata().getFinishReason()).isEqualTo("incomplete");
        assertThat(result.getMetadata().getUsage().getTotalTokens()).isEqualTo(12);
    }

    @Test
    void overflowingUsageReturnsUnknownTerminalResponseInsteadOfLosingTheCall() {
        var invalid = response(List.of(ResponseOutputItem.ofFunctionCall(functionCall())), "overflowing-usage");
        var usage = com.openai.models.responses.ResponseUsage.builder().inputTokens((long) Integer.MAX_VALUE + 1).outputTokens(2).totalTokens((long) Integer.MAX_VALUE + 3)
                .inputTokensDetails(com.openai.models.responses.ResponseUsage.InputTokensDetails.builder().cachedTokens(0).cacheWriteTokens(0).build())
                .outputTokensDetails(com.openai.models.responses.ResponseUsage.OutputTokensDetails.builder().reasoningTokens(0).build()).build();
        when(invalid.usage()).thenReturn(Optional.of(usage));
        when(responseService.create(any(ResponseCreateParams.class))).thenReturn(invalid);
        var result = new OpenAiResponsesChatModel(openAIClient, new JsonMapper(), "gpt-test").call(new Prompt("work"));
        assertThat(result.getResult().getMetadata().getFinishReason()).isEqualTo("invalid_usage");
        assertThat(result.getResult().getOutput().hasToolCalls()).isFalse();
        assertThat(result.getMetadata().getUsage()).isInstanceOf(org.springframework.ai.chat.metadata.EmptyUsage.class);
    }

    @Test
    void cancelledDispatchNeverContactsProvider() {
        var adapter = new OpenAiResponsesChatModel(openAIClient, new JsonMapper(), "luna");
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
        var adapter = new OpenAiResponsesChatModel(openAIClient, new JsonMapper(), (String) null);
        assertThatThrownBy(() -> adapter.call(new Prompt("work", org.springframework.ai.chat.prompt.ChatOptions.builder().model("luna").build())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requires OpenAiChatOptions");
        assertThatThrownBy(() -> adapter.call(new Prompt("work", OpenAiChatOptions.builder().model("").build()))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a model");
        org.mockito.Mockito.verifyNoInteractions(responseService);
    }

    @ParameterizedTest
    @MethodSource("invalidReplayMessages")
    void rejectsInvalidReplayBeforeDispatch(org.springframework.ai.chat.messages.AssistantMessage message, String error) {
        var adapter = new OpenAiResponsesChatModel(openAIClient, new JsonMapper(), "luna");
        assertThatThrownBy(() -> adapter.call(new Prompt(List.of(message)))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(error);
        org.mockito.Mockito.verifyNoInteractions(responseService);
    }

    private static Stream<Arguments> invalidReplayMessages() {
        return Stream.of(
                Arguments.of(org.springframework.ai.chat.messages.AssistantMessage.builder().content("")
                        .properties(Map.of(OpenAiResponsesChatModel.RESPONSES_OUTPUT_ITEMS_METADATA_KEY, "broken")).build(), "list of output items"),
                Arguments.of(org.springframework.ai.chat.messages.AssistantMessage.builder().content("")
                        .properties(Map.of(OpenAiResponsesChatModel.RESPONSES_OUTPUT_ITEMS_METADATA_KEY, List.of("broken"))).build(), "unsupported output item"),
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
        var adapter = new OpenAiResponsesChatModel(openAIClient, new JsonMapper(), "default-model");
        adapter.call(new Prompt(
                List.of(new org.springframework.ai.chat.messages.AssistantMessage("plain text"),
                        org.springframework.ai.chat.messages.AssistantMessage.builder().content("ignored duplicate")
                                .properties(Map.of(OpenAiResponsesChatModel.RESPONSES_OUTPUT_ITEMS_METADATA_KEY, List.of(ResponseOutputItem.ofMessage(preserved)))).build()),
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
    void replaysToolResponseWithMissingResultAsEmptyOutput() {
        var response = response(List.of(ResponseOutputItem.ofMessage(message("done"))), "tool-response");
        when(responseService.create(any(ResponseCreateParams.class))).thenReturn(response);
        var adapter = new OpenAiResponsesChatModel(openAIClient, new JsonMapper(), "default-model");
        var toolResponse = org.springframework.ai.chat.messages.ToolResponseMessage.builder()
                .responses(List.of(new org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse("call-9", "lookup", null))).build();

        adapter.call(new Prompt(List.of(toolResponse), OpenAiChatOptions.builder().deploymentName("default-model").build()));

        var request = ArgumentCaptor.forClass(ResponseCreateParams.class);
        org.mockito.Mockito.verify(responseService).create(request.capture());
        var input = request.getValue().input().orElseThrow().asResponse();
        assertThat(input).hasSize(1);
        var output = input.getFirst().asFunctionCallOutput();
        assertThat(output.callId()).hasValue("call-9");
        assertThat(output.output().asString()).isEmpty();
    }

    @Test
    void malformedToolSchemaNeverContactsProvider() {
        var callback = mock(ToolCallback.class);
        when(callback.getToolDefinition())
                .thenReturn(org.springframework.ai.tool.definition.ToolDefinition.builder().name("broken").description("broken schema").inputSchema("{").build());
        var adapter = new OpenAiResponsesChatModel(openAIClient, new JsonMapper(), "luna");
        assertThatThrownBy(() -> adapter.call(new Prompt("work", OpenAiChatOptions.builder().toolCallbacks(callback).build()))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JSON schema for tool broken");
        org.mockito.Mockito.verifyNoInteractions(responseService);
    }

    @Test
    void toolResponseWithoutResultIsReplayedAsEmptyOutput() {
        var response = response(List.of(ResponseOutputItem.ofMessage(message("done"))), "tool-response");
        when(responseService.create(any(ResponseCreateParams.class))).thenReturn(response);
        var toolResponses = org.springframework.ai.chat.messages.ToolResponseMessage.builder()
                .responses(List.of(new org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse("call-1", "lookup", null),
                        new org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse("call-2", "lookup", "result")))
                .build();

        new OpenAiResponsesChatModel(openAIClient, new JsonMapper(), "luna").call(new Prompt(List.of(toolResponses)));

        var request = ArgumentCaptor.forClass(ResponseCreateParams.class);
        org.mockito.Mockito.verify(responseService).create(request.capture());
        var input = request.getValue().input().orElseThrow().asResponse();
        assertThat(input).hasSize(2);
        assertThat(input.get(0).asFunctionCallOutput().callId()).hasValue("call-1");
        assertThat(input.get(0).asFunctionCallOutput().output().asString()).isEmpty();
        assertThat(input.get(1).asFunctionCallOutput().output().asString()).isEqualTo("result");
    }

    private static String toolResult(String arguments, ToolContext context, AtomicReference<String> marker) {
        marker.set((String) context.getContext().get("marker"));
        return "result:" + arguments;
    }
}
