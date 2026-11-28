package de.tum.cit.aet.artemis.atlas.service;

import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.functionCall;
import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.message;
import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.model;
import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.reasoning;
import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.response;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.aiworker.api.LlmChatOptions;
import de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesChatModel;

/**
 * Verifies Atlas chat, orchestration, flavor stripping and per-response usage accounting over the Responses adapter.
 */
class AtlasResponsesToolLoopTest {

    private final Queue<Map<String, Object>> replies = new ArrayDeque<>();

    private final List<JsonNode> requests = new ArrayList<>();

    private final JsonMapper mapper = new JsonMapper();

    private Map<String, Object> respond(Map<String, Object> request) {
        requests.add(mapper.valueToTree(request));
        return replies.remove();
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
                    .properties(Map.of(OpenAiResponsesChatModel.RESPONSES_OUTPUT_ITEMS_METADATA_KEY, List.of(message(earlierReply)))).build());

            var reasoning = reasoning("encrypted-reasoning");
            var toolCall = functionCall();
            var firstReply = response(List.of(message("first reply")), "first-reply");
            var followUpReply = response(List.of(message("follow-up reply")), "follow-up-reply");
            if (useTool) {
                var toolRequest = response(List.of(reasoning, toolCall), "tool-call");
                replies.addAll(List.of(toolRequest, firstReply, followUpReply));
            }
            else {
                replies.addAll(List.of(firstReply, followUpReply));
            }

            var templateService = mock(AtlasPromptTemplateService.class);
            when(templateService.render(anyString(), anyMap())).thenReturn("System prompt");
            var client = ChatClient.create(model("gpt-test", this::respond));
            var service = new AtlasAgentDelegationService(client, client, client, templateService, sessions);
            var lookup = FunctionToolCallback.<Map<String, Object>, String>builder("lookup", input -> "lookup result").inputType(Map.class).build();
            var tools = ToolCallbackProvider.from(lookup);

            assertThat(service.delegateToAgent("prompt", "first question", 42L, sessionId, true, tools)).isEqualTo("first reply");
            assertThat(service.delegateToAgent("prompt", "follow-up question", 42L, sessionId, true, tools)).isEqualTo("follow-up reply");
            // Each request reads 20 events plus the missing user message of the oldest turn.
            assertThat(jdbc.historyReadSizes).containsExactly(20, 1, 20, 1);

            assertThat(requests).hasSize(useTool ? 3 : 2);
            JsonNode firstInput = requests.getFirst().get("input");
            assertThat(firstInput.size()).isEqualTo(23);
            assertThat(firstInput.at("/1/content").asString()).isEqualTo("question 5");
            assertThat(firstInput.at("/21/content").asString()).isEqualTo(earlierReply);
            if (useTool) {
                JsonNode continuation = requests.get(1).get("input");
                assertThat(continuation.get(23)).isEqualTo(mapper.valueToTree(reasoning));
                assertThat(continuation.get(24)).isEqualTo(mapper.valueToTree(toolCall));
                assertThat(continuation.at("/25/call_id").asString()).isEqualTo("call-1");
            }
            JsonNode followUp = requests.getLast().get("input");
            assertThat(followUp.size()).isEqualTo(23);
            assertThat(followUp.valueStream()).allSatisfy(item -> assertThat(item.has("role")).isTrue());
            assertThat(followUp.at("/21/content").asString()).isEqualTo("first reply");
            assertThat(followUp.at("/22/content").asString()).isEqualTo("follow-up question");

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

    private static void addBilledUsage(Map<String, Object> reply, int input, int output, double cost) {
        reply.put("usage", Map.of("input_tokens", input, "output_tokens", output, "total_tokens", input + output, "cost", cost, "cost_currency", "USD"));
    }

    private void runTerminalRound(boolean firstUsageKnown) {
        var readCall = Map.<String, Object>of("type", "function_call", "id", "fc-read", "call_id", "call-read", "name", "listCompetencyIndex", "arguments", "{}", "status",
                "completed");
        var terminalCall = Map.<String, Object>of("type", "function_call", "id", "fc-terminal", "call_id", "call-terminal", "name", "completeOrchestration", "arguments",
                "{\"verified\":true,\"message\":\"Verified the mappings.\"}", "status", "completed");
        var first = response(List.of(readCall), "read");
        var last = response(List.of(terminalCall), "terminal");
        if (firstUsageKnown) {
            addBilledUsage(first, 10, 2, 0.01);
        }
        addBilledUsage(last, 20, 3, 0.02);
        replies.addAll(List.of(first, last));
        // The orchestrator preset carries the model and reasoning effort; the harness passes no per-call model options.
        var adapter = model(LlmChatOptions.builder().model("gpt-5.6-luna").reasoningEffort("xhigh").build(), this::respond);
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
        assertThat(requests).hasSize(2);
        assertThat(requests.getLast().at("/reasoning/effort").asString()).isEqualTo("xhigh");
        assertThat(requests.getLast().has("temperature")).isFalse();
    }

    @Test
    void flavorEditEntityIsParsedThroughResponsesWithLunaHigh() {
        var result = response(List.of(message("{\"edits\":[{\"reason\":\"flavor\",\"search\":\"Alice. \",\"replace\":\"\"}]}")), "edits");
        replies.add(result);
        var parsed = ChatClient.create(model("gpt-5.6-luna", this::respond)).prompt().user("Alice. Calculate 2+3.")
                .options(LlmChatOptions.builder().model("gpt-5.6-luna").reasoningEffort("high")).call().entity(de.tum.cit.aet.artemis.atlas.dto.FlavorStripEditsDTO.class);
        assertThat(parsed.edits()).hasSize(1);
        assertThat(parsed.edits().getFirst().search()).isEqualTo("Alice. ");
        assertThat(requests).hasSize(1);
        assertThat(requests.getLast().get("model").asString()).isEqualTo("gpt-5.6-luna");
        assertThat(requests.getLast().at("/reasoning/effort").asString()).isEqualTo("high");
    }

    @Test
    void unsupportedOutputRetainsProviderCostAndNeverExecutesTools() {
        var unsupported = Map.<String, Object>of("type", "unknown");
        var billed = response(List.of(functionCall(), unsupported), "unsupported-billed");
        addBilledUsage(billed, 10, 2, 0.01);
        replies.add(billed);
        List<ChatResponse> calls = new ArrayList<>();
        var tools = org.springframework.ai.chat.client.advisor.ToolCallingAdvisor.builder().build();
        var client = ChatClient.builder(model("gpt-test", this::respond)).defaultAdvisors(tools, new AtlasTokenUsageAdvisor(calls::add, tools.getOrder())).build();
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
        var billed = response(List.of(functionCall()), "malformed-billed");
        addBilledUsage(billed, 10, 2, 0.01);
        billed.remove(field);
        replies.add(billed);
        List<ChatResponse> calls = new ArrayList<>();
        var tools = org.springframework.ai.chat.client.advisor.ToolCallingAdvisor.builder().build();
        var client = ChatClient.builder(model("requested-model", this::respond)).defaultAdvisors(tools, new AtlasTokenUsageAdvisor(calls::add, tools.getOrder())).build();
        var result = client.prompt().user("work").call().chatResponse();
        assertThat(result.getResult().getMetadata().getFinishReason()).isEqualTo(field.equals("status") ? "missing_status" : "unsupported_output");
        assertThat(result.getResult().getOutput().hasToolCalls()).isFalse();
        assertThat(result.getMetadata().getModel()).isEqualTo(field.equals("id") || field.equals("model") ? "requested-model" : "gpt-test");
        assertThat(calls).hasSize(1);
        var usage = calls.getFirst().getMetadata().getUsage();
        assertThat(de.tum.cit.aet.artemis.aiworker.api.LLMResponseCost.reportedUsd(usage.getNativeUsage(), usage.getPromptTokens(), usage.getCompletionTokens())).isEqualTo(0.01);
    }

    @ParameterizedTest
    @ValueSource(strings = { "input", "output", "total" })
    void missingRequiredUsageCounterReturnsUnknownTerminalResponse(String field) {
        var invalid = response(List.of(functionCall()), "missing-counter");
        var usage = new java.util.HashMap<>(Map.of("input_tokens", 10, "output_tokens", 2, "total_tokens", 12));
        usage.remove(field + "_tokens");
        invalid.put("usage", usage);
        replies.add(invalid);
        List<ChatResponse> calls = new ArrayList<>();
        var tools = org.springframework.ai.chat.client.advisor.ToolCallingAdvisor.builder().build();
        var client = ChatClient.builder(model("gpt-test", this::respond)).defaultAdvisors(tools, new AtlasTokenUsageAdvisor(calls::add, tools.getOrder())).build();
        var result = client.prompt().user("work").call().chatResponse();
        assertThat(result.getResult().getMetadata().getFinishReason()).isEqualTo("invalid_usage");
        assertThat(result.getResult().getOutput().hasToolCalls()).isFalse();
        assertThat(result.getMetadata().getUsage()).isInstanceOf(org.springframework.ai.chat.metadata.EmptyUsage.class);
        assertThat(calls).containsExactly(result);
    }

}
