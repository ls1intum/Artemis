package de.tum.cit.aet.artemis.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

import de.tum.cit.aet.artemis.communication.test_repository.ConversationParticipantTestRepository;

/**
 * Covers how {@code createIfAbsent} treats the outcomes of two requests that create the same participant at once: the unique index refusing the later insert (PostgreSQL), and
 * a deadlock victim (MySQL). The real databases are exercised by {@link ConversationParticipantCreationTest}.
 */
class ConversationParticipantCreateIfAbsentUnitTest {

    private static final long CONVERSATION_ID = 10L;

    private static final long USER_ID = 42L;

    private ConversationParticipantTestRepository repository;

    private final ZonedDateTime lastRead = ZonedDateTime.now();

    @BeforeEach
    void setUp() {
        // the default method is the code under test, the statements are stubbed
        repository = mock(ConversationParticipantTestRepository.class, CALLS_REAL_METHODS);
    }

    @Test
    void createIfAbsent_whenTheInsertCreatesTheParticipant_returnsTrue() {
        doReturn(1).when(repository).insertIfAbsent(CONVERSATION_ID, USER_ID, false, lastRead, 0L);

        assertThat(repository.createIfAbsent(CONVERSATION_ID, USER_ID, false, lastRead, 0L)).isTrue();
    }

    @Test
    void createIfAbsent_whenTheParticipantExists_returnsFalse() {
        doReturn(0).when(repository).insertIfAbsent(CONVERSATION_ID, USER_ID, false, lastRead, 0L);

        assertThat(repository.createIfAbsent(CONVERSATION_ID, USER_ID, false, lastRead, 0L)).isFalse();
    }

    @Test
    void createIfAbsent_whenAConcurrentRequestInsertedFirst_countsItAsExisting() {
        doThrow(new DataIntegrityViolationException("conversation_participant_uq")).when(repository).insertIfAbsent(CONVERSATION_ID, USER_ID, false, lastRead, 0L);
        doReturn(true).when(repository).existsByConversationIdAndUserId(CONVERSATION_ID, USER_ID);

        assertThat(repository.createIfAbsent(CONVERSATION_ID, USER_ID, false, lastRead, 0L)).isFalse();
    }

    @Test
    void createIfAbsent_whenTheInsertFailsForAnotherReason_rethrowsTheError() {
        doThrow(new DataIntegrityViolationException("foreign key violation")).when(repository).insertIfAbsent(CONVERSATION_ID, USER_ID, false, lastRead, 0L);
        doReturn(false).when(repository).existsByConversationIdAndUserId(CONVERSATION_ID, USER_ID);

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() -> repository.createIfAbsent(CONVERSATION_ID, USER_ID, false, lastRead, 0L));
    }

    @Test
    void createIfAbsent_whenTheDeadlockVictimFindsTheParticipant_countsItAsExisting() {
        doThrow(new CannotAcquireLockException("Deadlock found when trying to get lock")).when(repository).insertIfAbsent(CONVERSATION_ID, USER_ID, false, lastRead, 0L);
        doReturn(true).when(repository).existsByConversationIdAndUserId(CONVERSATION_ID, USER_ID);

        assertThat(repository.createIfAbsent(CONVERSATION_ID, USER_ID, false, lastRead, 0L)).isFalse();
        verify(repository, times(1)).insertIfAbsent(CONVERSATION_ID, USER_ID, false, lastRead, 0L);
    }

    @Test
    void createIfAbsent_whenTheDeadlockVictimDoesNotSeeTheParticipantYet_triesAgain() {
        // the other request has not committed when the victim looks, it has by the second attempt
        doThrow(new CannotAcquireLockException("Deadlock found when trying to get lock")).doReturn(0).when(repository).insertIfAbsent(CONVERSATION_ID, USER_ID, false, lastRead,
                0L);
        doReturn(false).when(repository).existsByConversationIdAndUserId(CONVERSATION_ID, USER_ID);

        assertThat(repository.createIfAbsent(CONVERSATION_ID, USER_ID, false, lastRead, 0L)).isFalse();
        verify(repository, times(2)).insertIfAbsent(CONVERSATION_ID, USER_ID, false, lastRead, 0L);
    }

    @Test
    void createIfAbsent_whenTheDeadlockPersists_givesUpAfterThreeAttempts() {
        doThrow(new CannotAcquireLockException("Deadlock found when trying to get lock")).when(repository).insertIfAbsent(CONVERSATION_ID, USER_ID, false, lastRead, 0L);
        doReturn(false).when(repository).existsByConversationIdAndUserId(CONVERSATION_ID, USER_ID);

        assertThatExceptionOfType(CannotAcquireLockException.class).isThrownBy(() -> repository.createIfAbsent(CONVERSATION_ID, USER_ID, false, lastRead, 0L));
        verify(repository, times(3)).insertIfAbsent(CONVERSATION_ID, USER_ID, false, lastRead, 0L);
    }
}
