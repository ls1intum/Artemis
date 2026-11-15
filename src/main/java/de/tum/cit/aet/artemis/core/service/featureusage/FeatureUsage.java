package de.tum.cit.aet.artemis.core.service.featureusage;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Assigns endpoints to the user-facing feature they serve, for the admin feature usage page.
 * <p>
 * Required on every REST controller, enforced by {@code FeatureUsageAnnotationTest}. Tracking does not depend on it:
 * every endpoint is recorded either way, listed by its HTTP verb and canonical path. What the annotation adds is
 * meaning. It names the feature the way users know it, from the {@link UserFeature} catalogue, and collapses the endpoints
 * that make up one feature into a single row, even when they live in several modules. A new controller cannot slip in
 * without someone deciding which feature it belongs to.
 * <p>
 * The module is <b>not</b> part of this annotation. It is derived from the controller's package, which the
 * {@code api/<module>/} path convention already guarantees to be right, and shown one level below the feature.
 * <p>
 * On a method the annotation assigns that endpoint. On a controller it assigns every endpoint in it, and a method level
 * annotation wins over the class level one, so a controller that serves several features splits them per method. Because
 * the feature is only a grouping attribute on a row keyed by endpoint, reassigning is safe at any time: historic counters
 * regroup under the new feature immediately and no endpoint detail is lost.
 * <p>
 * How a call is counted, as an action, a view, an automatic call or a system call, is a separate question answered by the
 * HTTP verb and, where the verb is misleading, by {@link UsageInteraction}.
 * <p>
 * Not to be confused with {@code @FeatureToggle}, which is a kill switch that decides whether an endpoint may be called
 * at all. This annotation never affects behaviour.
 *
 * <pre>
 * &#64;FeatureUsage(UserFeature.PROGRAMMING_GRADING_CONFIGURATION)
 * &#64;PatchMapping("programming-exercises/{exerciseId}/static-code-analysis-categories")
 * public ResponseEntity&lt;Set&lt;StaticCodeAnalysisCategory&gt;&gt; updateStaticCodeAnalysisCategories(...)
 * </pre>
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface FeatureUsage {

    /**
     * The feature the annotated endpoints serve.
     *
     * @return the feature under which the annotated endpoints are reported
     */
    UserFeature value();
}
