package de.tum.cit.aet.artemis.core.security.policy;

import static de.tum.cit.aet.artemis.core.security.policy.AccessPolicy.when;
import static de.tum.cit.aet.artemis.core.security.policy.Conditions.hasNotEnded;
import static de.tum.cit.aet.artemis.core.security.policy.Conditions.hasStarted;
import static de.tum.cit.aet.artemis.core.security.policy.Conditions.isAdmin;
import static de.tum.cit.aet.artemis.core.security.policy.Conditions.memberOfGroup;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.security.Role;

class AccessPolicyTest {

    private static User createUser(Set<String> groups, Set<Authority> authorities) {
        User user = new User();
        user.setGroups(groups);
        user.setAuthorities(authorities);
        return user;
    }

    private static User createUser(Set<String> groups) {
        return createUser(groups, Set.of());
    }

    // -- Policy evaluation tests --

    @Test
    void testMatchingRuleReturnsEffect() {
        PolicyCondition<String> alwaysTrue = Conditions.always();
        AccessPolicy<String> policy = AccessPolicy.forResource(String.class).named("test").rule(when(alwaysTrue).thenAllow()).denyByDefault();

        User user = createUser(Set.of());
        assertThat(policy.evaluate(user, "resource")).isEqualTo(PolicyEffect.ALLOW);
    }

    @Test
    void testNoMatchingRuleReturnsDenyDefault() {
        PolicyCondition<String> alwaysFalse = Conditions.never();
        AccessPolicy<String> policy = AccessPolicy.forResource(String.class).named("test").rule(when(alwaysFalse).thenAllow()).denyByDefault();

        User user = createUser(Set.of());
        assertThat(policy.evaluate(user, "resource")).isEqualTo(PolicyEffect.DENY);
    }

    @Test
    void testNoMatchingRuleReturnsAllowDefault() {
        PolicyCondition<String> alwaysFalse = Conditions.never();
        AccessPolicy<String> policy = AccessPolicy.forResource(String.class).named("test").rule(when(alwaysFalse).thenDeny()).allowByDefault();

        User user = createUser(Set.of());
        assertThat(policy.evaluate(user, "resource")).isEqualTo(PolicyEffect.ALLOW);
    }

    @Test
    void testFirstMatchingRuleWins() {
        PolicyCondition<String> alwaysTrue = Conditions.always();
        AccessPolicy<String> policy = AccessPolicy.forResource(String.class).named("test").rule(when(alwaysTrue).thenDeny()).rule(when(alwaysTrue).thenAllow()).denyByDefault();

        User user = createUser(Set.of());
        assertThat(policy.evaluate(user, "resource")).isEqualTo(PolicyEffect.DENY);
    }

    @Test
    void testPolicyName() {
        AccessPolicy<String> policy = AccessPolicy.forResource(String.class).named("my-policy").denyByDefault();

        assertThat(policy.getName()).isEqualTo("my-policy");
    }

    @Test
    void testPolicyResourceType() {
        AccessPolicy<String> policy = AccessPolicy.forResource(String.class).named("test").denyByDefault();

        assertThat(policy.getResourceType()).isEqualTo(String.class);
    }

    // -- Condition composition tests --

    @Test
    void testAndCondition() {
        PolicyCondition<String> trueCondition = Conditions.always();
        PolicyCondition<String> falseCondition = Conditions.never();

        User user = createUser(Set.of());
        assertThat(trueCondition.and(trueCondition).test(user, "x")).isTrue();
        assertThat(trueCondition.and(falseCondition).test(user, "x")).isFalse();
        assertThat(falseCondition.and(trueCondition).test(user, "x")).isFalse();
        assertThat(falseCondition.and(falseCondition).test(user, "x")).isFalse();
    }

    @Test
    void testOrCondition() {
        PolicyCondition<String> trueCondition = Conditions.always();
        PolicyCondition<String> falseCondition = Conditions.never();

        User user = createUser(Set.of());
        assertThat(trueCondition.or(trueCondition).test(user, "x")).isTrue();
        assertThat(trueCondition.or(falseCondition).test(user, "x")).isTrue();
        assertThat(falseCondition.or(trueCondition).test(user, "x")).isTrue();
        assertThat(falseCondition.or(falseCondition).test(user, "x")).isFalse();
    }

    @Test
    void testNegateCondition() {
        PolicyCondition<String> trueCondition = Conditions.always();
        PolicyCondition<String> falseCondition = Conditions.never();

        User user = createUser(Set.of());
        assertThat(trueCondition.negate().test(user, "x")).isFalse();
        assertThat(falseCondition.negate().test(user, "x")).isTrue();
    }

    // -- Built-in condition tests --

    record GroupResource(String groupName) {
    }

    @Test
    void testMemberOfGroupMatches() {
        User user = createUser(Set.of("students"));
        PolicyCondition<GroupResource> condition = memberOfGroup(GroupResource::groupName);

        assertThat(condition.test(user, new GroupResource("students"))).isTrue();
    }

    @Test
    void testMemberOfGroupDoesNotMatch() {
        User user = createUser(Set.of("students"));
        PolicyCondition<GroupResource> condition = memberOfGroup(GroupResource::groupName);

        assertThat(condition.test(user, new GroupResource("instructors"))).isFalse();
    }

    @Test
    void testMemberOfGroupWithNullGroup() {
        User user = createUser(Set.of("students"));
        PolicyCondition<GroupResource> condition = memberOfGroup(GroupResource::groupName);

        assertThat(condition.test(user, new GroupResource(null))).isFalse();
    }

    record DatedResource(ZonedDateTime startDate, ZonedDateTime endDate) {
    }

    @Test
    void testHasStartedWithPastDate() {
        User user = createUser(Set.of());
        PolicyCondition<DatedResource> condition = hasStarted(DatedResource::startDate);

        assertThat(condition.test(user, new DatedResource(ZonedDateTime.now().minusDays(1), null))).isTrue();
    }

    @Test
    void testHasStartedWithFutureDate() {
        User user = createUser(Set.of());
        PolicyCondition<DatedResource> condition = hasStarted(DatedResource::startDate);

        assertThat(condition.test(user, new DatedResource(ZonedDateTime.now().plusDays(1), null))).isFalse();
    }

    @Test
    void testHasStartedWithNullDate() {
        User user = createUser(Set.of());
        PolicyCondition<DatedResource> condition = hasStarted(DatedResource::startDate);

        assertThat(condition.test(user, new DatedResource(null, null))).isTrue();
    }

    @Test
    void testHasNotEndedWithFutureDate() {
        User user = createUser(Set.of());
        PolicyCondition<DatedResource> condition = hasNotEnded(DatedResource::endDate);

        assertThat(condition.test(user, new DatedResource(null, ZonedDateTime.now().plusDays(1)))).isTrue();
    }

    @Test
    void testHasNotEndedWithPastDate() {
        User user = createUser(Set.of());
        PolicyCondition<DatedResource> condition = hasNotEnded(DatedResource::endDate);

        assertThat(condition.test(user, new DatedResource(null, ZonedDateTime.now().minusDays(1)))).isFalse();
    }

    @Test
    void testHasNotEndedWithNullDate() {
        User user = createUser(Set.of());
        PolicyCondition<DatedResource> condition = hasNotEnded(DatedResource::endDate);

        assertThat(condition.test(user, new DatedResource(null, null))).isTrue();
    }

    @Test
    void testIsAdminWithAdminAuthority() {
        User user = createUser(Set.of(), Set.of(new Authority(Role.ADMIN.getAuthority())));
        PolicyCondition<String> condition = isAdmin();

        assertThat(condition.test(user, "resource")).isTrue();
    }

    @Test
    void testIsAdminWithSuperAdminAuthority() {
        User user = createUser(Set.of(), Set.of(new Authority(Role.SUPER_ADMIN.getAuthority())));
        PolicyCondition<String> condition = isAdmin();

        assertThat(condition.test(user, "resource")).isTrue();
    }

    @Test
    void testIsAdminWithNonAdminAuthority() {
        User user = createUser(Set.of(), Set.of(new Authority(Role.STUDENT.getAuthority())));
        PolicyCondition<String> condition = isAdmin();

        assertThat(condition.test(user, "resource")).isFalse();
    }

    @Test
    void testIsAdminWithNoAuthorities() {
        User user = createUser(Set.of(), Set.of());
        PolicyCondition<String> condition = isAdmin();

        assertThat(condition.test(user, "resource")).isFalse();
    }
}
