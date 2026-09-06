package de.tum.cit.aet.artemis.account.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import de.tum.cit.aet.artemis.account.domain.UserActivity;

/**
 * The creation of an activity row for an account that has none, and the recovery from a row another request inserted
 * first. Driven against a stubbed repository rather than a database, because the collision has to happen between the
 * lookup and the save - inserting the row beforehand only exercises the ordinary update path.
 * <p>
 * A plain unit test on purpose: overriding the repository bean would fork the Spring context for every test scheduled
 * after it, which is a large cost for one narrow path.
 */
class UserActivityRepositoryConcurrencyTest {

    private static final long USER_ID = 42L;

    private static final String LOGIN = "ab12cde";

    private static final Instant RECORDED_AT = Instant.parse("2026-01-02T03:04:05Z");

    private static UserActivityRepository repositoryRunningItsDefaultMethods() {
        return mock(UserActivityRepository.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
    }

    /**
     * The hot path, and the reason the timestamps have their own table: a login of an account that already has a row must
     * cost one statement, with no lookup of the row and no save of an entity.
     */
    @Test
    void aLoginIsRecordedWithoutLoadingTheRowWhenTheAccountAlreadyHasOne() {
        UserActivityRepository repository = repositoryRunningItsDefaultMethods();
        when(repository.recordLogin(LOGIN, RECORDED_AT)).thenReturn(1);

        repository.recordLoginCreatingRowIfMissing(LOGIN, RECORDED_AT);

        verify(repository).recordLogin(LOGIN, RECORDED_AT);
        verify(repository, never()).findUserIdByLogin(anyString());
        verify(repository, never()).findByUserId(anyLong());
        verify(repository, never()).save(any());
    }

    @Test
    void aLoginCreatesTheRowForAnAccountThatHasNoneYet() {
        UserActivityRepository repository = repositoryRunningItsDefaultMethods();
        when(repository.recordLogin(LOGIN, RECORDED_AT)).thenReturn(0);
        when(repository.findUserIdByLogin(LOGIN)).thenReturn(Optional.of(USER_ID));
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(repository.save(any(UserActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        repository.recordLoginCreatingRowIfMissing(LOGIN, RECORDED_AT);

        ArgumentCaptor<UserActivity> saved = ArgumentCaptor.forClass(UserActivity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getLastLoginDate()).isEqualTo(RECORDED_AT);
    }

    /**
     * The row the loser of the race built is discarded, and the timestamp is applied by the update the winner has just
     * made possible, so it is not lost.
     */
    @Test
    void aLoginRetriesTheUpdateWhenAnotherWriterCreatesTheRowFirst() {
        UserActivityRepository repository = repositoryRunningItsDefaultMethods();
        when(repository.recordLogin(LOGIN, RECORDED_AT)).thenReturn(0, 1);
        when(repository.findUserIdByLogin(LOGIN)).thenReturn(Optional.of(USER_ID));
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(repository.save(any(UserActivity.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        repository.recordLoginCreatingRowIfMissing(LOGIN, RECORDED_AT);

        verify(repository, times(2)).recordLogin(LOGIN, RECORDED_AT);
    }

    /**
     * A violation that leaves the update still matching nothing is not a lost race, so it must reach the caller instead of
     * being swallowed as if the timestamp had been written.
     */
    @Test
    void aViolationThatIsNotALostRaceReachesTheCaller() {
        UserActivityRepository repository = repositoryRunningItsDefaultMethods();
        when(repository.recordLogin(LOGIN, RECORDED_AT)).thenReturn(0);
        when(repository.findUserIdByLogin(LOGIN)).thenReturn(Optional.of(USER_ID));
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(repository.save(any(UserActivity.class))).thenThrow(new DataIntegrityViolationException("not null constraint"));

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() -> repository.recordLoginCreatingRowIfMissing(LOGIN, RECORDED_AT));
    }

    /**
     * An account can be deleted between authenticating and recording the login, and that must not fail the request that
     * is only writing a timestamp.
     */
    @Test
    void nothingIsRecordedForALoginThatMatchesNoAccount() {
        UserActivityRepository repository = repositoryRunningItsDefaultMethods();
        when(repository.recordLogin(LOGIN, RECORDED_AT)).thenReturn(0);
        when(repository.findUserIdByLogin(LOGIN)).thenReturn(Optional.empty());

        repository.recordLoginCreatingRowIfMissing(LOGIN, RECORDED_AT);

        verify(repository, never()).save(any());
    }

    @Test
    void aDeletionWarningCreatesTheRowForAnAccountThatHasNoneYet() {
        UserActivityRepository repository = repositoryRunningItsDefaultMethods();
        when(repository.recordDeletionWarning(LOGIN, RECORDED_AT)).thenReturn(0);
        when(repository.findUserIdByLogin(LOGIN)).thenReturn(Optional.of(USER_ID));
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(repository.save(any(UserActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        repository.recordDeletionWarningCreatingRowIfMissing(LOGIN, RECORDED_AT);

        ArgumentCaptor<UserActivity> saved = ArgumentCaptor.forClass(UserActivity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getDeletionWarningSentDate()).isEqualTo(RECORDED_AT);
        verify(repository, never()).recordLogin(anyString(), any());
    }

    /**
     * The credentials change is keyed on the account id rather than the login, so it reaches the shared recovery without
     * resolving a login first.
     */
    @Test
    void aCredentialsChangeCreatesTheRowWithoutResolvingALogin() {
        UserActivityRepository repository = repositoryRunningItsDefaultMethods();
        when(repository.recordCredentialsChanged(USER_ID, RECORDED_AT)).thenReturn(0);
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(repository.save(any(UserActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        repository.recordCredentialsChangedCreatingRowIfMissing(USER_ID, RECORDED_AT);

        ArgumentCaptor<UserActivity> saved = ArgumentCaptor.forClass(UserActivity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getCredentialsChangedDate()).isEqualTo(RECORDED_AT);
        verify(repository, never()).findUserIdByLogin(anyString());
    }

    @Test
    void aCredentialsChangeRetriesTheUpdateWhenAnotherWriterCreatesTheRowFirst() {
        UserActivityRepository repository = repositoryRunningItsDefaultMethods();
        when(repository.recordCredentialsChanged(USER_ID, RECORDED_AT)).thenReturn(0, 1);
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(repository.save(any(UserActivity.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        repository.recordCredentialsChangedCreatingRowIfMissing(USER_ID, RECORDED_AT);

        verify(repository, times(2)).recordCredentialsChanged(USER_ID, RECORDED_AT);
    }

    /**
     * A row that exists but was not matched by the update - the account was given one between the two statements - is
     * updated in place rather than replaced by a fresh row, so the timestamps already on it survive.
     */
    @Test
    void anExistingRowIsUpdatedInPlaceRatherThanReplaced() {
        UserActivityRepository repository = repositoryRunningItsDefaultMethods();
        UserActivity existingRow = new UserActivity(USER_ID);
        existingRow.setLastLoginDate(RECORDED_AT.minusSeconds(60));
        when(repository.recordCredentialsChanged(USER_ID, RECORDED_AT)).thenReturn(0);
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(existingRow));
        when(repository.save(any(UserActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        repository.recordCredentialsChangedCreatingRowIfMissing(USER_ID, RECORDED_AT);

        ArgumentCaptor<UserActivity> saved = ArgumentCaptor.forClass(UserActivity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue()).as("the change is applied to the row that already exists, not to a new one").isSameAs(existingRow);
        assertThat(existingRow.getLastLoginDate()).as("a timestamp already on the row is not cleared").isEqualTo(RECORDED_AT.minusSeconds(60));
    }
}
