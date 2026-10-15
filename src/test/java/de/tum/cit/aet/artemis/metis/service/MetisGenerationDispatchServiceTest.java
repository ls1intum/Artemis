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
import de.tum.cit.aet.artemis.metis.service.MetisQuestionGenerationService.Outcome;
import de.tum.cit.aet.artemis.metis.util.MetisTestProperties;

/**
 * Unit tests of the paths of the dispatcher that the integration test cannot reach with one node and a synchronous executor: the second insert of the checks
 * after a concurrent inserter won, an executor that refuses a task, the longest pause of the circuit breaker, and the attempts of a check after its last one.
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
            when(checkRepository.claimForGeneration(anyLong())).thenReturn(1);
            executor.mode = TestExecutor.Mode.REFUSE;

            dispatchService.dispatch(NOW);

            verify(checkRepository).releaseGeneration(7L);
            verify(generationService, never()).generate(anyLong(), anyBoolean());

            // Both slots are free again: the next dispatch hands two checks to the executor, and only then runs out of slots
            executor.mode = TestExecutor.Mode.KEEP;
            dispatchService.dispatch(NOW.plusSeconds(30));

            assertThat(executor.kept).hasSize(2);
            verify(checkRepository, times(2)).claimForGeneration(7L);
            verify(checkRepository).claimForGeneration(8L);
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
    class CircuitBreaker {

        @BeforeEach
        void setUpUnavailableEndpoint() {
            when(checkRepository.findClaimableCheckIds(any(), any(Pageable.class))).thenReturn(List.of(7L));
            when(checkRepository.claimForGeneration(7L)).thenReturn(1);
            when(generationService.generate(eq(7L), anyBoolean())).thenReturn(Outcome.LLM_UNAVAILABLE);
        }

        @Test
        void shouldDoubleThePauseUpToFifteenMinutes() {
            ZonedDateTime time = NOW;
            int calls = 0;
            // 60, 120, 240 and 480 seconds, then the cap of 15 minutes (900 seconds) instead of 960 seconds, and the cap again instead of 1920 seconds
            for (int pauseSeconds : List.of(60, 120, 240, 480, 900, 900)) {
                dispatchAt(time);
                verify(generationService, times(++calls)).generate(eq(7L), anyBoolean());

                dispatchAt(time.plusSeconds(pauseSeconds - 1));
                verify(generationService, times(calls)).generate(eq(7L), anyBoolean());
                time = time.plusSeconds(pauseSeconds);
            }
        }

        @Test
        void shouldPauseAfterTheLastAttemptAndCountAfreshWhenTheCheckIsQueuedAgain() {
            // The configuration allows three attempts; the third one fails the check, and the endpoint is just as unavailable for the next check
            dispatchAt(NOW);
            dispatchAt(NOW.plusSeconds(60));
            dispatchAt(NOW.plusSeconds(180));

            verify(generationService, times(2)).generate(7L, false);
            verify(generationService).generate(7L, true);

            dispatchAt(NOW.plusSeconds(180 + 239));
            verify(generationService, times(3)).generate(eq(7L), anyBoolean());

            // "Generate now" queued the failed check again: its attempts start from the beginning
            dispatchAt(NOW.plusSeconds(180 + 240));
            verify(generationService, times(3)).generate(7L, false);
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
