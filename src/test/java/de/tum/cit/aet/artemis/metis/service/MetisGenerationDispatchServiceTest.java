package de.tum.cit.aet.artemis.metis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.metis.domain.MetisCheck;
import de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus;
import de.tum.cit.aet.artemis.metis.dto.MetisActiveExerciseDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisCheckCandidateDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisExerciseFactsDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisGenerateResultDTO;
import de.tum.cit.aet.artemis.metis.repository.MetisCheckRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisExerciseConfigRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisQuestionRepository;
import de.tum.cit.aet.artemis.metis.service.MetisAnswerRatingService.BackfillOutcome;
import de.tum.cit.aet.artemis.metis.service.MetisQuestionGenerationService.Outcome;
import de.tum.cit.aet.artemis.metis.util.MetisTestProperties;

/**
 * Unit tests of the paths of the dispatcher that the integration test cannot reach with one node and a synchronous executor: the second insert of the checks
 * after a concurrent inserter won, an executor that refuses a task, a database that fails while a check is claimed, the longest pause of the circuit breaker,
 * and the attempts of a check after its last one.
 * The repositories, the services and the executor are test doubles.
 */
class MetisGenerationDispatchServiceTest {

    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-03-02T10:00:00Z");

    private static final long EXERCISE_ID = 2L;

    private static final long COURSE_ID = 1L;

    private final MetisExerciseConfigRepository configRepository = mock(MetisExerciseConfigRepository.class);

    private final MetisCheckRepository checkRepository = mock(MetisCheckRepository.class);

    private final MetisQuestionRepository questionRepository = mock(MetisQuestionRepository.class);

    private final MetisAiConsentService consentService = mock(MetisAiConsentService.class);

    private final MetisLlmService llmService = mock(MetisLlmService.class);

    private final MetisQuestionGenerationService generationService = mock(MetisQuestionGenerationService.class);

    private final MetisAnswerRatingService ratingService = mock(MetisAnswerRatingService.class);

    private final TestExecutor executor = new TestExecutor();

    private final MetisGenerationDispatchService dispatchService = new MetisGenerationDispatchService(MetisTestProperties.defaults(), configRepository, checkRepository,
            questionRepository, consentService, llmService, generationService, ratingService, executor);

    @BeforeEach
    void setUp() {
        TimeUtil.setClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
        when(llmService.isConfigured()).thenReturn(true);
        when(consentService.consentingUsers(any())).thenAnswer(invocation -> Set.copyOf(invocation.<Collection<Long>>getArgument(0)));
    }

    @AfterEach
    void tearDown() {
        TimeUtil.resetClock();
    }

    @Nested
    class CheckCreation {

        private static final List<MetisCheckCandidateDTO> CANDIDATES = List.of(new MetisCheckCandidateDTO(11L, 21L), new MetisCheckCandidateDTO(12L, 22L),
                new MetisCheckCandidateDTO(13L, 23L));

        private final List<List<MetisCheck>> insertedBatches = new ArrayList<>();

        @BeforeEach
        void setUpExercise() {
            when(configRepository.findExerciseFacts(EXERCISE_ID))
                    .thenReturn(Optional.of(new MetisExerciseFactsDTO(EXERCISE_ID, COURSE_ID, ExerciseMode.INDIVIDUAL, NOW.minusDays(1), "Sorting", "sort")));
            when(configRepository.existsByExerciseIdAndEnabledTrue(EXERCISE_ID)).thenReturn(true);
        }

        static Stream<RuntimeException> concurrentInsertFailures() {
            // A duplicate key, and a deadlock between two batch inserts, which is no integrity violation
            return Stream.of(new DataIntegrityViolationException("duplicate key"), new CannotAcquireLockException("deadlock"));
        }

        @ParameterizedTest
        @MethodSource("concurrentInsertFailures")
        void shouldInsertFreshChecksForTheCandidatesThatAreStillMissingAfterAConcurrentInsert(RuntimeException concurrentInsert) {
            // Another node stored the check of the first participation meanwhile, so the candidate query no longer returns it
            when(checkRepository.findCheckCandidates(eq(EXERCISE_ID), any())).thenReturn(CANDIDATES, CANDIDATES.subList(1, 3));
            failFirstInsertsWith(concurrentInsert, 1);

            MetisGenerateResultDTO result = dispatchService.generateNow(EXERCISE_ID);

            assertThat(result.createdChecks()).isEqualTo(2);
            assertThat(insertedBatches).hasSize(2);
            assertThat(insertedBatches.getLast()).extracting(MetisCheck::getParticipationId).containsExactly(12L, 13L);
            // The failed batch got ids before the database refused it; the second insert must not reuse those objects
            assertThat(insertedBatches.getLast()).allSatisfy(check -> {
                assertThat(check.getId()).isNull();
                assertThat(check.getStatus()).isEqualTo(MetisCheckStatus.PENDING);
            });
        }

        @Test
        void shouldSkipTheSecondInsertWhenTheConcurrentInserterCreatedEveryCheck() {
            when(checkRepository.findCheckCandidates(eq(EXERCISE_ID), any())).thenReturn(CANDIDATES, List.of());
            failFirstInsertsWith(new DataIntegrityViolationException("duplicate key"), 1);

            assertThat(dispatchService.generateNow(EXERCISE_ID).createdChecks()).isZero();
            assertThat(insertedBatches).hasSize(1);
        }

        @Test
        void shouldLeaveTheChecksToTheNextSweepWhenTheSecondInsertFailsAsWell() {
            when(checkRepository.findCheckCandidates(eq(EXERCISE_ID), any())).thenReturn(CANDIDATES, CANDIDATES.subList(1, 3));
            failFirstInsertsWith(new CannotAcquireLockException("deadlock"), 2);

            assertThat(dispatchService.generateNow(EXERCISE_ID).createdChecks()).isZero();
            assertThat(insertedBatches).hasSize(2);
        }

        @Test
        void shouldInsertNothingInASweepThatFindsNoCandidate() {
            when(configRepository.findActiveExercises(any())).thenReturn(List.of(new MetisActiveExerciseDTO(EXERCISE_ID, COURSE_ID, NOW.minusDays(1))));
            when(checkRepository.findCheckCandidates(eq(EXERCISE_ID), any())).thenReturn(CANDIDATES, List.of());
            failFirstInsertsWith(new DataIntegrityViolationException("unused"), 0);

            dispatchService.dispatch(NOW);
            dispatchService.dispatch(NOW.plusSeconds(30));

            verify(checkRepository, times(2)).findCheckCandidates(eq(EXERCISE_ID), any());
            assertThat(insertedBatches).hasSize(1);
        }

        /**
         * Records every batch of checks the dispatcher inserts. The first {@code failures} batches get ids, as a failed insert assigns them, and are then
         * refused with the given exception; the others are stored.
         */
        private void failFirstInsertsWith(RuntimeException failure, int failures) {
            doAnswer(invocation -> {
                List<MetisCheck> batch = new ArrayList<>();
                invocation.<Iterable<MetisCheck>>getArgument(0).forEach(batch::add);
                insertedBatches.add(batch);
                if (insertedBatches.size() <= failures) {
                    long id = 100L;
                    for (MetisCheck check : batch) {
                        check.setId(id++);
                    }
                    throw failure;
                }
                return batch;
            }).when(checkRepository).saveAll(any());
        }
    }

    @Nested
    class RefusedTasks {

        @Test
        void shouldPutARefusedGenerationBackIntoTheQueueAndFreeItsSlot() {
            when(checkRepository.findClaimableCheckIds(any(), any())).thenReturn(List.of(7L), List.of(7L), List.of(8L), List.of());
            when(checkRepository.claimForGeneration(anyLong(), anyLong())).thenReturn(1);
            executor.mode = TestExecutor.Mode.REFUSE;

            dispatchService.dispatch(NOW);

            verify(checkRepository).releaseGeneration(eq(7L), anyLong());
            verify(generationService, never()).generate(anyLong(), anyLong(), anyBoolean());

            // Both slots are free again: the next dispatch hands two checks to the executor, and only then runs out of slots
            executor.mode = TestExecutor.Mode.KEEP;
            dispatchService.dispatch(NOW.plusSeconds(30));

            assertThat(executor.kept).hasSize(2);
            verify(checkRepository, times(2)).claimForGeneration(eq(7L), anyLong());
            verify(checkRepository).claimForGeneration(eq(8L), anyLong());
            verify(checkRepository, times(3)).findClaimableCheckIds(any(), any());
        }

        @Test
        void shouldOfferARefusedRatingAgainAndFreeItsSlot() {
            when(questionRepository.findUnratedAnsweredQuestionIds(any(), any(), any(), any())).thenReturn(List.of(5L), List.of(5L), List.of(6L), List.of());
            executor.mode = TestExecutor.Mode.REFUSE;

            dispatchService.dispatch(NOW);

            verify(ratingService, never()).rateStoredAnswer(anyLong());

            executor.mode = TestExecutor.Mode.KEEP;
            dispatchService.dispatch(NOW.plusSeconds(30));

            assertThat(executor.kept).hasSize(2);
            // The refused answer is neither in flight nor given up on, so the next claim does not exclude it
            ArgumentCaptor<Collection<Long>> excluded = excludedIdsCaptor();
            verify(questionRepository, times(3)).findUnratedAnsweredQuestionIds(any(), any(), excluded.capture(), any());
            assertThat(excluded.getAllValues().get(1)).doesNotContain(5L);
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private static ArgumentCaptor<Collection<Long>> excludedIdsCaptor() {
            return (ArgumentCaptor) ArgumentCaptor.forClass(Collection.class);
        }
    }

    @Nested
    class FailedTasks {

        @Test
        void shouldPutACheckBackIntoTheQueueWhoseTaskFailedBeforeItStoredTheOutcome() {
            when(checkRepository.findClaimableCheckIds(any(), any(Pageable.class))).thenReturn(List.of(7L), List.of());
            when(checkRepository.claimForGeneration(eq(7L), anyLong())).thenReturn(1);
            // The database fails while the generation stores the outcome, so the check is still marked as being generated
            when(generationService.generate(eq(7L), anyLong(), anyBoolean())).thenThrow(new DataAccessResourceFailureException("The database connection was lost"));
            when(checkRepository.releaseGeneration(eq(7L), anyLong())).thenThrow(new DataAccessResourceFailureException("The database is still unavailable")).thenReturn(1);

            dispatchService.dispatch(NOW);
            verify(checkRepository, never()).releaseGeneration(eq(7L), anyLong());

            // The first attempt meets the database still down, the second one puts the check back into the queue, and no later tick tries again
            dispatchService.dispatch(NOW.plusSeconds(30));
            dispatchService.dispatch(NOW.plusSeconds(60));
            dispatchService.dispatch(NOW.plusSeconds(90));

            // The run and every release name the claim the check was taken under, which every write of the run requires
            ArgumentCaptor<Long> claim = ArgumentCaptor.forClass(Long.class);
            verify(checkRepository).claimForGeneration(eq(7L), claim.capture());
            verify(generationService).generate(7L, claim.getValue(), false);
            verify(checkRepository, times(2)).releaseGeneration(7L, claim.getValue());
        }

        @Test
        void shouldPutARefusedGenerationBackIntoTheQueueOnceTheDatabaseTakesTheWrite() {
            when(checkRepository.findClaimableCheckIds(any(), any(Pageable.class))).thenReturn(List.of(7L), List.of());
            when(checkRepository.claimForGeneration(eq(7L), anyLong())).thenReturn(1);
            when(checkRepository.releaseGeneration(eq(7L), anyLong())).thenThrow(new DataAccessResourceFailureException("The database connection was lost")).thenReturn(1);
            executor.mode = TestExecutor.Mode.REFUSE;

            dispatchService.dispatch(NOW);
            dispatchService.dispatch(NOW.plusSeconds(30));
            dispatchService.dispatch(NOW.plusSeconds(60));

            verify(checkRepository, times(2)).releaseGeneration(eq(7L), anyLong());
            verify(generationService, never()).generate(anyLong(), anyLong(), anyBoolean());
        }
    }

    @Nested
    class RatingBackfill {

        private static final long QUESTION_ID = 5L;

        private boolean rated;

        @BeforeEach
        void offerTheAnswerUntilRatedUnlessExcluded() {
            // As the query does: the answer is offered while it is unrated and neither in flight, given up on nor waiting
            when(questionRepository.findUnratedAnsweredQuestionIds(any(), any(), any(), any()))
                    .thenAnswer(invocation -> rated || invocation.<Collection<Long>>getArgument(2).contains(QUESTION_ID) ? List.of() : List.of(QUESTION_ID));
        }

        @Test
        void shouldCountTheFailuresBeforeAndAfterAGapInTheConsentTogether() {
            when(ratingService.rateStoredAnswer(QUESTION_ID)).thenReturn(BackfillOutcome.FAILED, BackfillOutcome.FAILED, BackfillOutcome.NO_CONSENT, BackfillOutcome.FAILED);

            for (int attempt = 0; attempt < 5; attempt++) {
                dispatchAt(NOW.plus(MetisGenerationDispatchService.RATING_RETRY_DELAY.multipliedBy(attempt)));
            }

            // The third failure gives up on the answer, although the student withdrew the consent in between
            verify(ratingService, times(4)).rateStoredAnswer(QUESTION_ID);
        }

        @Test
        void shouldOfferTheAnswerOfAStudentWithoutConsentAgainAfterEveryWaitUntilItIsRated() {
            when(ratingService.rateStoredAnswer(QUESTION_ID))
                    .thenReturn(BackfillOutcome.NO_CONSENT, BackfillOutcome.NO_CONSENT, BackfillOutcome.NO_CONSENT, BackfillOutcome.NO_CONSENT).thenAnswer(invocation -> {
                        rated = true;
                        return BackfillOutcome.RATED;
                    });

            dispatchAt(NOW);
            dispatchAt(NOW.plus(MetisGenerationDispatchService.RATING_RETRY_DELAY).minusSeconds(1));
            verify(ratingService, times(1)).rateStoredAnswer(QUESTION_ID);

            // More waits than failures allowed: a missing consent never gives up on the answer
            for (int attempt = 1; attempt <= 4; attempt++) {
                dispatchAt(NOW.plus(MetisGenerationDispatchService.RATING_RETRY_DELAY.multipliedBy(attempt)));
            }

            verify(ratingService, times(5)).rateStoredAnswer(QUESTION_ID);
        }

        @Test
        void shouldStartTheBackoffOverOnceTheModelRatesAnAnswerAgain() {
            long otherQuestionId = 6L;
            offerTheAnswerUntilRatedThen(otherQuestionId);
            when(ratingService.rateStoredAnswer(QUESTION_ID)).thenReturn(BackfillOutcome.UNAVAILABLE, BackfillOutcome.UNAVAILABLE).thenAnswer(invocation -> {
                rated = true;
                return BackfillOutcome.RATED;
            });
            when(ratingService.rateStoredAnswer(otherQuestionId)).thenReturn(BackfillOutcome.UNAVAILABLE);

            // Two outages in a row pause for 60 and 120 seconds; then the model rates the first answer, and the second answer meets another outage
            dispatchAt(NOW);
            dispatchAt(NOW.plusSeconds(60));
            dispatchAt(NOW.plusSeconds(180));
            verify(ratingService, times(3)).rateStoredAnswer(QUESTION_ID);
            verify(ratingService).rateStoredAnswer(otherQuestionId);

            // The rating by the model closed the circuit, so this outage pauses for 60 seconds again instead of 240
            dispatchAt(NOW.plusSeconds(180 + 59));
            verify(ratingService).rateStoredAnswer(otherQuestionId);
            dispatchAt(NOW.plusSeconds(180 + 60));
            verify(ratingService, times(2)).rateStoredAnswer(otherQuestionId);
        }

        @Test
        void shouldKeepTheBackoffWhenAnAnswerIsRatedWithoutTheModel() {
            long otherQuestionId = 6L;
            offerTheAnswerUntilRatedThen(otherQuestionId);
            // Another writer rated the first answer during the outage, so the backfill rates it without the model
            when(ratingService.rateStoredAnswer(QUESTION_ID)).thenReturn(BackfillOutcome.UNAVAILABLE).thenAnswer(invocation -> {
                rated = true;
                return BackfillOutcome.RATED_WITHOUT_MODEL;
            });
            when(ratingService.rateStoredAnswer(otherQuestionId)).thenReturn(BackfillOutcome.UNAVAILABLE);

            dispatchAt(NOW);
            dispatchAt(NOW.plusSeconds(60));
            verify(ratingService, times(2)).rateStoredAnswer(QUESTION_ID);
            verify(ratingService).rateStoredAnswer(otherQuestionId);

            // That rating says nothing about the endpoint, so the outage after it pauses for the doubled 120 seconds instead of 60 again
            dispatchAt(NOW.plusSeconds(60 + 60));
            verify(ratingService).rateStoredAnswer(otherQuestionId);
            dispatchAt(NOW.plusSeconds(60 + 120));
            verify(ratingService, times(2)).rateStoredAnswer(otherQuestionId);
        }

        /**
         * Offers the first answer until it is rated, then a second one, each unless excluded. With doAnswer, because when() would call the answer of the set-up
         * with null arguments.
         */
        private void offerTheAnswerUntilRatedThen(long otherQuestionId) {
            doAnswer(invocation -> {
                Collection<Long> excluded = invocation.getArgument(2);
                if (!rated && !excluded.contains(QUESTION_ID)) {
                    return List.of(QUESTION_ID);
                }
                return rated && !excluded.contains(otherQuestionId) ? List.of(otherQuestionId) : List.of();
            }).when(questionRepository).findUnratedAnsweredQuestionIds(any(), any(), any(), any());
        }

        private void dispatchAt(ZonedDateTime time) {
            TimeUtil.setClock(Clock.fixed(time.toInstant(), ZoneOffset.UTC));
            dispatchService.dispatch(time);
        }
    }

    @Nested
    class CircuitBreaker {

        @BeforeEach
        void setUpUnavailableEndpoint() {
            when(checkRepository.findClaimableCheckIds(any(), any(Pageable.class))).thenReturn(List.of(7L));
            when(checkRepository.claimForGeneration(eq(7L), anyLong())).thenReturn(1);
            when(generationService.generate(eq(7L), anyLong(), anyBoolean())).thenReturn(Outcome.LLM_UNAVAILABLE);
        }

        @Test
        void shouldDoubleThePauseUpToFifteenMinutes() {
            ZonedDateTime time = NOW;
            int calls = 0;
            // 60, 120, 240 and 480 seconds, then the cap of 15 minutes (900 seconds) instead of 960 seconds, and the cap again instead of 1920 seconds
            for (int pauseSeconds : List.of(60, 120, 240, 480, 900, 900)) {
                dispatchAt(time);
                verify(generationService, times(++calls)).generate(eq(7L), anyLong(), anyBoolean());

                dispatchAt(time.plusSeconds(pauseSeconds - 1));
                verify(generationService, times(calls)).generate(eq(7L), anyLong(), anyBoolean());
                time = time.plusSeconds(pauseSeconds);
            }
        }

        @Test
        void shouldPauseAfterTheLastAttemptAndCountAfreshWhenTheCheckIsQueuedAgain() {
            // The configuration allows three attempts; the third one fails the check, and the endpoint is just as unavailable for the next check
            dispatchAt(NOW);
            dispatchAt(NOW.plusSeconds(60));
            dispatchAt(NOW.plusSeconds(180));

            verify(generationService, times(2)).generate(eq(7L), anyLong(), eq(false));
            verify(generationService).generate(eq(7L), anyLong(), eq(true));

            dispatchAt(NOW.plusSeconds(180 + 239));
            verify(generationService, times(3)).generate(eq(7L), anyLong(), anyBoolean());

            // "Generate now" queued the failed check again: its attempts start from the beginning
            dispatchAt(NOW.plusSeconds(180 + 240));
            verify(generationService, times(3)).generate(eq(7L), anyLong(), eq(false));
        }

        private void dispatchAt(ZonedDateTime time) {
            TimeUtil.setClock(Clock.fixed(time.toInstant(), ZoneOffset.UTC));
            dispatchService.dispatch(time);
        }
    }

    /**
     * An executor that runs a task at once, keeps it without running it, or refuses it, as the test sets it.
     */
    private static final class TestExecutor implements Executor {

        private enum Mode {
            RUN, KEEP, REFUSE
        }

        private Mode mode = Mode.RUN;

        private final List<Runnable> kept = new ArrayList<>();

        @Override
        public void execute(Runnable task) {
            switch (mode) {
                case RUN -> task.run();
                case KEEP -> kept.add(task);
                case REFUSE -> throw new RejectedExecutionException("The queue is full");
            }
        }
    }
}
