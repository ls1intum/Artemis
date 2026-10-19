package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveEpisode;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;
import de.tum.cit.aet.artemis.iris.repository.IrisEpisodeWentTerminalException;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisProactiveEpisodeRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisProactiveEpisodeWriteRepositoryImpl;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;

/**
 * Unit tests for the append-with-outcome workflow of the proactive episode's write fragment.
 *
 * <p>
 * The fragment is the transaction boundary, so the way it takes an append back is to throw: an unchecked exception
 * escaping a {@code @Transactional} method rolls its transaction back, which is what
 * {@code TransactionStatus#setRollbackOnly} did while this boundary still lived in the service. These tests assert
 * that the throw happens on exactly the branch that needs it. That the throw does roll the transaction back is
 * Spring's own contract, and {@code IrisProactiveEpisodeRegistryTest} proves against a real database that the
 * interceptor is in the call path at all.
 */
@ExtendWith(MockitoExtension.class)
class IrisProactiveEpisodeWriteRepositoryTest {

    private static final long USER_ID = 3L;

    private static final long EXERCISE_ID = 42L;

    private static final long SESSION_ID = 99L;

    @Mock
    private IrisProactiveEpisodeRepository irisProactiveEpisodeRepository;

    @Mock
    private IrisMessageRepository irisMessageRepository;

    @Mock
    private IrisSessionRepository irisSessionRepository;

    private IrisProactiveEpisodeWriteRepositoryImpl writeRepository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ObjectProvider<IrisProactiveEpisodeRepository> episodes = mock(ObjectProvider.class);
        lenient().when(episodes.getObject()).thenReturn(irisProactiveEpisodeRepository);
        ObjectProvider<IrisSessionRepository> sessions = mock(ObjectProvider.class);
        lenient().when(sessions.getObject()).thenReturn(irisSessionRepository);
        writeRepository = new IrisProactiveEpisodeWriteRepositoryImpl(episodes, irisMessageRepository, sessions);
    }

    @Test
    void appendWithOutcome_whenAForeignOutcomeWonUnderTheLock_takesTheAppendBack() {
        // The terminal check reads the message rows without locking them, so a dismiss can still commit between it
        // and the write it guards. The guarded UPDATE is what detects that: it reports zero rows, and the locking
        // re-read behind it names the outcome that won.
        when(irisProactiveEpisodeRepository.findForUpdate(USER_ID, EXERCISE_ID, "ep-lost")).thenReturn(Optional.empty());
        when(irisMessageRepository.findEpisodeOutcomes("ep-lost", USER_ID, EXERCISE_ID)).thenReturn(List.of());
        when(irisSessionRepository.appendProactiveMessage(SESSION_ID, EXERCISE_ID, "Closing.", "ep-lost")).thenReturn(new IrisMessage());
        when(irisMessageRepository.findEpisodeRowIdsForUserOrderByIdAsc("ep-lost", USER_ID, EXERCISE_ID)).thenReturn(List.of(500L));
        when(irisMessageRepository.setProactiveOutcomeIfNull(500L, IrisProactiveOutcome.RECOVERED)).thenReturn(0);
        when(irisMessageRepository.findEpisodeOutcomesForUpdate("ep-lost", USER_ID, EXERCISE_ID)).thenReturn(List.of(IrisProactiveOutcome.DISMISSED));

        assertThatExceptionOfType(IrisEpisodeWentTerminalException.class)
                .isThrownBy(() -> writeRepository.appendProactiveMessageWithOutcome(SESSION_ID, USER_ID, EXERCISE_ID, "Closing.", "ep-lost", IrisProactiveOutcome.RECOVERED));

        // The session write lock is taken before the episode's message rows are read, never the other way round: the
        // superseded-message delete holds the session row and then takes the message row, and the opposite order
        // deadlocks against it on InnoDB.
        InOrder order = inOrder(irisSessionRepository, irisMessageRepository);
        order.verify(irisSessionRepository).findByIdWithWriteLockElseThrow(SESSION_ID);
        order.verify(irisMessageRepository).findEpisodeOutcomes("ep-lost", USER_ID, EXERCISE_ID);
    }

    @Test
    void appendWithOutcome_whenTheEpisodeIsAlreadyTerminal_appendsNothingAndCommits() {
        // Nothing has been written at this point, so this branch returns rather than throws: it must commit, exactly
        // as the transaction template did before the boundary moved into the repository.
        var terminal = new IrisProactiveEpisode();
        terminal.setOutcome(IrisProactiveOutcome.DISMISSED);
        when(irisProactiveEpisodeRepository.findForUpdate(USER_ID, EXERCISE_ID, "ep-done")).thenReturn(Optional.of(terminal));

        var outcome = writeRepository.appendProactiveMessageWithOutcome(SESSION_ID, USER_ID, EXERCISE_ID, "Too late.", "ep-done", IrisProactiveOutcome.RECOVERED);

        assertThat(outcome.terminal()).isTrue();
        assertThat(outcome.message()).isNull();
        verify(irisSessionRepository, never()).appendProactiveMessage(anyLong(), anyLong(), any(), any());
    }

    @Test
    void appendWithOutcome_whenTheOutcomeApplies_keepsTheAppend() {
        var episode = new IrisProactiveEpisode();
        episode.setId(7L);
        var appended = new IrisMessage();
        when(irisProactiveEpisodeRepository.findForUpdate(USER_ID, EXERCISE_ID, "ep-ok")).thenReturn(Optional.of(episode));
        when(irisSessionRepository.appendProactiveMessage(SESSION_ID, EXERCISE_ID, "Well done.", "ep-ok")).thenReturn(appended);
        when(irisMessageRepository.findEpisodeRowIdsForUserOrderByIdAsc("ep-ok", USER_ID, EXERCISE_ID)).thenReturn(List.of(600L));
        when(irisMessageRepository.findEpisodeOutcomes("ep-ok", USER_ID, EXERCISE_ID)).thenReturn(List.of());
        when(irisMessageRepository.setProactiveOutcomeIfNull(600L, IrisProactiveOutcome.RECOVERED)).thenReturn(1);

        var outcome = writeRepository.appendProactiveMessageWithOutcome(SESSION_ID, USER_ID, EXERCISE_ID, "Well done.", "ep-ok", IrisProactiveOutcome.RECOVERED);

        assertThat(outcome.terminal()).isFalse();
        assertThat(outcome.message()).isSameAs(appended);
        verify(irisProactiveEpisodeRepository).setOutcomeIfNull(7L, IrisProactiveOutcome.RECOVERED);
    }

    @Test
    void appendWithoutEpisodeId_takesNoEpisodeLockAndRecordsNoOutcome() {
        var appended = new IrisMessage();
        when(irisSessionRepository.appendProactiveMessage(SESSION_ID, EXERCISE_ID, "Loose hint.", null)).thenReturn(appended);

        var outcome = writeRepository.appendProactiveMessageWithOutcome(SESSION_ID, USER_ID, EXERCISE_ID, "Loose hint.", null, null);

        assertThat(outcome.message()).isSameAs(appended);
        verify(irisProactiveEpisodeRepository, never()).findForUpdate(anyLong(), anyLong(), eq("ep-none"));
        verify(irisMessageRepository, never()).setProactiveOutcomeIfNull(anyLong(), any());
    }
}
