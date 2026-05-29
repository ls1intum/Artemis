package de.tum.cit.aet.artemis.account.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if SAML2-based single sign-on is enabled.
 * Based on this condition, Spring components concerning SAML2 authentication can be enabled or disabled.
 */
public class Saml2Enabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public Saml2Enabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isSaml2Enabled(context.getEnvironment());
    }
}
