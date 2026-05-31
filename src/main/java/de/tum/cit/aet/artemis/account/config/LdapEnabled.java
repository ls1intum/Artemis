package de.tum.cit.aet.artemis.account.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if LDAP-based user synchronization is enabled.
 * Based on this condition, Spring components concerning the external LDAP system can be enabled or disabled.
 */
public class LdapEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public LdapEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isLdapEnabled(context.getEnvironment());
    }
}
