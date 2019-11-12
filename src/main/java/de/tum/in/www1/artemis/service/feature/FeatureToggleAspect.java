package de.tum.in.www1.artemis.service.feature;

import java.util.Arrays;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.web.rest.util.ResponseUtil;

@Component
@Aspect
public class FeatureToggleAspect {

    @Pointcut("@within(featureToggle) || @annotation(featureToggle)")
    public void callAt(FeatureToggle featureToggle) {
    }

    @Around(value = "callAt(featureToggle)", argNames = "joinPoint,featureToggle")
    public Object before(ProceedingJoinPoint joinPoint, FeatureToggle featureToggle) throws Throwable {
        if (Arrays.stream(featureToggle.value()).allMatch(Feature::isEnabled)) {
            return joinPoint.proceed();
        }
        else {
            return ResponseUtil.forbidden();
        }
    }
}
