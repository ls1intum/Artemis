package de.tum.cit.aet.artemis.modeling.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if Apollon PDF export is enabled.
 * This only controls the PDF export functionality via the external Apollon conversion service.
 * The modeling editor itself (using the Apollon library) is controlled by {@link ModelingEnabled}.
 */
public class ApollonEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public ApollonEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isApollonEnabled(context.getEnvironment());
    }
}
