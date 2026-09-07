package de.tum.cit.aet.artemis.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.domain.UserAiPreference;
import de.tum.cit.aet.artemis.account.repository.UserAiPreferenceRepository;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * The AI preferences live in {@code user_ai_preference}, where a row exists only for an account that has made a decision
 * or turned Memiris off. That makes "no row" a meaningful state which every reader has to interpret the same way, so
 * these tests pin it: no row means no recorded decision and Memiris enabled.
 * <p>
 * Getting that default wrong flips behaviour for the majority of accounts - 26,648 of 34,354 have no row - which is why
 * it is tested per accessor rather than only through the happy path.
 */
class UserAiPreferenceServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "useraipref";

    // Fixed rather than wall-clock: the recorded moment is asserted in places, and a fixture that moves with the
    // clock cannot be compared against.
    private static final ZonedDateTime DECIDED_AT = ZonedDateTime.parse("2026-01-02T03:04:05Z");

    private static final ZonedDateTime DECIDED_EARLIER = ZonedDateTime.parse("2026-01-01T03:04:05Z");

    @Autowired
    private UserAiPreferenceService userAiPreferenceService;

    @Autowired
    private UserAiPreferenceRepository userAiPreferenceRepository;

    @Autowired
    private UserUtilService userUtilService;

    private User user;

    @BeforeEach
    void setUp() {
        user = userUtilService.createAndSaveUser(TEST_PREFIX + "student1");
        // The fixture records a default decision for every account it creates, so start from a clean slate.
        userAiPreferenceRepository.findByUserId(user.getId()).ifPresent(userAiPreferenceRepository::delete);
    }

    @Nested
    class WithoutARow {

        @Test
        void hasNoRecordedDecision() {
            assertThat(userAiPreferenceService.findDecision(user.getId())).isNull();
            assertThat(userAiPreferenceService.findDecisionDate(user.getId())).isNull();
        }

        @Test
        void hasMemirisEnabled() {
            assertThat(userAiPreferenceService.isMemirisEnabled(user.getId())).as("the column default was true, so no row must read as enabled").isTrue();
        }

        @Test
        void hasNotOptedIntoLlmUsage() {
            assertThat(userAiPreferenceService.hasOptedIntoLlmUsage(user.getId())).isFalse();
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> userAiPreferenceService.hasOptedIntoLlmUsageElseThrow(user.getId()));
        }

        @Test
        void isAbsentFromABatchLookupRatherThanMappedToNull() {
            assertThat(userAiPreferenceService.findDecisions(Set.of(user.getId()))).doesNotContainKey(user.getId());
        }

        @Test
        void clearingIsANoOp() {
            userAiPreferenceService.clearDecision(user.getId());

            assertThat(userAiPreferenceRepository.findByUserId(user.getId())).isEmpty();
        }
    }

    @Nested
    class RecordingADecision {

        @Test
        void storesTheDecisionAndItsDate() {
            ZonedDateTime when = DECIDED_EARLIER;

            userAiPreferenceService.recordDecision(user.getId(), AiSelectionDecision.CLOUD_AI, when);

            assertThat(userAiPreferenceService.findDecision(user.getId())).isEqualTo(AiSelectionDecision.CLOUD_AI);
            assertThat(userAiPreferenceService.findDecisionDate(user.getId())).isNotNull();
        }

        @Test
        void replacesAPreviousDecisionRatherThanAddingARow() {
            userAiPreferenceService.recordDecision(user.getId(), AiSelectionDecision.CLOUD_AI, DECIDED_AT);
            userAiPreferenceService.recordDecision(user.getId(), AiSelectionDecision.LOCAL_AI, DECIDED_AT);

            assertThat(userAiPreferenceService.findDecision(user.getId())).isEqualTo(AiSelectionDecision.LOCAL_AI);
            assertThat(userAiPreferenceRepository.findAll()).filteredOn(preference -> preference.getUserId() == user.getId()).hasSize(1);
        }

        @Test
        void leavesMemirisEnabledUntouched() {
            userAiPreferenceService.recordDecision(user.getId(), AiSelectionDecision.CLOUD_AI, DECIDED_AT);

            assertThat(userAiPreferenceService.isMemirisEnabled(user.getId())).isTrue();
        }
    }

    @Nested
    class OptingIn {

        @Test
        void anAccountThatChoseAnLlmHasOptedIn() {
            userAiPreferenceService.recordDecision(user.getId(), AiSelectionDecision.LOCAL_AI, DECIDED_AT);

            assertThat(userAiPreferenceService.hasOptedIntoLlmUsage(user.getId())).isTrue();
            assertThatNoException().isThrownBy(() -> userAiPreferenceService.hasOptedIntoLlmUsageElseThrow(user.getId()));
        }

        /**
         * NO_AI is a recorded decision but not consent, so it must read as not opted in. Treating any recorded decision as
         * consent would let AI features run for a user who explicitly declined.
         */
        @Test
        void anAccountThatChoseNoAiHasNotOptedIn() {
            userAiPreferenceService.recordDecision(user.getId(), AiSelectionDecision.NO_AI, DECIDED_AT);

            assertThat(userAiPreferenceService.hasOptedIntoLlmUsage(user.getId())).isFalse();
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> userAiPreferenceService.hasOptedIntoLlmUsageElseThrow(user.getId()))
                    .withMessageContaining("not selected to use AI");
        }
    }

    @Nested
    class Memiris {

        @Test
        void canBeTurnedOffAndBackOn() {
            userAiPreferenceService.setMemirisEnabled(user.getId(), false);
            assertThat(userAiPreferenceService.isMemirisEnabled(user.getId())).isFalse();

            userAiPreferenceService.setMemirisEnabled(user.getId(), true);
            assertThat(userAiPreferenceService.isMemirisEnabled(user.getId())).isTrue();
        }

        @Test
        void isIndependentOfTheLlmDecision() {
            userAiPreferenceService.recordDecision(user.getId(), AiSelectionDecision.CLOUD_AI, DECIDED_AT);
            userAiPreferenceService.setMemirisEnabled(user.getId(), false);

            assertThat(userAiPreferenceService.findDecision(user.getId())).isEqualTo(AiSelectionDecision.CLOUD_AI);
            assertThat(userAiPreferenceService.isMemirisEnabled(user.getId())).isFalse();
        }
    }

    @Nested
    class ClearingADecision {

        /**
         * With Memiris still at its default there is nothing left to record, so the row goes away and the account is
         * represented the same way as one that never decided.
         */
        @Test
        void removesTheRowWhenNothingElseIsRecorded() {
            userAiPreferenceService.recordDecision(user.getId(), AiSelectionDecision.CLOUD_AI, DECIDED_AT);

            userAiPreferenceService.clearDecision(user.getId());

            assertThat(userAiPreferenceRepository.findByUserId(user.getId())).isEmpty();
            assertThat(userAiPreferenceService.findDecision(user.getId())).isNull();
            assertThat(userAiPreferenceService.isMemirisEnabled(user.getId())).isTrue();
        }

        /**
         * A Memiris choice of false cannot be expressed by the absence of a row, so the row has to survive.
         */
        @Test
        void keepsTheRowWhenMemirisIsTurnedOff() {
            userAiPreferenceService.recordDecision(user.getId(), AiSelectionDecision.CLOUD_AI, DECIDED_AT);
            userAiPreferenceService.setMemirisEnabled(user.getId(), false);

            userAiPreferenceService.clearDecision(user.getId());

            assertThat(userAiPreferenceRepository.findByUserId(user.getId())).isPresent();
            assertThat(userAiPreferenceService.findDecision(user.getId())).isNull();
            assertThat(userAiPreferenceService.isMemirisEnabled(user.getId())).isFalse();
        }
    }

    /**
     * The batch lookup is the reason this cluster could be extracted at all: assembling a post with its answers reads one
     * decision per answer author, and doing that one query at a time would have been an N+1 on an Iris request path.
     */
    @Nested
    class ConcurrentFirstWrite {

        /**
         * Writing to a row another request created in the meantime still ends with the decision recorded once. This only
         * covers the outcome; the recovery itself needs the insert to actually collide, which
         * {@code UserAiPreferenceServiceConcurrencyTest} arranges.
         */
        @Test
        void recordingADecisionOnAnExistingRowRecordsItOnce() {
            userAiPreferenceRepository.save(new UserAiPreference(user.getId()));

            userAiPreferenceService.recordDecision(user.getId(), AiSelectionDecision.LOCAL_AI, DECIDED_AT);

            assertThat(userAiPreferenceService.findDecision(user.getId())).isEqualTo(AiSelectionDecision.LOCAL_AI);
            assertThat(userAiPreferenceRepository.findAllByUserIdIn(Set.of(user.getId()))).hasSize(1);
        }

        @Test
        void aClearedDecisionIsStillDistinguishableFromNoRow() {
            userAiPreferenceService.setMemirisEnabled(user.getId(), false);
            userAiPreferenceService.recordDecision(user.getId(), AiSelectionDecision.CLOUD_AI, DECIDED_AT);
            userAiPreferenceService.clearDecision(user.getId());

            // The row survives because it still carries the Memiris choice, so "no decision" and "no row" differ here.
            assertThat(userAiPreferenceService.findDecision(user.getId())).isNull();
            assertThat(userAiPreferenceService.hasPreferenceRow(user.getId())).isTrue();
        }
    }

    @Nested
    class BatchLookup {

        @Test
        void returnsOnlyTheAccountsThatRecordedADecision() {
            User decided = userUtilService.createAndSaveUser(TEST_PREFIX + "decided");
            User undecided = userUtilService.createAndSaveUser(TEST_PREFIX + "undecided");
            userAiPreferenceService.recordDecision(decided.getId(), AiSelectionDecision.NO_AI, DECIDED_AT);
            userAiPreferenceService.clearDecision(undecided.getId());

            var decisions = userAiPreferenceService.findDecisions(List.of(decided.getId(), undecided.getId()));

            assertThat(decisions).containsEntry(decided.getId(), AiSelectionDecision.NO_AI).doesNotContainKey(undecided.getId());
        }

        @Test
        void agreesWithTheSingleLookupForEveryAccount() {
            User cloud = userUtilService.createAndSaveUser(TEST_PREFIX + "cloud");
            User local = userUtilService.createAndSaveUser(TEST_PREFIX + "local");
            userAiPreferenceService.recordDecision(cloud.getId(), AiSelectionDecision.CLOUD_AI, DECIDED_AT);
            userAiPreferenceService.recordDecision(local.getId(), AiSelectionDecision.LOCAL_AI, DECIDED_AT);

            var decisions = userAiPreferenceService.findDecisions(List.of(cloud.getId(), local.getId(), user.getId()));

            assertThat(decisions.get(cloud.getId())).isEqualTo(userAiPreferenceService.findDecision(cloud.getId()));
            assertThat(decisions.get(local.getId())).isEqualTo(userAiPreferenceService.findDecision(local.getId()));
            assertThat(decisions.get(user.getId())).isEqualTo(userAiPreferenceService.findDecision(user.getId())).isNull();
        }

        /**
         * A post's answers can repeat an author and can have none at all, so the batch has to cope with duplicates, nulls
         * and an empty input without querying for them.
         */
        @Test
        void toleratesDuplicatesNullsAndAnEmptyInput() {
            userAiPreferenceService.recordDecision(user.getId(), AiSelectionDecision.CLOUD_AI, DECIDED_AT);

            assertThat(userAiPreferenceService.findDecisions(List.of())).isEmpty();
            assertThat(userAiPreferenceService.findDecisions(Arrays.asList(user.getId(), null, user.getId())))
                    .containsExactlyEntriesOf(Map.of(user.getId(), AiSelectionDecision.CLOUD_AI));
        }

        @Test
        void ignoresAccountsThatOnlyTurnedMemirisOff() {
            User memirisOff = userUtilService.createAndSaveUser(TEST_PREFIX + "memirisoff");
            userAiPreferenceService.clearDecision(memirisOff.getId());
            userAiPreferenceService.setMemirisEnabled(memirisOff.getId(), false);

            var decisions = userAiPreferenceService.findDecisions(Set.of(memirisOff.getId()));

            assertThat(decisions).as("a row without a decision must not appear as one").doesNotContainKey(memirisOff.getId());
        }
    }
}
