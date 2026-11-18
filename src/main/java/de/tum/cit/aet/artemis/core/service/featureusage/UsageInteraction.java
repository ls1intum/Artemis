package de.tum.cit.aet.artemis.core.service.featureusage;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;

/**
 * Overrides the interaction the feature usage report derives for an endpoint.
 * <p>
 * By default, a {@code GET} or {@code HEAD} endpoint is a {@link FeatureInteraction#VIEW}, an {@code @Internal} endpoint
 * is {@link FeatureInteraction#SYSTEM} and everything else is an {@link FeatureInteraction#ACTION}. That is right for most
 * endpoints, and this annotation marks the exceptions:
 * <ul>
 * <li>{@link FeatureInteraction#AUTOMATIC} for an endpoint the client calls on its own, such as a status probe, a badge
 * or a title lookup. Without it, a poll on a frequently visited page makes the feature look heavily used.</li>
 * <li>{@link FeatureInteraction#VIEW} for a {@code POST} that only reads, such as a search or a preview.</li>
 * <li>{@link FeatureInteraction#ACTION} for a {@code GET} whose purpose is a side effect, such as starting an exam or
 * locking a submission for assessment.</li>
 * <li>{@link FeatureInteraction#SYSTEM} for an endpoint that another system calls.</li>
 * </ul>
 * <p>
 * Like {@link FeatureUsage}, this never affects behaviour, and changing it is safe at any time: the interaction is stored
 * on the endpoint's inventory row and re-derived on every startup, so historic counts regroup immediately.
 * <p>
 * {@code FeatureUsageAnnotationTest} rejects an override that repeats the derived value, so every occurrence is a
 * deliberate exception.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UsageInteraction {

    /**
     * The interaction to report the annotated endpoint as.
     *
     * @return the interaction that overrides the derived one
     */
    FeatureInteraction value();
}
