package de.tum.cit.aet.artemis.proof.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if the proof module is enabled.
 * Based on this condition, Spring components concerning proof exercise functionality can be enabled or disabled.
 */
public class ProofEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public ProofEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isProofEnabled(context.getEnvironment());
    }
}
