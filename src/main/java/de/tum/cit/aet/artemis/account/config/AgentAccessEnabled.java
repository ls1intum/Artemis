package de.tum.cit.aet.artemis.account.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if agent access (personal access tokens and the endpoints built on them) is enabled.
 * It is off unless {@code artemis.agent-access.enabled} is set to {@code true}, so that an instance has to choose it.
 */
public class AgentAccessEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public AgentAccessEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isAgentAccessEnabled(context.getEnvironment());
    }
}
