package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * The model that may take over one stalled repair block, resolved from the run's repair LLM preset. It brings its own endpoint, options and price, so its calls are accounted
 * for under that preset rather than the primary one.
 *
 * @param preset    the name of the repair LLM preset
 * @param chatModel the preset's chat model
 * @param options   the options every request of the handoff starts from
 */
public record RepairModel(String preset, ChatModel chatModel, ChatOptions options) {
}
