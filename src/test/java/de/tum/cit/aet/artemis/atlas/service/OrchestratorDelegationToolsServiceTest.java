package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;

import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.atlas.config.AtlasOrchestratorProperties;
import de.tum.cit.aet.artemis.atlas.config.AtlasToolSurface;
import de.tum.cit.aet.artemis.atlas.dto.AppliedActionDTO;
import de.tum.cit.aet.artemis.atlas.dto.WorkerCompletionDTO;
import de.tum.cit.aet.artemis.atlas.dto.WorkerResultDTO;

@ExtendWith(MockitoExtension.class)
class OrchestratorDelegationToolsServiceTest {

    private static final long COURSE_ID = 42L;

    @Mock
    private AtlasPromptTemplateService templateService;

    @Mock
    private AtlasAgentDelegationService delegationService;

    @Mock
    private ToolCallbackProvider readTools;

    @Mock
    private ToolCallbackProvider creatorTools;

    @Mock
    private ToolCallbackProvider assignerTools;

    @Mock
    private ToolCallbackProvider editorTools;

    @Mock
    private ToolCallbackProvider terminalTools;

    @Mock
    private LLMTokenUsageService llmTokenUsageService;

    @Mock
    private UserTestRepository userRepository;

    private OrchestratorDelegationToolsService service;

    @BeforeEach
    void setUp() {
        AtlasOrchestratorProperties properties = new AtlasOrchestratorProperties("gpt-5.6-luna", 1.0, "xhigh", "gpt-5.6-luna", "high", 300, 10, 30000L, 10);
        service = new OrchestratorDelegationToolsService(templateService, delegationService, new AtlasToolSurface(readTools), new AtlasToolSurface(creatorTools),
                new AtlasToolSurface(assignerTools), new AtlasToolSurface(editorTools), new AtlasToolSurface(terminalTools), properties, llmTokenUsageService, userRepository);
        lenient().when(templateService.render(anyString(), anyMap())).thenReturn("worker system prompt");
    }

    @Test
    void delegateToCreator_returnsOnlyItsActionSliceAndUsesWorkerModelOptions() {
        Map<String, Object> parent = parentContext();
        OrchestratorToolContextKeys.AppliedActionsBuffer buffer = buffer(parent);
        buffer.actions().add(AppliedActionDTO.edit(1L, "Existing", "Earlier edit", "Earlier worker"));
        ChatResponse response = response("worker response");
        when(delegationService.delegateOrchestratorRound(anyString(), anyString(), any(OpenAiChatOptions.Builder.class), anyMap(), any(ToolCallbackProvider.class),
                any(ToolCallbackProvider.class), any(ToolCallbackProvider.class))).thenAnswer(invocation -> {
                    Map<String, Object> workerContext = invocation.getArgument(3);
                    buffer(workerContext).actions().add(AppliedActionDTO.create(2L, "Loops", "Created competency", "Exercise teaches loops"));
                    workerHolder(workerContext).set(new WorkerCompletionDTO(true, "Created the requested competency"));
                    return response;
                });

        WorkerResultDTO result = service.delegateToCreator("Create the missing loops competency", new ToolContext(parent));

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Created the requested competency");
        assertThat(result.appliedActions()).singleElement().extracting(AppliedActionDTO::type).isEqualTo(AppliedActionDTO.ActionType.CREATE);
        assertThat(((AtomicLong) parent.get(OrchestratorToolContextKeys.LAST_DELEGATION_SEQUENCE_KEY)).get()).isPositive();
        verify(llmTokenUsageService).trackChatResponseTokenUsage(eq(response), eq(LLMServiceType.ATLAS), eq("ATLAS_ORCHESTRATION"), any());

        ArgumentCaptor<OpenAiChatOptions.Builder> optionsCaptor = ArgumentCaptor.forClass(OpenAiChatOptions.Builder.class);
        ArgumentCaptor<ToolCallbackProvider> providerCaptor = ArgumentCaptor.forClass(ToolCallbackProvider.class);
        verify(delegationService).delegateOrchestratorRound(anyString(), anyString(), optionsCaptor.capture(), anyMap(), providerCaptor.capture(), providerCaptor.capture(),
                providerCaptor.capture());
        OpenAiChatOptions options = optionsCaptor.getValue().build();
        assertThat(options.getDeploymentName()).isEqualTo("gpt-5.6-luna");
        assertThat(options.getReasoningEffort()).isEqualTo("high");
        assertThat(options.getTemperature()).isNull();
        assertThat(providerCaptor.getAllValues()).containsExactly(readTools, creatorTools, terminalTools);
    }

    @Test
    void delegateToAssigner_missingTerminalReturnsStructuredFailure() {
        ChatResponse response = response("forgot terminal");
        when(delegationService.delegateOrchestratorRound(anyString(), anyString(), any(OpenAiChatOptions.Builder.class), anyMap(), any(ToolCallbackProvider.class),
                any(ToolCallbackProvider.class), any(ToolCallbackProvider.class))).thenReturn(response);

        WorkerResultDTO result = service.delegateToAssigner("Assign exercise 7 to competency 9", new ToolContext(parentContext()));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("without calling completeWorkerTask");
        assertThat(result.appliedActions()).isEmpty();
    }

    @Test
    void delegateToEditor_nestedExceptionAfterActionReturnsStructuredFailureWithActionSlice() {
        Map<String, Object> parent = parentContext();
        when(delegationService.delegateOrchestratorRound(anyString(), anyString(), any(OpenAiChatOptions.Builder.class), anyMap(), any(ToolCallbackProvider.class),
                any(ToolCallbackProvider.class), any(ToolCallbackProvider.class))).thenAnswer(invocation -> {
                    Map<String, Object> workerContext = invocation.getArgument(3);
                    buffer(workerContext).actions().add(AppliedActionDTO.delete(8L, "Obsolete", "Deleted competency", "No remaining evidence"));
                    throw new IllegalStateException("nested tool loop failed");
                });

        WorkerResultDTO result = service.delegateToEditor("Delete competency 8 after links were moved", new ToolContext(parent));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Editor worker failed while executing its batch.");
        assertThat(result.appliedActions()).singleElement().extracting(AppliedActionDTO::type).isEqualTo(AppliedActionDTO.ActionType.DELETE);
        assertThat(((AtomicLong) parent.get(OrchestratorToolContextKeys.LAST_DELEGATION_SEQUENCE_KEY)).get()).isPositive();
    }

    @Test
    void blankTaskFailsWithoutCallingWorker() {
        WorkerResultDTO result = service.delegateToCreator("  ", new ToolContext(parentContext()));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("must not be blank");
        verify(delegationService, org.mockito.Mockito.never()).delegateOrchestratorRound(anyString(), anyString(), any(OpenAiChatOptions.Builder.class), anyMap(),
                any(ToolCallbackProvider.class), any(ToolCallbackProvider.class), any(ToolCallbackProvider.class));
    }

    private static Map<String, Object> parentContext() {
        Map<String, Object> context = new HashMap<>();
        context.put(OrchestratorToolContextKeys.COURSE_ID_KEY, COURSE_ID);
        context.put(OrchestratorToolContextKeys.LEARNING_OBJECT_ID_KEY, 7L);
        context.put(OrchestratorToolContextKeys.APPLIED_ACTIONS_KEY, new OrchestratorToolContextKeys.AppliedActionsBuffer(Collections.synchronizedList(new ArrayList<>())));
        context.put(OrchestratorToolContextKeys.TOOL_SEQUENCE_KEY, new AtomicLong());
        context.put(OrchestratorToolContextKeys.LAST_DELEGATION_SEQUENCE_KEY, new AtomicLong());
        return context;
    }

    private static OrchestratorToolContextKeys.AppliedActionsBuffer buffer(Map<String, Object> context) {
        return (OrchestratorToolContextKeys.AppliedActionsBuffer) context.get(OrchestratorToolContextKeys.APPLIED_ACTIONS_KEY);
    }

    @SuppressWarnings("unchecked")
    private static AtomicReference<WorkerCompletionDTO> workerHolder(Map<String, Object> context) {
        return (AtomicReference<WorkerCompletionDTO>) context.get(OrchestratorToolContextKeys.WORKER_COMPLETION_KEY);
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
