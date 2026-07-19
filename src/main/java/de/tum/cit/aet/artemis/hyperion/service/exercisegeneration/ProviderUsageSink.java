package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.util.function.Consumer;

import org.springframework.ai.chat.model.ChatResponse;

/** Records successful provider usage and marks calls whose billing outcome cannot be determined from a response. */
public interface ProviderUsageSink extends Consumer<ChatResponse> {

    /** Marks a provider call as potentially billable even though no usage response was received. */
    void markUncertain();
}
