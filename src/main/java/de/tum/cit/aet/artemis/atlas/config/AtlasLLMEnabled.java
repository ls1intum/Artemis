package de.tum.cit.aet.artemis.atlas.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if the LLM-backed part of Atlas is enabled.
 * <p>
 * It covers the chat agent, the autonomous competency orchestrator, and the tool surfaces they call: everything that
 * needs a configured chat model to do anything at all. Competencies, learning paths and learner profiles are the Atlas
 * module itself and stay on {@link AtlasEnabled}, so an installation without an LLM keeps all of them.
 */
public class AtlasLLMEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public AtlasLLMEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isAtlasLLMEnabled(context.getEnvironment());
    }
}
