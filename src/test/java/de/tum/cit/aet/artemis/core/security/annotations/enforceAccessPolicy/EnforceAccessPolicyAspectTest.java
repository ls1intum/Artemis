package de.tum.cit.aet.artemis.core.security.annotations.enforceAccessPolicy;

import static de.tum.cit.aet.artemis.core.security.policy.AccessPolicy.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.policy.AccessPolicy;
import de.tum.cit.aet.artemis.core.security.policy.Conditions;
import de.tum.cit.aet.artemis.core.security.policy.EntityManagerPolicyResourceResolver;
import de.tum.cit.aet.artemis.core.security.policy.PolicyEngine;
import de.tum.cit.aet.artemis.core.security.policy.PolicyResourceResolver;

@ExtendWith(MockitoExtension.class)
class EnforceAccessPolicyAspectTest {

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EntityManagerPolicyResourceResolver defaultResolver;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    private PolicyEngine policyEngine;

    private EnforceAccessPolicyAspect aspect;

    /**
     * Simple test entity used as the policy resource.
     */
    private static class TestResource {

        private final long id;

        private final boolean published;

        TestResource(long id, boolean published) {
            this.id = id;
            this.published = published;
        }

        boolean isPublished() {
            return published;
        }
    }

    /**
     * A test-specific resolver for {@link TestResource}.
     */
    private static class TestResourceResolver implements PolicyResourceResolver<TestResource> {

        private final TestResource resource;

        TestResourceResolver(TestResource resource) {
            this.resource = resource;
        }

        @Override
        public Class<TestResource> getResourceType() {
            return TestResource.class;
        }

        @Override
        public TestResource loadById(long id) {
            if (resource != null && resource.id == id) {
                return resource;
            }
            throw new EntityNotFoundException("TestResource", id);
        }
    }

    @BeforeEach
    void setUp() {
        policyEngine = new PolicyEngine();
    }

    private void setupAspect(List<PolicyResourceResolver<?>> resolvers) {
        aspect = new EnforceAccessPolicyAspect(applicationContext, policyEngine, userRepository, defaultResolver, resolvers);
    }

    private void setupJoinPoint(String paramName, Long paramValue) {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getParameterNames()).thenReturn(new String[] { paramName });
        when(joinPoint.getArgs()).thenReturn(new Object[] { paramValue });
    }

    private static User createUser(Set<String> groups, Set<Authority> authorities) {
        User user = new User();
        user.setGroups(groups);
        user.setAuthorities(authorities);
        return user;
    }

    @Test
    void testAllowedAccess_WithSpecificResolver() throws Throwable {
        TestResource resource = new TestResource(42L, true);
        TestResourceResolver resolver = new TestResourceResolver(resource);
        setupAspect(List.of(resolver));

        // Policy: allow admins
        AccessPolicy<TestResource> policy = AccessPolicy.forResource(TestResource.class).named("test-policy").rule(when(Conditions.<TestResource>isAdmin()).thenAllow())
                .denyByDefault();
        when(applicationContext.getBean("testPolicy", AccessPolicy.class)).thenReturn(policy);

        User admin = createUser(Set.of(), Set.of(new Authority(Role.ADMIN.getAuthority())));
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(admin);

        setupJoinPoint("resourceId", 42L);
        Object expectedResult = "success";
        when(joinPoint.proceed()).thenReturn(expectedResult);

        EnforceAccessPolicy annotation = createAnnotation("testPolicy", "resourceId");
        Object result = aspect.enforce(joinPoint, annotation);

        assertThat(result).isEqualTo(expectedResult);
        verify(joinPoint).proceed();
    }

    @Test
    void testDeniedAccess_ThrowsAccessForbiddenException() throws Throwable {
        TestResource resource = new TestResource(42L, true);
        TestResourceResolver resolver = new TestResourceResolver(resource);
        setupAspect(List.of(resolver));

        // Policy: only allow admins
        AccessPolicy<TestResource> policy = AccessPolicy.forResource(TestResource.class).named("test-policy").rule(when(Conditions.<TestResource>isAdmin()).thenAllow())
                .denyByDefault();
        when(applicationContext.getBean("testPolicy", AccessPolicy.class)).thenReturn(policy);

        User student = createUser(Set.of("some-group"), Set.of());
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(student);

        setupJoinPoint("resourceId", 42L);

        EnforceAccessPolicy annotation = createAnnotation("testPolicy", "resourceId");
        assertThatThrownBy(() -> aspect.enforce(joinPoint, annotation)).isInstanceOf(AccessForbiddenException.class);

        verify(joinPoint, never()).proceed();
    }

    @Test
    void testFallsBackToDefaultResolver_WhenNoSpecificResolverRegistered() throws Throwable {
        setupAspect(List.of()); // no specific resolvers

        TestResource resource = new TestResource(42L, true);

        // Policy: allow everyone
        AccessPolicy<TestResource> policy = AccessPolicy.forResource(TestResource.class).named("test-policy").allowByDefault();
        when(applicationContext.getBean("testPolicy", AccessPolicy.class)).thenReturn(policy);
        when(defaultResolver.loadById(TestResource.class, 42L)).thenReturn(resource);

        User user = createUser(Set.of(), Set.of());
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user);

        setupJoinPoint("resourceId", 42L);
        when(joinPoint.proceed()).thenReturn("ok");

        EnforceAccessPolicy annotation = createAnnotation("testPolicy", "resourceId");
        Object result = aspect.enforce(joinPoint, annotation);

        assertThat(result).isEqualTo("ok");
        verify(defaultResolver).loadById(TestResource.class, 42L);
    }

    @Test
    void testMissingResourceIdParameter_ThrowsIllegalArgumentException() {
        setupAspect(List.of());

        AccessPolicy<TestResource> policy = AccessPolicy.forResource(TestResource.class).named("test-policy").allowByDefault();
        when(applicationContext.getBean("testPolicy", AccessPolicy.class)).thenReturn(policy);

        // Parameter name doesn't match
        setupJoinPoint("wrongParam", 42L);

        EnforceAccessPolicy annotation = createAnnotation("testPolicy", "resourceId");
        assertThatThrownBy(() -> aspect.enforce(joinPoint, annotation)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("resourceId");
    }

    @Test
    void testEntityNotFound_ThrowsEntityNotFoundException() {
        TestResource resource = new TestResource(42L, true);
        TestResourceResolver resolver = new TestResourceResolver(resource);
        setupAspect(List.of(resolver));

        AccessPolicy<TestResource> policy = AccessPolicy.forResource(TestResource.class).named("test-policy").allowByDefault();
        when(applicationContext.getBean("testPolicy", AccessPolicy.class)).thenReturn(policy);

        // Request entity with different ID than what the resolver has
        setupJoinPoint("resourceId", 999L);

        EnforceAccessPolicy annotation = createAnnotation("testPolicy", "resourceId");
        assertThatThrownBy(() -> aspect.enforce(joinPoint, annotation)).isInstanceOf(EntityNotFoundException.class);
    }

    /**
     * Creates a proxy {@link EnforceAccessPolicy} annotation instance for testing.
     */
    private static EnforceAccessPolicy createAnnotation(String value, String resourceIdFieldName) {
        return new EnforceAccessPolicy() {

            @Override
            public Class<EnforceAccessPolicy> annotationType() {
                return EnforceAccessPolicy.class;
            }

            @Override
            public String value() {
                return value;
            }

            @Override
            public String resourceIdFieldName() {
                return resourceIdFieldName;
            }
        };
    }
}
