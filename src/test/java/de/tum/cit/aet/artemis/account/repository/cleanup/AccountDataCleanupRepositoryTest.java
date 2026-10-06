package de.tum.cit.aet.artemis.account.repository.cleanup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Proves that both shapes of counting query the cleanup repositories use actually run and project.
 *
 * <p>
 * A malformed query here is only rejected when it executes, and the entity-based and the native form are mapped by
 * different machinery, so one of each is exercised: the account has authorities from the moment it is created, which
 * makes the join table the natural native case.
 */
class AccountDataCleanupRepositoryTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "accountcleanup";

    @Autowired
    private AccountDataCleanupRepository accountDataCleanupRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Test
    void countsTheAuthoritiesOfEveryGivenAccountInOneQuery() {
        User first = userUtilService.createAndSaveUser(TEST_PREFIX + "first");
        User second = userUtilService.createAndSaveUser(TEST_PREFIX + "second");
        // A plainly created account holds no authority row, so the join table has to be given something to count.
        userUtilService.addAdminAuthorityTo(first.getLogin());
        userUtilService.addAdminAuthorityTo(second.getLogin());

        List<UserReferenceCount> counts = accountDataCleanupRepository.countAuthorities(List.of(first.getId(), second.getId()));

        assertThat(counts).as("a native count projects one row per account").hasSize(2);
        assertThat(counts).allSatisfy(count -> assertThat(count.getCount()).isPositive());
        assertThat(counts).extracting(UserReferenceCount::getUserId).containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    @Test
    void countsAnEntityBackedReferenceAndReportsNothingForAnAccountWithoutRows() {
        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "entity");

        List<UserReferenceCount> counts = accountDataCleanupRepository.countConductAgreements(List.of(user.getId()));

        assertThat(counts).as("grouping returns no row for an account that owns none").isEmpty();
    }

    @Test
    void deletingReportsHowManyRowsItRemoved() {
        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "delete");
        userUtilService.addAdminAuthorityTo(user.getLogin());

        assertThat(accountDataCleanupRepository.deleteAuthorities(user.getId())).as("the granted authority is removed").isPositive();
        assertThat(accountDataCleanupRepository.countAuthorities(List.of(user.getId()))).isEmpty();
    }
}
