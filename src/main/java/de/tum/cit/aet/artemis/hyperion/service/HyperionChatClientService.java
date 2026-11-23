package de.tum.cit.aet.artemis.hyperion.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.llm.LlmProperties;
import de.tum.cit.aet.artemis.core.service.llm.LlmClientService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.config.HyperionLlmPresets;

/**
 * Hands each Hyperion authoring task the chat client of its preset from {@code artemis.hyperion.llm-presets}.
 */
@Conditional(HyperionEnabled.class)
@Lazy
@Service
public class HyperionChatClientService {

    private final LlmClientService llmClientService;

    private final HyperionLlmPresets presets;

    public HyperionChatClientService(LlmClientService llmClientService, HyperionLlmPresets presets) {
        this.llmClientService = llmClientService;
        this.presets = presets;
    }

    /**
     * @param task a Hyperion task
     * @return the chat client of the task's preset
     */
    public ChatClient forTask(HyperionLlmPresets.Task task) {
        return llmClientService.chatClient(presets.presetFor(task));
    }

    /**
     * @param task a Hyperion task
     * @return the preset assigned to the task
     */
    public LlmProperties.Preset presetFor(HyperionLlmPresets.Task task) {
        return llmClientService.preset(presets.presetFor(task));
    }
}
