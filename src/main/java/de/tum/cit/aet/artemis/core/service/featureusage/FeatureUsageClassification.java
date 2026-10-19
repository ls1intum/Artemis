package de.tum.cit.aet.artemis.core.service.featureusage;

import java.lang.reflect.Method;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RequestMethod;

import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;
import de.tum.cit.aet.artemis.core.security.annotations.Internal;

/**
 * Derives which feature an endpoint serves and how its calls count, from the annotations on its handler method.
 * <p>
 * A class of its own so that the startup inventory and {@code FeatureUsageAnnotationTest}, which renders the catalogue
 * document from the same annotations, apply literally the same rules. A catalogue that derived the interaction
 * differently from the runtime would be a review surface for something that never happens.
 */
public final class FeatureUsageClassification {

    /** The verbs that only read by convention, which is what makes them views unless an endpoint says otherwise. */
    private static final Set<RequestMethod> READING_VERBS = Set.of(RequestMethod.GET, RequestMethod.HEAD, RequestMethod.OPTIONS);

    private FeatureUsageClassification() {
    }

    /**
     * Resolves the feature an endpoint serves. A method level {@link FeatureUsage} wins over the controller's own, so a
     * controller that serves several features can split them.
     *
     * @param method   the handler method
     * @param beanType the controller declaring it
     * @return the feature, or {@code null} if neither the method nor the controller declares one
     */
    @Nullable
    public static UserFeature featureOf(Method method, Class<?> beanType) {
        FeatureUsage annotation = method.getAnnotation(FeatureUsage.class);
        if (annotation == null) {
            annotation = beanType.getAnnotation(FeatureUsage.class);
        }
        return annotation == null ? null : annotation.value();
    }

    /**
     * Derives how calls to an endpoint count. In order of precedence: an explicit {@link UsageInteraction}, then
     * {@link Internal} for a call by another system, then the verb, where only a mapping that exclusively reads is a view.
     * A mapping that declares no verb at all accepts every verb and is therefore treated as an action.
     *
     * @param method   the handler method
     * @param beanType the controller declaring it
     * @param verbs    the HTTP verbs the endpoint is mapped to, empty when it accepts any
     * @return the interaction to report the endpoint's calls as
     */
    public static FeatureInteraction interactionOf(Method method, Class<?> beanType, Set<RequestMethod> verbs) {
        UsageInteraction override = method.getAnnotation(UsageInteraction.class);
        if (override != null) {
            return override.value();
        }
        return derivedInteractionOf(method, beanType, verbs);
    }

    /**
     * The interaction an endpoint gets without an explicit {@link UsageInteraction}. Exposed so that the annotation test
     * can reject an override that only repeats it.
     *
     * @param method   the handler method
     * @param beanType the controller declaring it
     * @param verbs    the HTTP verbs the endpoint is mapped to, empty when it accepts any
     * @return the interaction derived from {@link Internal} and the verb
     */
    public static FeatureInteraction derivedInteractionOf(Method method, Class<?> beanType, Set<RequestMethod> verbs) {
        if (method.isAnnotationPresent(Internal.class) || beanType.isAnnotationPresent(Internal.class)) {
            return FeatureInteraction.SYSTEM;
        }
        if (!verbs.isEmpty() && READING_VERBS.containsAll(verbs)) {
            return FeatureInteraction.VIEW;
        }
        return FeatureInteraction.ACTION;
    }
}
