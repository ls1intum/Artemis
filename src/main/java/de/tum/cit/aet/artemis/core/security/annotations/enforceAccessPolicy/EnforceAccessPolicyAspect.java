package de.tum.cit.aet.artemis.core.security.annotations.enforceAccessPolicy;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.security.annotations.AnnotationUtils.getIdFromSignature;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.policy.AccessPolicy;
import de.tum.cit.aet.artemis.core.security.policy.EntityManagerPolicyResourceResolver;
import de.tum.cit.aet.artemis.core.security.policy.PolicyEngine;
import de.tum.cit.aet.artemis.core.security.policy.PolicyResourceResolver;

/**
 * AOP aspect that enforces {@link EnforceAccessPolicy} annotations on controller methods.
 * <p>
 * When a method annotated with {@code @EnforceAccessPolicy} is called, this aspect:
 * <ol>
 * <li>Looks up the named {@link AccessPolicy} bean from the application context</li>
 * <li>Extracts the resource ID from the method parameters</li>
 * <li>Loads the resource entity using a {@link PolicyResourceResolver} (or the default {@link EntityManagerPolicyResourceResolver})</li>
 * <li>Loads the current user with groups and authorities</li>
 * <li>Evaluates the policy via the {@link PolicyEngine}</li>
 * </ol>
 * If the policy denies access, an {@link de.tum.cit.aet.artemis.core.exception.AccessForbiddenException} is thrown.
 */
@Profile(PROFILE_CORE)
@Component
@Lazy
@Aspect
public class EnforceAccessPolicyAspect {

    private final ApplicationContext applicationContext;

    private final PolicyEngine policyEngine;

    private final UserRepository userRepository;

    private final EntityManagerPolicyResourceResolver defaultResolver;

    private final Map<Class<?>, PolicyResourceResolver<?>> resolvers;

    public EnforceAccessPolicyAspect(ApplicationContext applicationContext, PolicyEngine policyEngine, UserRepository userRepository,
            EntityManagerPolicyResourceResolver defaultResolver, List<PolicyResourceResolver<?>> resolverList) {
        this.applicationContext = applicationContext;
        this.policyEngine = policyEngine;
        this.userRepository = userRepository;
        this.defaultResolver = defaultResolver;
        this.resolvers = resolverList.stream().collect(Collectors.toMap(PolicyResourceResolver::getResourceType, r -> r));
    }

    /**
     * Intercepts methods annotated with {@link EnforceAccessPolicy} and evaluates the referenced policy.
     *
     * @param joinPoint           the method invocation join point
     * @param enforceAccessPolicy the annotation instance providing the policy bean name and resource ID field name
     * @return the original method's return value if the policy allows access
     * @throws Throwable if the method itself throws or if the policy denies access
     */
    @Around(value = "@annotation(enforceAccessPolicy)", argNames = "joinPoint,enforceAccessPolicy")
    public Object enforce(ProceedingJoinPoint joinPoint, EnforceAccessPolicy enforceAccessPolicy) throws Throwable {
        String policyBeanName = enforceAccessPolicy.value();
        String resourceIdFieldName = enforceAccessPolicy.resourceIdFieldName();

        @SuppressWarnings("unchecked")
        AccessPolicy<Object> policy = applicationContext.getBean(policyBeanName, AccessPolicy.class);

        long resourceId = getIdFromSignature(joinPoint, resourceIdFieldName).orElseThrow(
                () -> new IllegalArgumentException("Method annotated with @EnforceAccessPolicy must have a parameter named '" + resourceIdFieldName + "' of type Long."));

        Object resource = loadResource(policy.getResourceType(), resourceId);
        User user = userRepository.getUserWithGroupsAndAuthorities();

        policyEngine.checkAllowedElseThrow(policy, user, resource);

        return joinPoint.proceed();
    }

    private Object loadResource(Class<?> resourceType, long resourceId) {
        PolicyResourceResolver<?> resolver = resolvers.get(resourceType);
        if (resolver != null) {
            return resolver.loadById(resourceId);
        }
        return defaultResolver.loadById(resourceType, resourceId);
    }
}
