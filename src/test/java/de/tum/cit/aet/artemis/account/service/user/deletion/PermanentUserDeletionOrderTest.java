package de.tum.cit.aet.artemis.account.service.user.deletion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.dto.UserDeletionImpactDTO;
import de.tum.cit.aet.artemis.account.dto.UserDeletionResultStatus;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.AccountCredentialRevocationService;
import de.tum.cit.aet.artemis.admin.repository.CustomAuditEventRepository;
import de.tum.cit.aet.artemis.core.service.FileService;

/**
 * Pins the order in which a permanent deletion does its work.
 *
 * <p>
 * The account is taken out of use before anything is removed: it is deactivated, which every authentication provider
 * refuses, and its course memberships are dropped, which is what its access inside Artemis consists of. Removing an
 * account's data takes a while and is deliberately not wrapped in one transaction, so this order is what keeps the
 * account from being usable while its rows are disappearing. An ordering mistake would not fail any other test - the
 * end state is the same - which is why it is asserted here.
 */
class PermanentUserDeletionOrderTest {

    private static final long USER_ID = 42L;

    private static final String FINGERPRINT = "fingerprint";

    private UserRepository userRepository;

    private UserDeletionPlanService userDeletionPlanService;

    private UserReferenceCleanupService userReferenceCleanupService;

    private UserOwnedContentDeletionService userOwnedContentDeletionService;

    private PermanentUserDeletionService permanentUserDeletionService;

    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userDeletionPlanService = mock(UserDeletionPlanService.class);
        userReferenceCleanupService = mock(UserReferenceCleanupService.class);
        userOwnedContentDeletionService = mock(UserOwnedContentDeletionService.class);
        AccountCredentialRevocationService accountCredentialRevocationService = mock(AccountCredentialRevocationService.class);
        FileService fileService = mock(FileService.class);
        CustomAuditEventRepository auditEventRepository = mock(CustomAuditEventRepository.class);

        user = new User();
        user.setId(USER_ID);
        user.setLogin("doomed");
        user.setActivated(true);
        user.setAuthorities(Set.of());

        when(userRepository.findByIdForDeletion(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.deleteUserRow(USER_ID)).thenReturn(1);
        when(userDeletionPlanService.createImpact(any(), any())).thenReturn(impact());
        when(userOwnedContentDeletionService.deleteDataExports(anyLong())).thenReturn(List.of());
        when(userOwnedContentDeletionService.deleteExamAttendance(anyLong())).thenReturn(List.of());

        permanentUserDeletionService = new PermanentUserDeletionService(userRepository, userDeletionPlanService, userReferenceCleanupService, userOwnedContentDeletionService,
                accountCredentialRevocationService, fileService, auditEventRepository, null);
    }

    private static UserDeletionImpactDTO impact() {
        return new UserDeletionImpactDTO(USER_ID, "doomed", false, false, true, 0, FINGERPRINT, List.of());
    }

    @Test
    void theAccountIsClosedBeforeAnythingIsRemoved() {
        assertThat(permanentUserDeletionService.deleteByAdmin(USER_ID, FINGERPRINT, "an-admin").status()).isEqualTo(UserDeletionResultStatus.DELETED);

        InOrder order = inOrder(userRepository, userReferenceCleanupService, userOwnedContentDeletionService);
        order.verify(userRepository).deactivateForDeletion(USER_ID);
        order.verify(userReferenceCleanupService).resolve(UserDeletionReferencePolicy.COURSE_ROLE, USER_ID);
        // Only then does the deletion start removing what the account owns.
        order.verify(userOwnedContentDeletionService).deleteDataExports(USER_ID);
        order.verify(userOwnedContentDeletionService).deleteTeams(USER_ID);
        order.verify(userRepository).deleteUserRow(USER_ID);
    }

    @Test
    void theAccountStaysClosedWhenTheDeletionCannotFinish() {
        // A deletion is deliberately not one transaction: it would hold locks across a great deal of work. So a failure
        // part of the way through leaves the account behind - and it has to be left behind unusable rather than intact.
        doThrow(new IllegalStateException("something went wrong halfway through")).when(userOwnedContentDeletionService).deleteTeams(anyLong());

        assertThat(catchThrowable(() -> permanentUserDeletionService.deleteByAdmin(USER_ID, FINGERPRINT, "an-admin"))).isInstanceOf(IllegalStateException.class);

        InOrder order = inOrder(userRepository, userReferenceCleanupService);
        order.verify(userRepository).deactivateForDeletion(USER_ID);
        order.verify(userReferenceCleanupService).resolve(UserDeletionReferencePolicy.COURSE_ROLE, USER_ID);
        verify(userRepository, never()).deleteUserRow(anyLong());
    }

    @Test
    void aProtectedAccountIsNeverEvenClosed() {
        user.setLogin("the-admin");

        assertThat(permanentUserDeletionService.deleteByAdmin(USER_ID, FINGERPRINT, "the-admin").status()).isEqualTo(UserDeletionResultStatus.FORBIDDEN);

        verify(userRepository, never()).deactivateForDeletion(anyLong());
        verify(userReferenceCleanupService, never()).resolve(any(), anyLong());
    }

    @Test
    void aChangedPlanStopsBeforeTheAccountIsClosed() {
        assertThat(permanentUserDeletionService.deleteByAdmin(USER_ID, "a-stale-fingerprint", "an-admin").status()).isEqualTo(UserDeletionResultStatus.PLAN_CHANGED);

        verify(userRepository, never()).deactivateForDeletion(anyLong());
    }

    @Test
    void everyReferenceIsResolvedForAnAdministratorConfirmedDeletion() {
        permanentUserDeletionService.deleteByAdmin(USER_ID, FINGERPRINT, "an-admin");

        for (UserDeletionReferencePolicy policy : UserDeletionReferencePolicy.values()) {
            verify(userReferenceCleanupService, atLeastOnce()).resolve(eq(policy), eq(USER_ID));
        }
        verify(userOwnedContentDeletionService).anonymiseScienceEvents(anyString(), eq(USER_ID));
    }
}
