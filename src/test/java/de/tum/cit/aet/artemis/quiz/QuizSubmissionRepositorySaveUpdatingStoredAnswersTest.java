package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;

import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizSubmissionTestRepository;

/**
 * Which failures of the save of an exam submission are repeated: the ones a competing save causes, and only those. The repository is a mock whose default
 * methods run, so that the policy is tested without a database.
 */
class QuizSubmissionRepositorySaveUpdatingStoredAnswersTest {

    private QuizSubmissionTestRepository repository;

    private QuizSubmission submission;

    @BeforeEach
    void setUp() {
        repository = mock(QuizSubmissionTestRepository.class, CALLS_REAL_METHODS);
        doReturn(List.of()).when(repository).findStoredAnswerIdsBySubmissionId(anyLong());
        submission = new QuizSubmission();
        submission.setId(1L);
    }

    private static DataIntegrityViolationException violationOf(ConstraintKind kind) {
        // Spring wraps the exception of Hibernate, which is what tells the kinds of violation apart
        return new DataIntegrityViolationException("violation", new ConstraintViolationException("violation", new SQLException("violation"), "sql", kind, "constraint"));
    }

    @Test
    void repeatsAfterAUniqueConstraintViolationAndReturnsWhatTheRepeatStores() {
        doThrow(violationOf(ConstraintKind.UNIQUE)).doReturn(submission).when(repository).save(any());

        var saved = repository.saveUpdatingStoredAnswers(submission, null);

        assertThat(saved).isSameAs(submission);
        verify(repository, times(2)).save(any());
    }

    @Test
    void givesUpAfterThreeAttemptsAtAUniqueConstraintViolation() {
        var violation = violationOf(ConstraintKind.UNIQUE);
        doThrow(violation).when(repository).save(any());

        assertThatThrownBy(() -> repository.saveUpdatingStoredAnswers(submission, null)).isSameAs(violation);

        verify(repository, times(3)).save(any());
    }

    @Test
    void doesNotRepeatAViolationOfAnotherConstraint() {
        for (ConstraintKind kind : List.of(ConstraintKind.FOREIGN_KEY, ConstraintKind.NOT_NULL, ConstraintKind.CHECK, ConstraintKind.OTHER)) {
            var mockedRepository = mock(QuizSubmissionTestRepository.class, CALLS_REAL_METHODS);
            doReturn(List.of()).when(mockedRepository).findStoredAnswerIdsBySubmissionId(anyLong());
            var violation = violationOf(kind);
            doThrow(violation).when(mockedRepository).save(any());

            assertThatThrownBy(() -> mockedRepository.saveUpdatingStoredAnswers(submission, null)).as("violation of kind %s", kind).isSameAs(violation);

            verify(mockedRepository, times(1)).save(any());
        }
    }

    @Test
    void doesNotRepeatAnIntegrityViolationWithoutAConstraintException() {
        var violation = new DataIntegrityViolationException("violation");
        doThrow(violation).when(repository).save(any());

        assertThatThrownBy(() -> repository.saveUpdatingStoredAnswers(submission, null)).isSameAs(violation);

        verify(repository, times(1)).save(any());
    }

    @Test
    void repeatsAfterALockConflict() {
        doThrow(new DeadlockLoserDataAccessException("deadlock", new SQLException("deadlock"))).doReturn(submission).when(repository).save(any());

        assertThat(repository.saveUpdatingStoredAnswers(submission, null)).isSameAs(submission);

        verify(repository, times(2)).save(any());
    }

    @Test
    void givesUpAfterThreeAttemptsAtALockConflict() {
        doThrow(new ConcurrencyFailureException("conflict")).when(repository).save(any());

        assertThatThrownBy(() -> repository.saveUpdatingStoredAnswers(submission, null)).isInstanceOf(ConcurrencyFailureException.class);

        verify(repository, times(3)).save(any());
    }

    @Test
    void usesTheStoredAnswersTheCallerKnowsForTheFirstAttemptOnly() {
        doThrow(violationOf(ConstraintKind.UNIQUE)).doReturn(submission).when(repository).save(any());

        repository.saveUpdatingStoredAnswers(submission, Map.of(5L, 50L));

        // the repeat has to see what the competing save stored, so it reads the stored answers, and the first attempt did not
        verify(repository, times(1)).findStoredAnswerIdsBySubmissionId(1L);
    }

    @Test
    void readsTheStoredAnswersWhenTheCallerKnowsNone() {
        doReturn(submission).when(repository).save(any());

        repository.saveUpdatingStoredAnswers(submission, null);

        verify(repository, times(1)).findStoredAnswerIdsBySubmissionId(1L);
    }

    @Test
    void readsNothingForAKnownSubmissionOnTheFirstAttempt() {
        doReturn(submission).when(repository).save(any());

        repository.saveUpdatingStoredAnswers(submission, Map.of());

        verify(repository, never()).findStoredAnswerIdsBySubmissionId(anyLong());
    }
}
