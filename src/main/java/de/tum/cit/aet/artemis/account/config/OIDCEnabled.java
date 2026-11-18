package de.tum.cit.aet.artemis.account.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if OIDC-based single sign-on is enabled.
 * Based on this condition, Spring components concerning OIDC authentication can be enabled or disabled.
 */
public class OIDCEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public OIDCEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return this.artemisConfigHelper.isOIDCEnabled(context.getEnvironment());
    }

}
