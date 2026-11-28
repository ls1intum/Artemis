package de.tum.cit.aet.artemis.core.config.llm;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_AIWORKER;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Loads the LLM catalog and clients on core nodes, and on AI Worker nodes only for the {@code hyperion-generation} workload, the one worker workload that
 * calls a model. Other workloads get no model client, even when they inherit the server configuration.
 */
public class LlmEnabled implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment environment = context.getEnvironment();
        if (environment.acceptsProfiles(Profiles.of(PROFILE_CORE))) {
            return true;
        }
        return environment.acceptsProfiles(Profiles.of(PROFILE_AIWORKER)) && "hyperion-generation".equals(environment.getProperty("artemis.aiworker.workload"));
    }
}
