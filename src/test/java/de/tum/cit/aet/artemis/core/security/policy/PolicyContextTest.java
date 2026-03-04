package de.tum.cit.aet.artemis.core.security.policy;

import static de.tum.cit.aet.artemis.core.security.policy.SpecificationConditions.isAdmin;
import static de.tum.cit.aet.artemis.core.security.policy.SpecificationConditions.memberOfGroup;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Course_;

/**
 * Unit tests for {@link PolicyContext}.
 * <p>
 * Verifies that the ThreadLocal policy context correctly stores and retrieves
 * policy specifications, and that cleanup prevents context leaks.
 */
class PolicyContextTest {

    @AfterEach
    void cleanup() {
        PolicyContext.clear();
    }

    @Test
    void testPolicyContextSetAndGet() {
        // Create a simple policy
        AccessPolicy<Course> policy = AccessPolicy.forResource(Course.class).named("test-policy")
                .rule(AccessPolicy.when(memberOfGroup(Course::getInstructorGroupName, Course_.instructorGroupName).or(isAdmin())).thenAllow()).denyByDefault();

        Set<String> userGroups = Set.of("group1", "group2");
        boolean isAdmin = false;

        // Set context
        PolicyContext.set(policy, userGroups, isAdmin, false);

        // Verify context is set
        assertThat(PolicyContext.isContextSet()).isTrue();

        // Get specification
        Specification<Course> spec = PolicyContext.getCurrentSpecification(Course.class).orElseThrow();
        assertThat(spec).isNotNull();

        // Get raw context
        PolicyContext.PolicyContextData data = PolicyContext.getCurrent();
        assertThat(data).isNotNull();
        assertThat(data.policy()).isEqualTo(policy);
        assertThat(data.userGroups()).isEqualTo(userGroups);
        assertThat(data.isAdmin()).isEqualTo(isAdmin);
        assertThat(data.includeActive()).isFalse();
    }

    @Test
    void testPolicyContextClear() {
        // Create and set a policy
        AccessPolicy<Course> policy = AccessPolicy.forResource(Course.class).named("test-policy")
                .rule(AccessPolicy.when(memberOfGroup(Course::getInstructorGroupName, Course_.instructorGroupName)).thenAllow()).denyByDefault();

        PolicyContext.set(policy, Set.of("group1"), false, false);

        assertThat(PolicyContext.isContextSet()).isTrue();

        // Clear context
        PolicyContext.clear();

        // Verify context is cleared
        assertThat(PolicyContext.isContextSet()).isFalse();
        assertThat(PolicyContext.getCurrent()).isNull();
        assertThat(PolicyContext.getCurrentSpecification(Course.class)).isEmpty();
    }

    @Test
    void testGetSpecificationWrongResourceType() {
        // Create a Course policy
        AccessPolicy<Course> policy = AccessPolicy.forResource(Course.class).named("test-policy")
                .rule(AccessPolicy.when(memberOfGroup(Course::getInstructorGroupName, Course_.instructorGroupName)).thenAllow()).denyByDefault();

        PolicyContext.set(policy, Set.of("group1"), false, false);

        // Try to get specification for a different resource type (using Object as example)
        // Should return empty because resource type doesn't match
        assertThat(PolicyContext.getCurrentSpecification(Object.class)).isEmpty();
    }

    @Test
    void testGetSpecificationWhenNotSet() {
        // Try to get specification when no context is set
        assertThat(PolicyContext.getCurrentSpecification(Course.class)).isEmpty();
        assertThat(PolicyContext.isContextSet()).isFalse();
        assertThat(PolicyContext.getCurrent()).isNull();
    }
}
