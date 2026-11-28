package de.tum.cit.aet.artemis.atlas.service;

import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.message;
import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.model;
import static de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures.reasoning;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.aiworker.api.LlmChatOptions;
import de.tum.cit.aet.artemis.atlas.config.AtlasToolSurface;
import de.tum.cit.aet.artemis.atlas.dto.AppliedActionDTO;
import de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures;

/** Exercises real nested delegation and the native tool advisor against a scripted Responses provider. */
class AtlasWorkerResponsesTest {

    @ParameterizedTest
    @CsvSource({ "Creator,createCompetency,true", "Assigner,assignExerciseToCompetency,false", "Editor,editCompetency,true" })
    void workersShareBudgetButIsolateConversationAndUsage(String role, String mutation, boolean completeUsage) {
        var mapper = new JsonMapper();
        var parentReasoning = reasoning("parent");
        var workerReasoning = reasoning("worker");
        var scriptedResponses = List.of(response("delegate", List.of(parentReasoning, call("delegateTo" + role, "{\"task\":\"Apply the requested batch\"}")), true),
                response("worker-read", List.of(workerReasoning, call("getExerciseContent", "{}")), completeUsage), response("worker-write", List.of(call(mutation, "{}")), true),
                response("worker-complete", List.of(call("completeWorkerTask", "{\"success\":true,\"message\":\"Batch applied\"}")), true),
                response("worker-text", List.of(message("Worker done")), true), response("index", List.of(call("listCompetencyIndex", "{}")), true),
                response("complete", List.of(call("completeOrchestration", "{\"verified\":true,\"message\":\"Verified final mappings\"}")), true));
        var responses = scriptedResponses.iterator();
        List<JsonNode> requests = new ArrayList<>();
        Function<Map<String, Object>, Map<String, Object>> provider = request -> {
            requests.add(mapper.valueToTree(request));
            return responses.next();
        };
        var templates = mock(AtlasPromptTemplateService.class);
        when(templates.render(anyString(), anyMap())).thenReturn("Worker instructions");
        var usage = mock(LLMTokenUsageService.class);
        // the orchestrator and worker presets differ only in their reasoning effort, which each role's model carries as its default
        var orchestratorClient = ChatClient.create(model(LlmChatOptions.builder().model("gpt-5.6-luna").reasoningEffort("xhigh").build(), provider));
        var workerClient = ChatClient.create(model(LlmChatOptions.builder().model("gpt-5.6-luna").reasoningEffort("high").build(), provider));
        var harness = new AtlasAgentDelegationService(orchestratorClient, orchestratorClient, workerClient, templates, null);
        Map<String, Object> context = new HashMap<>();
        context.put(OrchestratorToolContextKeys.COURSE_ID_KEY, 42L);
        context.put(OrchestratorToolContextKeys.LEARNING_OBJECT_ID_KEY, 7L);
        context.put(OrchestratorToolContextKeys.DELEGATION_COUNT_KEY, new AtomicInteger());
        var actions = new OrchestratorToolContextKeys.AppliedActionsBuffer(Collections.synchronizedList(new ArrayList<>()));
        context.put(OrchestratorToolContextKeys.APPLIED_ACTIONS_KEY, actions);
        var budget = AtlasToolCallBudget.budgetForContext(context);
        var read = FunctionToolCallback.<Map<String, Object>, String>builder("getExerciseContent", (input, worker) -> {
            assertThat(AtlasToolCallBudget.existingBudget(worker.getContext())).isSameAs(budget);
            assertThat(worker.getContext().get(OrchestratorToolContextKeys.APPLIED_ACTIONS_KEY)).isSameAs(actions);
            assertThat(worker.getContext()).containsEntry(OrchestratorToolContextKeys.COURSE_ID_KEY, 42L).containsEntry(OrchestratorToolContextKeys.LEARNING_OBJECT_ID_KEY, 7L);
            OrchestratorToolHelpers.markWorkerToolActivity(worker);
            OrchestratorToolHelpers.markWorkerRead(worker);
            return "Exercise evidence";
        }).inputType(Map.class).build();
        var write = FunctionToolCallback.<Map<String, Object>, String>builder(mutation, (input, worker) -> {
            OrchestratorToolHelpers.markWorkerToolActivity(worker);
            OrchestratorToolHelpers.appendAction(worker, AppliedActionDTO.edit(9L, "Loops", "Updated competency", "Exercise evidence"));
            return "Applied";
        }).inputType(Map.class).build();
        var roleTools = new AtlasToolSurface(ToolCallbackProvider.from(write));
        var empty = new AtlasToolSurface(ToolCallbackProvider.from(List.of()));
        var workers = new OrchestratorDelegationToolsService(templates, harness, new AtlasToolSurface(ToolCallbackProvider.from(read)), role.equals("Creator") ? roleTools : empty,
                role.equals("Assigner") ? roleTools : empty, role.equals("Editor") ? roleTools : empty,
                new AtlasToolSurface(MethodToolCallbackProvider.builder().toolObjects(new AtlasWorkerTerminalToolService(new JsonMapper())).build()), usage,
                mock(UserRepository.class));
        var index = FunctionToolCallback.<Map<String, Object>, String>builder("listCompetencyIndex", input -> "{\"competencies\":[]}").inputType(Map.class).build();
        ChatResponse mainResponse = harness.delegateOrchestratorRound("Main instructions", "Process exercise 7", context, ignored -> {
        }, ToolCallbackProvider.from(index), MethodToolCallbackProvider.builder().toolObjects(workers, new AtlasOrchestratorTerminalToolService()).build());

        AtlasToolCallBudget.checkResponse(mainResponse, context);
        assertThat(budget.completion().verified()).isTrue();
        assertThat(budget.completion().message()).isEqualTo("Verified final mappings");
        assertThat(budget.calls()).isEqualTo(6);
        assertThat(budget.activity().stream().filter(entry -> entry.get("role").equals("worker"))).hasSize(3);
        assertThat(actions.actions()).hasSize(1);
        assertThat(mainResponse.getMetadata().getUsage().getTotalTokens()).isEqualTo(36);
        var tracked = ArgumentCaptor.forClass(ChatResponse.class);
        verify(usage, times(4)).trackModelCallTokenUsage(tracked.capture(), eq(LLMServiceType.ATLAS), eq("ATLAS_ORCHESTRATION"), any());
        assertThat(tracked.getAllValues()).hasSize(4);
        if (completeUsage) {
            assertThat(tracked.getAllValues().getFirst().getMetadata().getUsage().getPromptTokens()).isEqualTo(10);
            assertThat(tracked.getAllValues().getFirst().getMetadata().getUsage().getCompletionTokens()).isEqualTo(2);
        }
        else {
            assertThat(tracked.getAllValues().getFirst().getMetadata().getUsage()).isInstanceOf(org.springframework.ai.chat.metadata.EmptyUsage.class);
        }
        for (ChatResponse response : tracked.getAllValues().subList(1, tracked.getAllValues().size())) {
            assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(10);
            assertThat(response.getMetadata().getUsage().getCompletionTokens()).isEqualTo(2);
        }
        verifyNoMoreInteractions(usage);
        assertThat(requests).hasSize(7);
        for (int i = 0; i < requests.size(); i++) {
            var request = requests.get(i);
            boolean worker = i >= 1 && i <= 4;
            assertThat(request.get("model").asString()).isEqualTo("gpt-5.6-luna");
            assertThat(request.at("/reasoning/effort").asString()).isEqualTo(worker ? "high" : "xhigh");
            assertThat(request.get("store").asBoolean()).isFalse();
            assertThat(request.has("temperature")).isFalse();
            var toolNames = request.get("tools").valueStream().map(tool -> tool.get("name").asString()).toList();
            if (worker) {
                assertThat(toolNames).containsExactlyInAnyOrder("getExerciseContent", mutation, "completeWorkerTask");
            }
            else {
                assertThat(toolNames).containsExactlyInAnyOrder("listCompetencyIndex", "delegateToCreator", "delegateToAssigner", "delegateToEditor", "completeOrchestration");
            }
        }
        var workerInput = requests.get(2).get("input");
        assertThat(workerInput.valueStream().filter(item -> "reasoning".equals(item.path("type").asString()))).containsExactly(mapper.valueToTree(workerReasoning));
        var parentInput = requests.get(5).get("input");
        assertThat(parentInput.valueStream().filter(item -> "reasoning".equals(item.path("type").asString()))).containsExactly(mapper.valueToTree(parentReasoning));
        assertThat(parentInput.valueStream().filter(item -> "function_call_output".equals(item.path("type").asString())).findFirst().orElseThrow().get("output").asString())
                .contains("Batch applied", "appliedActions");
    }

    private static Map<String, Object> call(String name, String arguments) {
        return Map.of("type", "function_call", "id", "fc-" + name, "call_id", "call-" + name, "name", name, "arguments", arguments, "status", "completed");
    }

    private static Map<String, Object> response(String id, List<Map<String, Object>> output, boolean knownUsage) {
        var reply = OpenAiResponsesTestFixtures.response(output, id);
        reply.put("model", "gpt-5.6-luna");
        if (knownUsage) {
            OpenAiResponsesTestFixtures.withUsage(reply, 10, 2);
        }
        return reply;
    }
}
