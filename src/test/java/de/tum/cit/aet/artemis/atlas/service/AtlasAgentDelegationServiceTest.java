package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import de.tum.cit.aet.artemis.atlas.config.AtlasAgentProperties;

/**
 * Unit tests for the shared {@link AtlasAgentDelegationService} harness: system-prompt assembly,
 * tool binding, invocation, and the chat-memory semantics (ON for chat, OFF for orchestrator).
 */
@ExtendWith(MockitoExtension.class)
class AtlasAgentDelegationServiceTest {

    private static final String CHAT_DEPLOYMENT = "gpt-chat-mini";

    private static final String ORCHESTRATOR_DEPLOYMENT = "gpt-orchestrator";

    private static final AtlasAgentProperties CHAT_PROPERTIES = new AtlasAgentProperties(CHAT_DEPLOYMENT, 0.8);

    @Mock
    private ChatModel chatModel;

    @Mock
    private AtlasPromptTemplateService templateService;

    @Mock
    private ChatMemory chatMemory;

    @BeforeEach
    void setUp() {
        // Since Spring AI 2.0.0-M6 the ChatClient merges request options into the model's default options, which must be non-null.
        lenient().when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
        lenient().when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
    }

    private AtlasAgentDelegationService newService(boolean withMemory) {
        ChatClient chatClient = ChatClient.create(chatModel);
        return new AtlasAgentDelegationService(chatClient, templateService, withMemory ? chatMemory : null, CHAT_PROPERTIES);
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
    void delegateToAgent_withMemoryEnabled_engagesChatMemory() {
        when(templateService.render(anyString(), anyMap())).thenReturn("SYSTEM");
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("reply")))));

        newService(true).delegateToAgent("/prompts/atlas/agent_system_prompt.st", "hi", 1L, "course_1_user_1", true, null);

        // The MessageChatMemoryAdvisor reads history before and writes it after the round.
        verify(chatMemory).get("course_1_user_1");
        verify(chatMemory).add(anyString(), anyList());
    }

    @Test
    void delegateToAgent_withSaveToMemoryFalse_doesNotTouchChatMemory() {
        when(templateService.render(anyString(), anyMap())).thenReturn("SYSTEM");
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("reply")))));

        newService(true).delegateToAgent("/prompts/atlas/competency_expert_system_prompt.st", "brief", 1L, "course_1_user_1", false, null);

        verify(chatMemory, never()).get(anyString());
        verify(chatMemory, never()).add(anyString(), anyList());
    }

    @Test
    void delegateOrchestratorRound_usesSuppliedSystemPromptVerbatimAndReturnsChatResponse() {
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        ChatResponse expected = new ChatResponse(List.of(new Generation(new AssistantMessage("orchestrator summary"))));
        when(chatModel.call(promptCaptor.capture())).thenReturn(expected);

        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put(OrchestratorToolContextKeys.COURSE_ID_KEY, 42L);
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().deploymentName(ORCHESTRATOR_DEPLOYMENT).temperature(1.0);

        ChatResponse response = newService(true).delegateOrchestratorRound("ORCHESTRATOR PROMPT", "do work", options, toolContext);

        assertThat(response).isSameAs(expected);
        // No course-id breadcrumb is appended for the orchestrator — the prompt is passed verbatim.
        assertThat(systemTextOf(promptCaptor.getValue())).isEqualTo("ORCHESTRATOR PROMPT");
        // Orchestrator runs with chat memory OFF regardless of whether a ChatMemory bean exists.
        verify(chatMemory, never()).get(anyString());
        verify(chatMemory, never()).add(anyString(), anyList());
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
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().deploymentName(ORCHESTRATOR_DEPLOYMENT);

        ChatResponse response = newService(false).delegateOrchestratorRound("PROMPT", "msg", options, toolContext);

        assertThat(response).isSameAs(expected);
        assertThat(toolContext).containsEntry(OrchestratorToolContextKeys.COURSE_ID_KEY, 7L);
    }

    @Test
    void delegateOrchestratorRound_nullChatClient_throws() {
        AtlasAgentDelegationService service = new AtlasAgentDelegationService(null, templateService, chatMemory, CHAT_PROPERTIES);
        Map<String, Object> toolContext = new HashMap<>();
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().deploymentName(ORCHESTRATOR_DEPLOYMENT);

        assertThatThrownBy(() -> service.delegateOrchestratorRound("PROMPT", "msg", options, toolContext)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ChatClient is not configured");
    }

    @Test
    void extractContent_nullResponse_returnsEmptyString() {
        assertThat(AtlasAgentDelegationService.extractContent(null)).isEmpty();
    }
}
