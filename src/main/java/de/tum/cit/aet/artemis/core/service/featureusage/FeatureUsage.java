package de.tum.cit.aet.artemis.core.service.featureusage;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Groups one or more endpoints under a named logical feature on the admin feature usage page.
 * <p>
 * Purely optional. Every endpoint is already tracked without it, listed by its HTTP verb and canonical path, so the
 * analysis is complete whether or not anyone annotates anything. What the annotation adds is meaning: it names a feature
 * the way the team talks about it, and it collapses the several endpoints that make up one feature into a single row.
 * Use it where the raw path does not tell you what is being used, in particular for sub features of a larger area.
 * <p>
 * The module is <b>not</b> part of this annotation. It is derived from the controller's package, which the
 * {@code api/<module>/} path convention already guarantees to be right, so there is nothing here that can drift out of
 * sync with reality.
 * <p>
 * On a method the annotation labels that endpoint. On a controller it labels every endpoint in it, and a method level
 * annotation wins over the class level one. Because the label is only a grouping attribute on a row keyed by endpoint,
 * adding, renaming or removing it is safe at any time: historic counters regroup under the new label immediately and no
 * endpoint detail is lost.
 * <p>
 * Not to be confused with {@code @FeatureToggle}, which is a kill switch that decides whether an endpoint may be called
 * at all. This annotation never affects behaviour.
 *
 * <pre>
 * &#64;FeatureUsage("static-code-analysis")
 * &#64;PutMapping("programming-exercises/{exerciseId}")
 * public ResponseEntity&lt;ProgrammingExercise&gt; updateProgrammingExercise(...)
 * </pre>
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface FeatureUsage {

    /**
     * The feature label, in kebab-case, unique within the module (for example {@code "static-code-analysis"}).
     *
     * @return the label under which the annotated endpoints are reported
     */
    String value();
}
