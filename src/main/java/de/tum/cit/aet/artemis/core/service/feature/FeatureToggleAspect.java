package de.tum.cit.aet.artemis.core.service.feature;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.security.annotations.AnnotationUtils.getAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;

@Profile(PROFILE_CORE)
@Component
@Aspect
public class FeatureToggleAspect {

    private final FeatureToggleService featureToggleService;

    public FeatureToggleAspect(FeatureToggleService featureToggleService) {
        this.featureToggleService = featureToggleService;
    }

    /**
     * Pointcut around all methods or classes annotated with {@link FeatureToggle}.
     *
     * @param featureToggle The feature toggle annotation containing the relevant features
     */
    @Pointcut("@within(featureToggle) || @annotation(featureToggle)")
    public void callAt(FeatureToggle featureToggle) {
    }

    /**
     * Aspect around all methods for which a feature toggle has been activated. Will check all specified features and only
     * execute the underlying method if all features are enabled. Will otherwise return forbidden (as response entity)
     *
     * @param joinPoint Proceeding join point of the aspect
     * @return The original return value of the called method, if all features are enabled, a forbidden response entity otherwise
     * @throws Throwable If there was any error during method execution (both the aspect or the actual called method)
     */
    @Around(value = "callAt()", argNames = "joinPoint")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        List<FeatureToggle> featureToggleAnnotations = getAnnotations(FeatureToggle.class, joinPoint);
        Stream<Feature> features = featureToggleAnnotations.stream().flatMap(featureToggle -> Arrays.stream(featureToggle.value()));
        if (features.allMatch(featureToggleService::isFeatureEnabled)) {
            return joinPoint.proceed();
        }
        else {
            throw new AccessForbiddenException();
        }
    }
}
