package de.tum.cit.aet.artemis.core.security.annotations.enforceAccessPolicy;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.policy.AccessPolicy;
import de.tum.cit.aet.artemis.core.security.policy.PolicyContext;
import de.tum.cit.aet.artemis.core.security.policy.PolicyProvider;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;

/**
 * AOP aspect that enforces {@link FilterByAccessPolicy} annotations on controller methods.
 * <p>
 * When a method annotated with {@code @FilterByAccessPolicy} is called, this aspect:
 * <ol>
 * <li>Looks up the named {@link AccessPolicy} bean from the application context</li>
 * <li>Loads the current user with groups and authorities</li>
 * <li>Sets up a ThreadLocal {@link PolicyContext} with the policy and user information</li>
 * <li>Proceeds with the method execution (service/repository can access the context)</li>
 * <li>Clears the context after completion (even if exceptions occur)</li>
 * </ol>
 * <p>
 * This enables automatic policy-based SQL query filtering without explicit specification
 * building in service methods.
 * <p>
 * <strong>Design rationale:</strong>
 * <ul>
 * <li>Uses ThreadLocal for clean separation of concerns (no parameter passing)</li>
 * <li>Context is scoped to a single request thread (thread-safe)</li>
 * <li>Automatic cleanup prevents context leaks in thread pools</li>
 * <li>Service methods can still override by building specifications manually</li>
 * </ul>
 *
 * @see FilterByAccessPolicy
 * @see PolicyContext
 * @see de.tum.cit.aet.artemis.core.security.policy.AccessPolicy#toSpecification
 */
@Profile(PROFILE_CORE)
@Component
@Lazy
@Aspect
public class FilterByAccessPolicyAspect {

    private static final Logger log = LoggerFactory.getLogger(FilterByAccessPolicyAspect.class);

    private final ApplicationContext applicationContext;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authCheckService;

    public FilterByAccessPolicyAspect(ApplicationContext applicationContext, UserRepository userRepository, AuthorizationCheckService authCheckService) {
        this.applicationContext = applicationContext;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * Intercepts methods annotated with {@link FilterByAccessPolicy} and sets up
     * the policy context for automatic query filtering.
     *
     * @param joinPoint            the method invocation join point
     * @param filterByAccessPolicy the annotation instance providing the policy provider class and options
     * @return the original method's return value
     * @throws Throwable if the method itself throws an exception
     */
    @Around(value = "@annotation(filterByAccessPolicy)", argNames = "joinPoint,filterByAccessPolicy")
    public Object setupPolicyContext(ProceedingJoinPoint joinPoint, FilterByAccessPolicy filterByAccessPolicy) throws Throwable {
        Class<?> policyProviderClass = filterByAccessPolicy.value();
        boolean includeActive = filterByAccessPolicy.includeActive();

        // Look up the policy provider bean
        @SuppressWarnings("unchecked")
        PolicyProvider<Object> policyProvider = (PolicyProvider<Object>) applicationContext.getBean(policyProviderClass);
        AccessPolicy<Object> policy = policyProvider.getPolicy();

        // Load current user with groups and authorities
        User user = userRepository.getUserWithGroupsAndAuthorities();
        boolean isAdmin = authCheckService.isAdmin(user);

        // Set up ThreadLocal policy context
        PolicyContext.set(policy, user.getGroups(), isAdmin, includeActive);

        if (log.isDebugEnabled()) {
            log.debug("Set policy context for method {}: policy={}, userGroups={}, isAdmin={}, includeActive={}", joinPoint.getSignature().toShortString(), policy.getName(),
                    user.getGroups().size(), isAdmin, includeActive);
        }

        try {
            // Proceed with method execution - service/repository can access PolicyContext
            return joinPoint.proceed();
        }
        finally {
            // Always clear the context to prevent leaks
            PolicyContext.clear();
            if (log.isTraceEnabled()) {
                log.trace("Cleared policy context for method {}", joinPoint.getSignature().toShortString());
            }
        }
    }
}
