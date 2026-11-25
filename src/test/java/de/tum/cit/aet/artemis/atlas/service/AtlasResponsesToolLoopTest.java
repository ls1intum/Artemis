package de.tum.cit.aet.artemis.atlas.service;

import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.functionCall;
import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.message;
import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.reasoning;
import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.response;
import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.withUsage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.jdbc.JdbcSessionRepository;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import com.openai.client.OpenAIClient;
import com.openai.core.JsonField;
import com.openai.core.JsonValue;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.services.blocking.ResponseService;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesChatModel;

/**
 * Verifies Atlas chat, orchestration, flavor stripping and per-response usage accounting over the Responses adapter.
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

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void interactiveChatReplaysTextFromJdbcAndReadsOnlyRecentHistory(boolean useTool) {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:org/springframework/ai/session/jdbc/schema-h2.sql").build();
        try {
            var jdbc = new HistoryReadCountingJdbcTemplate(database);
            var repository = JdbcSessionRepository.builder().jdbcTemplate(jdbc).build();
            var sessions = DefaultSessionService.builder().sessionRepository(repository).build();
            String sessionId = "course_42_user_7";
            sessions.findOrCreate(CreateSessionRequest.builder().id(sessionId).build());
            for (int turn = 0; turn < 15; turn++) {
                sessions.appendMessage(sessionId, new UserMessage("question " + turn));
                sessions.appendMessage(sessionId, new AssistantMessage("answer " + turn));
            }
            // Exercise a turn with two replies and provider metadata stored by an earlier build.
            String earlierReply = "earlier plan reply";
            sessions.appendMessage(sessionId, AssistantMessage.builder().content(earlierReply)
                    .properties(Map.of(OpenAiResponsesChatModel.RESPONSES_OUTPUT_ITEMS_METADATA_KEY, List.of(ResponseOutputItem.ofMessage(message(earlierReply))))).build());

            var reasoning = reasoning("encrypted-reasoning");
            var toolCall = functionCall();
            var firstReply = response(List.of(ResponseOutputItem.ofMessage(message("first reply"))), "first-reply");
            var followUpReply = response(List.of(ResponseOutputItem.ofMessage(message("follow-up reply"))), "follow-up-reply");
            if (useTool) {
                var toolRequest = response(List.of(ResponseOutputItem.ofReasoning(reasoning), ResponseOutputItem.ofFunctionCall(toolCall)), "tool-call");
                when(responseService.create(any(ResponseCreateParams.class))).thenReturn(toolRequest, firstReply, followUpReply);
            }
            else {
                when(responseService.create(any(ResponseCreateParams.class))).thenReturn(firstReply, followUpReply);
            }

            var templateService = mock(AtlasPromptTemplateService.class);
            when(templateService.render(anyString(), anyMap())).thenReturn("System prompt");
            var client = ChatClient.create(new OpenAiResponsesChatModel(openAIClient, new JsonMapper(), "gpt-test"));
            var service = new AtlasAgentDelegationService(client, client, client, templateService, sessions);
            var lookup = FunctionToolCallback.<Map<String, Object>, String>builder("lookup", input -> "lookup result").inputType(Map.class).build();
            var tools = ToolCallbackProvider.from(lookup);

            assertThat(service.delegateToAgent("prompt", "first question", 42L, sessionId, true, tools)).isEqualTo("first reply");
            assertThat(service.delegateToAgent("prompt", "follow-up question", 42L, sessionId, true, tools)).isEqualTo("follow-up reply");
            // Each request reads 20 events plus the missing user message of the oldest turn.
            assertThat(jdbc.historyReadSizes).containsExactly(20, 1, 20, 1);

            var requests = ArgumentCaptor.forClass(ResponseCreateParams.class);
            verify(responseService, times(useTool ? 3 : 2)).create(requests.capture());
            List<ResponseInputItem> firstInput = requests.getAllValues().getFirst().input().orElseThrow().asResponse();
            assertThat(firstInput).hasSize(23);
            assertThat(firstInput.get(1).asEasyInputMessage().content().asTextInput()).isEqualTo("question 5");
            assertThat(firstInput.get(21).asEasyInputMessage().content().asTextInput()).isEqualTo(earlierReply);
            if (useTool) {
                List<ResponseInputItem> continuation = requests.getAllValues().get(1).input().orElseThrow().asResponse();
                assertThat(continuation.get(23).reasoning()).hasValue(reasoning);
                assertThat(continuation.get(24).functionCall()).hasValue(toolCall);
                assertThat(continuation.getLast().asFunctionCallOutput().callId()).hasValue("call-1");
            }
            List<ResponseInputItem> followUp = requests.getValue().input().orElseThrow().asResponse();
            assertThat(followUp).hasSize(23).allSatisfy(item -> assertThat(item.easyInputMessage()).isPresent());
            assertThat(followUp.get(21).asEasyInputMessage().content().asTextInput()).isEqualTo("first reply");
            assertThat(followUp.getLast().asEasyInputMessage().content().asTextInput()).isEqualTo("follow-up question");

            List<Message> stored = sessions.getMessages(sessionId);
            assertThat(stored).hasSize(35);
            assertThat(stored.get(30).getMetadata()).containsKey(OpenAiResponsesChatModel.RESPONSES_OUTPUT_ITEMS_METADATA_KEY);
            assertThat(stored.subList(31, 35)).extracting(Message::getText).containsExactly("first question", "first reply", "follow-up question", "follow-up reply");
            assertThat(stored.subList(31, 35)).allSatisfy(entry -> assertThat(entry.getMetadata()).doesNotContainKey(OpenAiResponsesChatModel.RESPONSES_OUTPUT_ITEMS_METADATA_KEY));
        }
        finally {
            database.shutdown();
        }
    }

    private static class HistoryReadCountingJdbcTemplate extends JdbcTemplate {

        private final List<Integer> historyReadSizes = new ArrayList<>();

        HistoryReadCountingJdbcTemplate(EmbeddedDatabase database) {
            super(database);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            List<T> rows = super.query(sql, rowMapper, args);
            if (sql.contains("FROM AI_SESSION_EVENT e")) {
                historyReadSizes.add(rows.size());
            }
            return rows;
        }
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
        var result = client.prompt().user("work").tools(callback).call().chatResponse();
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
