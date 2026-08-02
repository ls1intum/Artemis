package de.tum.cit.aet.artemis.buildagent.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Enables build-agent-side generation sandbox hosting only when the agent has configured positive capacity. */
public class GenerationSandboxHostingEnabled implements Condition {

    /** The single opt-in for build-agent-side generation hosting. Public so every diagnostic that reports missing capacity names the exact property an admin has to set. */
    public static final String MAX_GENERATION_SANDBOX_SLOTS_PROPERTY = "artemis.continuous-integration.build-agent.max-generation-sandbox-slots";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        int slots = context.getEnvironment().getProperty(MAX_GENERATION_SANDBOX_SLOTS_PROPERTY, Integer.class, 0);
        if (slots < 0) {
            throw new IllegalArgumentException(MAX_GENERATION_SANDBOX_SLOTS_PROPERTY + " must not be negative");
        }
        return slots > 0;
    }
}
