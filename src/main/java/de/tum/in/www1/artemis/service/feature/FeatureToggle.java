package de.tum.in.www1.artemis.service.feature;

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

    Feature[] value();
}
