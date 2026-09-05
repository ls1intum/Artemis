package de.tum.cit.aet.artemis.core.service.featureusage;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Groups one or more endpoints under a named logical feature on the admin feature usage page.
 * <p>
 * Required on every REST controller, enforced by {@code FeatureUsageAnnotationTest}. Tracking does not depend on it:
 * every endpoint is recorded either way, listed by its HTTP verb and canonical path. What the annotation adds is
 * meaning. It names a feature the way the team talks about it and collapses the several endpoints that make up one
 * feature into a single row, so a new controller cannot slip in without someone deciding which feature it belongs to.
 * <p>
 * The label is {@code area/feature} in kebab-case with exactly one slash, since the admin page splits on it to build
 * the tree. The module is <b>not</b> part of this annotation. It is derived from the controller's package, which the
 * {@code api/<module>/} path convention already guarantees to be right, so there is nothing here that can drift out of
 * sync with reality.
 * <p>
 * On a method the annotation labels that endpoint. On a controller it labels every endpoint in it, and a method level
 * annotation wins over the class level one. Because the label is only a grouping attribute on a row keyed by endpoint,
 * renaming or regrouping is safe at any time: historic counters regroup under the new label immediately and no endpoint
 * detail is lost. What the build rejects is a controller carrying no label at all, not a change to an existing one.
 * <p>
 * Not to be confused with {@code @FeatureToggle}, which is a kill switch that decides whether an endpoint may be called
 * at all. This annotation never affects behaviour.
 *
 * <pre>
 * &#64;FeatureUsage("configuration/static-code-analysis")
 * &#64;PutMapping("programming-exercises/{exerciseId}")
 * public ResponseEntity&lt;ProgrammingExercise&gt; updateProgrammingExercise(...)
 * </pre>
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface FeatureUsage {

    /**
     * The feature label as {@code area/feature} in kebab-case, unique within the module (for example
     * {@code "configuration/static-code-analysis"}).
     *
     * @return the label under which the annotated endpoints are reported
     */
    String value();
}
