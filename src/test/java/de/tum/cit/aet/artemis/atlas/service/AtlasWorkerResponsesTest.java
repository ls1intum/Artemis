package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.ResponsesModel;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.ResponseReasoningItem;
import com.openai.models.responses.ResponseStatus;
import com.openai.models.responses.ResponseUsage;
import com.openai.services.blocking.ResponseService;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.atlas.config.AtlasAgentProperties;
import de.tum.cit.aet.artemis.atlas.config.AtlasOrchestratorProperties;
import de.tum.cit.aet.artemis.atlas.config.AtlasResponsesApiConfiguration.AtlasResponsesChatClient;
import de.tum.cit.aet.artemis.atlas.config.AtlasToolSurface;
import de.tum.cit.aet.artemis.atlas.dto.AppliedActionDTO;

/** Exercises real nested delegation and the native tool advisor against a scripted Responses provider. */
class AtlasWorkerResponsesTest {

    @ParameterizedTest
    @CsvSource({ "Creator,createCompetency,true", "Assigner,assignExerciseToCompetency,false", "Editor,editCompetency,true" })
    void workersShareBudgetButIsolateConversationAndUsage(String role, String mutation, boolean completeUsage) {
        OpenAIClient client = mock(OpenAIClient.class);
        ResponseService provider = mock(ResponseService.class);
        when(client.responses()).thenReturn(provider);
        ResponseReasoningItem parentReasoning = reasoning("parent");
        ResponseReasoningItem workerReasoning = reasoning("worker");
        var scriptedResponses = List.of(
                response("delegate", List.of(ResponseOutputItem.ofReasoning(parentReasoning), call("delegateTo" + role, "{\"task\":\"Apply the requested batch\"}")), true),
                response("worker-read", List.of(ResponseOutputItem.ofReasoning(workerReasoning), call("getExerciseContent", "{}")), completeUsage),
                response("worker-write", List.of(call(mutation, "{}")), true),
                response("worker-complete", List.of(call("completeWorkerTask", "{\"success\":true,\"message\":\"Batch applied\"}")), true),
                response("worker-text", List.of(message("Worker done")), true), response("index", List.of(call("listCompetencyIndex", "{}")), true),
                response("complete", List.of(call("completeOrchestration", "{\"verified\":true,\"message\":\"Verified final mappings\"}")), true));
        var responses = scriptedResponses.iterator();
        when(provider.create(any(ResponseCreateParams.class))).thenAnswer(invocation -> responses.next());
        var templates = mock(AtlasPromptTemplateService.class);
        when(templates.render(anyString(), anyMap())).thenReturn("Worker instructions");
        var usage = mock(LLMTokenUsageService.class);
        var properties = new AtlasOrchestratorProperties("gpt-5.6-luna", 1.0, "xhigh", "gpt-5.6-luna", "high", true, 300, 10, 30000L, 10);
        var harness = new AtlasAgentDelegationService(null, templates, null, new AtlasAgentProperties("interactive", 1.0), properties,
                new AtlasResponsesChatClient(ChatClient.create(new AtlasResponsesChatModel(client, new JsonMapper(), "gpt-5.6-luna"))));
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
                new AtlasToolSurface(MethodToolCallbackProvider.builder().toolObjects(new AtlasWorkerTerminalToolService(new JsonMapper())).build()), properties, usage,
                mock(UserRepository.class));
        var index = FunctionToolCallback.<Map<String, Object>, String>builder("listCompetencyIndex", input -> "{\"competencies\":[]}").inputType(Map.class).build();
        ChatResponse mainResponse = harness.delegateOrchestratorRound("Main instructions", "Process exercise 7",
                OpenAiChatOptions.builder().deploymentName("gpt-5.6-luna").reasoningEffort("xhigh"), context, ToolCallbackProvider.from(index),
                MethodToolCallbackProvider.builder().toolObjects(workers, new AtlasOrchestratorTerminalToolService()).build());

        AtlasToolCallBudget.checkResponse(mainResponse, context);
        assertThat(budget.completion().verified()).isTrue();
        assertThat(budget.completion().message()).isEqualTo("Verified final mappings");
        assertThat(budget.calls()).isEqualTo(6);
        assertThat(budget.activity().stream().filter(entry -> entry.get("role").equals("worker"))).hasSize(3);
        assertThat(actions.actions()).hasSize(1);
        assertThat(mainResponse.getMetadata().getUsage().getTotalTokens()).isEqualTo(36);
        var tracked = ArgumentCaptor.forClass(ChatResponse.class);
        verify(usage).trackChatResponseTokenUsage(tracked.capture(), eq(LLMServiceType.ATLAS), eq("ATLAS_ORCHESTRATION"), any());
        assertThat(tracked.getValue().getMetadata().getUsage().getTotalTokens()).isEqualTo(completeUsage ? 48 : 36);
        assertThat(tracked.getValue().getMetadata().<Boolean>get(AtlasResponsesChatModel.USAGE_COMPLETE_METADATA_KEY)).isEqualTo(completeUsage);
        var requests = ArgumentCaptor.forClass(ResponseCreateParams.class);
        verify(provider, times(7)).create(requests.capture());
        for (int i = 0; i < requests.getAllValues().size(); i++) {
            var request = requests.getAllValues().get(i);
            boolean worker = i >= 1 && i <= 4;
            assertThat(request.model().orElseThrow().asString()).isEqualTo("gpt-5.6-luna");
            assertThat(request.reasoning().orElseThrow().effort().orElseThrow().asString()).isEqualTo(worker ? "high" : "xhigh");
            assertThat(request.store()).hasValue(false);
            assertThat(request.temperature()).isEmpty();
            var toolNames = request.tools().orElseThrow().stream().map(tool -> tool.asFunction().name()).toList();
            if (worker) {
                assertThat(toolNames).containsExactlyInAnyOrder("getExerciseContent", mutation, "completeWorkerTask");
            }
            else {
                assertThat(toolNames).containsExactlyInAnyOrder("listCompetencyIndex", "delegateToCreator", "delegateToAssigner", "delegateToEditor", "completeOrchestration");
            }
        }
        var workerInput = requests.getAllValues().get(2).input().orElseThrow().asResponse();
        assertThat(workerInput.stream().flatMap(item -> item.reasoning().stream())).containsExactly(workerReasoning);
        var parentInput = requests.getAllValues().get(5).input().orElseThrow().asResponse();
        assertThat(parentInput.stream().flatMap(item -> item.reasoning().stream())).containsExactly(parentReasoning);
        assertThat(parentInput.stream().flatMap(item -> item.functionCallOutput().stream()).findFirst().orElseThrow().output().asString()).contains("Batch applied",
                "appliedActions");
    }

    private static ResponseOutputItem call(String name, String arguments) {
        return ResponseOutputItem.ofFunctionCall(ResponseFunctionToolCall.builder().id("fc-" + name).callId("call-" + name).name(name).arguments(arguments)
                .status(ResponseFunctionToolCall.Status.COMPLETED).build());
    }

    private static ResponseReasoningItem reasoning(String id) {
        return ResponseReasoningItem.builder().id("rs-" + id).summary(List.of()).encryptedContent("sealed-" + id).status(ResponseReasoningItem.Status.COMPLETED).build();
    }

    private static ResponseOutputItem message(String text) {
        var output = ResponseOutputText.builder().text(text).annotations(List.of()).build();
        return ResponseOutputItem.ofMessage(ResponseOutputMessage.builder().id("msg").role(JsonValue.from("assistant")).status(ResponseOutputMessage.Status.COMPLETED)
                .content(List.of(ResponseOutputMessage.Content.ofOutputText(output))).build());
    }

    private static Response response(String id, List<ResponseOutputItem> output, boolean knownUsage) {
        var response = mock(Response.class);
        when(response.id()).thenReturn(id);
        when(response.model()).thenReturn(ResponsesModel.ofString("gpt-5.6-luna"));
        when(response.output()).thenReturn(output);
        when(response.status()).thenReturn(Optional.of(ResponseStatus.COMPLETED));
        if (knownUsage) {
            var usage = mock(ResponseUsage.class);
            when(usage.inputTokens()).thenReturn(10L);
            when(usage.outputTokens()).thenReturn(2L);
            when(usage.totalTokens()).thenReturn(12L);
            when(response.usage()).thenReturn(Optional.of(usage));
        }
        return response;
    }
}
