package de.tum.cit.aet.artemis.core.service.feature;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * All classes or methods annotated with this will check (used for controller classes) if the specified feature
 * is enabled. This is done using a custom aspect
 *
 * @see FeatureToggleAspect
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface FeatureToggle {

    /**
     * Set a list of features that should get checked before the endpoint is activated
     *
     * @return All features that should be active for the annotated endpoint
     */
    Feature[] value();
}
