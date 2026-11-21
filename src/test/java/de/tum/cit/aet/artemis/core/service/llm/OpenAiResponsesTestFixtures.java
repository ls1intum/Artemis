package de.tum.cit.aet.artemis.core.service.llm;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.openai.core.JsonField;
import com.openai.core.JsonValue;
import com.openai.models.ResponsesModel;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.ResponseReasoningItem;
import com.openai.models.responses.ResponseStatus;

/**
 * Mocked OpenAI Responses objects shared by the Responses adapter tests.
 */
public final class OpenAiResponsesTestFixtures {

    private OpenAiResponsesTestFixtures() {
    }

    public static void withUsage(Response response, long input, long output) {
        var usage = mock(com.openai.models.responses.ResponseUsage.class);
        when(usage._inputTokensDetails()).thenReturn(JsonField.ofNullable(null));
        when(usage.inputTokens()).thenReturn(input);
        when(usage.outputTokens()).thenReturn(output);
        when(usage.totalTokens()).thenReturn(input + output);
        when(response.usage()).thenReturn(Optional.of(usage));
    }

    public static Response response(List<ResponseOutputItem> output, String id) {
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

    public static ResponseFunctionToolCall functionCall() {
        return ResponseFunctionToolCall.builder().id("fc-1").type(JsonValue.from("function_call")).callId("call-1").name("lookup").arguments("{\"value\":\"x\"}")
                .status(ResponseFunctionToolCall.Status.COMPLETED).build();
    }

    public static ResponseReasoningItem reasoning(String encryptedContent) {
        return ResponseReasoningItem.builder().id("rs-1").type(JsonValue.from("reasoning")).summary(List.of()).encryptedContent(encryptedContent)
                .status(ResponseReasoningItem.Status.COMPLETED).build();
    }

    public static ResponseOutputMessage message(String text) {
        ResponseOutputText outputText = ResponseOutputText.builder().type(JsonValue.from("output_text")).text(text).annotations(List.of()).build();
        return ResponseOutputMessage.builder().id("msg-1").type(JsonValue.from("message")).role(JsonValue.from("assistant")).status(ResponseOutputMessage.Status.COMPLETED)
                .content(List.of(ResponseOutputMessage.Content.ofOutputText(outputText))).build();
    }
}
