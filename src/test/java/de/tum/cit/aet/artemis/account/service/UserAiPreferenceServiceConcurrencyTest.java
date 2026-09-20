package de.tum.cit.aet.artemis.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import de.tum.cit.aet.artemis.account.domain.UserAiPreference;
import de.tum.cit.aet.artemis.account.repository.UserAiPreferenceRepository;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;

/**
 * The recovery from a row another request inserted first. Driven against a stubbed repository rather than a database,
 * because the collision has to happen between the service's own lookup and its save - inserting the row beforehand only
 * exercises the ordinary update path, which is what the integration test covers.
 * <p>
 * A plain unit test on purpose: overriding the repository bean would fork the Spring context for every test scheduled
 * after it, which is a large cost for one narrow path.
 */
class UserAiPreferenceServiceConcurrencyTest {

    private static final long USER_ID = 42L;

    private static final ZonedDateTime DECISION_RECORDED_AT = ZonedDateTime.parse("2026-01-02T03:04:05Z");

    @Test
    void aDecisionIsReappliedToTheRowAnotherRequestInsertedFirst() {
        UserAiPreferenceRepository repository = mock(UserAiPreferenceRepository.class);
        UserAiPreference competingRow = new UserAiPreference(USER_ID);
        // Nothing on the first lookup, so the service builds its own row; the competing row appears once the insert fails.
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty(), Optional.of(competingRow));
        when(repository.save(any(UserAiPreference.class))).thenThrow(new DataIntegrityViolationException("duplicate key")).thenAnswer(i -> i.getArgument(0));

        ZonedDateTime when = DECISION_RECORDED_AT;
        new UserAiPreferenceService(repository).recordDecision(USER_ID, AiSelectionDecision.LOCAL_AI, when);

        ArgumentCaptor<UserAiPreference> saved = ArgumentCaptor.forClass(UserAiPreference.class);
        verify(repository, times(2)).save(saved.capture());
        assertThat(saved.getValue()).as("the change is applied to the row that already exists, not to the discarded one").isSameAs(competingRow);
        assertThat(competingRow.getSelectionDecision()).isEqualTo(AiSelectionDecision.LOCAL_AI);
        assertThat(competingRow.getSelectionDecisionDate()).isEqualTo(when);
    }

    /**
     * A violation that is not a lost race must still reach the caller, otherwise a genuine constraint problem would be
     * silently swallowed on the second attempt.
     */
    @Test
    void aViolationThatIsNotALostRaceStillFails() {
        UserAiPreferenceRepository repository = mock(UserAiPreferenceRepository.class);
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(repository.save(any(UserAiPreference.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        UserAiPreferenceService service = new UserAiPreferenceService(repository);

        org.assertj.core.api.Assertions.assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() -> service.setMemirisEnabled(USER_ID, false));
    }
}
