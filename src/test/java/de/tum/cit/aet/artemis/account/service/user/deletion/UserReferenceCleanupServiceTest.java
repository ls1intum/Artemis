package de.tum.cit.aet.artemis.account.service.user.deletion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Holds the cleanup bindings against the policy catalogue and against the database.
 *
 * <p>
 * A schema test already fails when a new foreign key to {@code jhi_user} has no policy. That would be worth little if a
 * policy could then exist without a way to carry it out, so this test insists that every one of them is bound, and that
 * both statements behind each binding actually run: a native query is not checked when the context starts, and a
 * mistake in one would otherwise surface only when an account is deleted.
 */
class UserReferenceCleanupServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "userrefcleanup";

    @Autowired
    private UserReferenceCleanupService userReferenceCleanupService;

    @Autowired
    private UserUtilService userUtilService;

    @Test
    void everyReferencePolicyIsBoundToACleanup() {
        assertThat(userReferenceCleanupService.boundPolicies()).as("a policy without a cleanup would leave rows behind when the account is deleted")
                .containsExactlyInAnyOrder(UserDeletionReferencePolicy.values());
    }

    @Test
    void everyBoundCountRunsAndReportsPerAccount() {
        User first = userUtilService.createAndSaveUser(TEST_PREFIX + "counta");
        User second = userUtilService.createAndSaveUser(TEST_PREFIX + "countb");
        userUtilService.addAdminAuthorityTo(first.getLogin());
        List<Long> userIds = List.of(first.getId(), second.getId());

        for (UserDeletionReferencePolicy policy : UserDeletionReferencePolicy.values()) {
            Map<Long, Long> counts = userReferenceCleanupService.count(policy, userIds);
            assertThat(counts.keySet()).as("%s counts only the accounts it was asked about", policy).isSubsetOf(userIds);
            assertThat(counts.values()).as("%s never reports an empty group", policy).allMatch(count -> count > 0);
        }

        assertThat(userReferenceCleanupService.count(UserDeletionReferencePolicy.AUTHORITY, userIds)).as("the granted authority is the one reference either account has")
                .containsOnlyKeys(first.getId());
    }

    @Test
    void everyBoundResolveRuns() {
        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "resolve");
        userUtilService.addAdminAuthorityTo(user.getLogin());

        for (UserDeletionReferencePolicy policy : UserDeletionReferencePolicy.values()) {
            assertThatCode(() -> userReferenceCleanupService.resolve(policy, user.getId())).as("%s can be carried out", policy).doesNotThrowAnyException();
        }

        assertThat(userReferenceCleanupService.count(UserDeletionReferencePolicy.AUTHORITY, List.of(user.getId()))).as("resolving removed the authority it counted before")
                .isEmpty();
    }

    @Test
    void countingReportsNothingWhenNoAccountIsGiven() {
        assertThat(userReferenceCleanupService.count(UserDeletionReferencePolicy.CONDUCT_AGREEMENT, List.of())).isEmpty();
    }
}
