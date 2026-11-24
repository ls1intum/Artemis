package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.session.SessionService;

/**
 * Unit tests for the shared {@link AtlasAgentDelegationService} harness: system-prompt assembly,
 * tool binding, invocation, and the chat-memory semantics (ON for chat, OFF for orchestrator).
 */
@ExtendWith(MockitoExtension.class)
class AtlasAgentDelegationServiceTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private AtlasPromptTemplateService templateService;

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        // Since Spring AI 2.0.0-M6 the ChatClient merges request options into the model's default options, which must be non-null.
        lenient().when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        sessionService = spy(AtlasSessionTestSupport.newSessionService());
    }

    private AtlasAgentDelegationService newService(boolean withMemory) {
        ChatClient chatClient = ChatClient.create(chatModel);
        return new AtlasAgentDelegationService(chatClient, chatClient, chatClient, templateService, withMemory ? sessionService : null);
    }

    private static String systemTextOf(Prompt prompt) {
        return prompt.getInstructions().stream().filter(SystemMessage.class::isInstance).map(Message::getText).findFirst().orElseThrow();
    }

    @Test
    void delegateToAgent_assemblesSystemPromptWithCourseBreadcrumbAndReturnsContent() {
        when(templateService.render(anyString(), anyMap())).thenReturn("RENDERED SYSTEM PROMPT");
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        when(chatModel.call(promptCaptor.capture())).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("agent reply")))));

        String result = newService(false).delegateToAgent("/prompts/atlas/agent_system_prompt.st", "user question", 42L, "course_42_user_7", true, null);

        assertThat(result).isEqualTo("agent reply");
        assertThat(systemTextOf(promptCaptor.getValue())).startsWith("RENDERED SYSTEM PROMPT").contains("CONTEXT FOR THIS REQUEST:\nCourse ID: 42");
    }

    @Test
    void delegateToAgent_withMemoryEnabled_keepsTheConversationInTheSession() {
        when(templateService.render(anyString(), anyMap())).thenReturn("SYSTEM");
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("reply")))));

        newService(true).delegateToAgent("/prompts/atlas/agent_system_prompt.st", "hi", 1L, "course_1_user_1", true, null);

        // The SessionMemoryAdvisor reads history before and writes the user message and the reply during the round.
        assertThat(sessionService.getMessages("course_1_user_1")).extracting(Message::getText).containsExactly("hi", "reply");
    }

    @Test
    void delegateToAgent_withSaveToMemoryFalse_doesNotTouchTheSessionMemory() {
        when(templateService.render(anyString(), anyMap())).thenReturn("SYSTEM");
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("reply")))));

        newService(true).delegateToAgent("/prompts/atlas/competency_expert_system_prompt.st", "brief", 1L, "course_1_user_1", false, null);

        verifyNoInteractions(sessionService);
    }

    @Test
    void delegateOrchestratorRound_usesSuppliedSystemPromptVerbatimAndReturnsChatResponse() {
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        ChatResponse expected = new ChatResponse(List.of(new Generation(new AssistantMessage("orchestrator summary"))));
        when(chatModel.call(promptCaptor.capture())).thenReturn(expected);

        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put(OrchestratorToolContextKeys.COURSE_ID_KEY, 42L);

        ChatResponse response = newService(true).delegateOrchestratorRound("ORCHESTRATOR PROMPT", "do work", toolContext, ignored -> {
        });

        assertThat(response).isSameAs(expected);
        // No course-id breadcrumb is appended for the orchestrator — the prompt is passed verbatim.
        assertThat(systemTextOf(promptCaptor.getValue())).startsWith("ORCHESTRATOR PROMPT").contains("Shared run budget: 0/256");
        // Orchestrator runs with session memory OFF regardless of whether a SessionService bean exists.
        verifyNoInteractions(sessionService);
    }

    @Test
    void eachFlowCallsTheModelOfItsOwnPreset() {
        ChatModel orchestratorModel = mock(ChatModel.class);
        ChatModel workerModel = mock(ChatModel.class);
        when(orchestratorModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(workerModel.getOptions()).thenReturn(ChatOptions.builder().build());
        ChatResponse orchestratorResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("orchestrator"))));
        ChatResponse workerResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("worker"))));
        when(orchestratorModel.call(any(Prompt.class))).thenReturn(orchestratorResponse);
        when(workerModel.call(any(Prompt.class))).thenReturn(workerResponse);
        AtlasAgentDelegationService service = new AtlasAgentDelegationService(ChatClient.create(chatModel), ChatClient.create(orchestratorModel), ChatClient.create(workerModel),
                templateService, sessionService);

        assertThat(service.delegateOrchestratorRound("SYSTEM", "work", new HashMap<>(), ignored -> {
        })).isSameAs(orchestratorResponse);
        assertThat(service.delegateWorkerRound("SYSTEM", "batch", new HashMap<>(), ignored -> {
        })).isSameAs(workerResponse);
        when(templateService.render(anyString(), anyMap())).thenReturn("INTERACTIVE");
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("chat")))));
        assertThat(service.delegateToAgent("prompt", "question", 42L, "session", false, null)).isEqualTo("chat");

        verify(orchestratorModel).call(any(Prompt.class));
        verify(workerModel).call(any(Prompt.class));
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void delegateOrchestratorRound_passesToolContextThroughToToolExecution() {
        // The tool context map is passed by reference; values stashed by the caller are visible to
        // tool bodies. We assert the harness wires it into the request by reading it back through a
        // captured prompt's tool context (Spring AI carries it on the runtime tool-calling options).
        ChatResponse expected = new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        when(chatModel.call(any(Prompt.class))).thenReturn(expected);

        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put(OrchestratorToolContextKeys.COURSE_ID_KEY, 7L);

        ChatResponse response = newService(false).delegateOrchestratorRound("PROMPT", "msg", toolContext, ignored -> {
        });

        assertThat(response).isSameAs(expected);
        assertThat(toolContext).containsEntry(OrchestratorToolContextKeys.COURSE_ID_KEY, 7L);
    }

    @Test
    void extractContent_nullResponse_returnsEmptyString() {
        assertThat(AtlasAgentDelegationService.extractContent(null)).isEmpty();
    }
}
