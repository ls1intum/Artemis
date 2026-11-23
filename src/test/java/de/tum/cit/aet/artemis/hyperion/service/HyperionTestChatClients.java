package de.tum.cit.aet.artemis.hyperion.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import org.springframework.ai.chat.client.ChatClient;

import de.tum.cit.aet.artemis.core.config.llm.LlmProperties;
import de.tum.cit.aet.artemis.core.config.llm.LlmTestCatalog;

/**
 * {@link HyperionChatClientService} for unit tests: every task gets the same chat client.
 */
public final class HyperionTestChatClients {

    private HyperionTestChatClients() {
    }

    /**
     * @param chatClient the client every task receives
     * @return task clients that hand out {@code chatClient}
     */
    public static HyperionChatClientService returning(ChatClient chatClient) {
        HyperionChatClientService chatClients = mock(HyperionChatClientService.class);
        lenient().when(chatClients.forTask(any())).thenReturn(chatClient);
        lenient().when(chatClients.presetFor(any())).thenReturn(LlmTestCatalog.preset("test", "test-model", LlmProperties.Api.CHAT_COMPLETIONS));
        return chatClients;
    }
}
