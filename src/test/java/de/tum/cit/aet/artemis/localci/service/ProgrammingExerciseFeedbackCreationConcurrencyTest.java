package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.assessment.service.FeedbackMessageService;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.localci.dto.BuildJobInterface;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.dto.BuildResultNotification;
import de.tum.cit.aet.artemis.programming.dto.TestCaseBase;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;

/**
 * The recovery from a second result for the same exercise inserting the same test cases first.
 * <p>
 * Results are processed on {@code artemis.continuous-integration.concurrent-result-processing-size} threads (16 by
 * default), and the read of the existing test cases is not atomic with the write, so two passes over one exercise can
 * both decide the same test names are new. The loser then violates the unique index on (test_name, exercise_id), and
 * before this was handled the exception unwound the whole result processing - the build's result was lost, not just its
 * test cases.
 * <p>
 * A plain unit test on purpose: the collision has to happen between the service's own read and its save, which a real
 * database cannot be made to do on demand, and overriding a repository bean would fork the Spring context for every
 * test scheduled afterwards.
 */
class ProgrammingExerciseFeedbackCreationConcurrencyTest {

    private static final long EXERCISE_ID = 42L;

    private static ProgrammingExercise exercise() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(EXERCISE_ID);
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        return exercise;
    }

    private static BuildResultNotification buildResultWith(String... testNames) {
        List<TestCaseBase> tests = List.of(testNames).stream().map(name -> {
            TestCaseBase testCase = mock(TestCaseBase.class);
            when(testCase.name()).thenReturn(name);
            return testCase;
        }).toList();
        BuildJobInterface job = mock(BuildJobInterface.class);
        doReturn(List.of()).when(job).failedTests();
        doReturn(tests).when(job).successfulTests();
        BuildResultNotification buildResult = mock(BuildResultNotification.class);
        doReturn(List.of(job)).when(buildResult).jobs();
        return buildResult;
    }

    private static ProgrammingExerciseTestCase persisted(String name, ProgrammingExercise exercise) {
        ProgrammingExerciseTestCase testCase = new ProgrammingExerciseTestCase().testName(name).weight(1.0).bonusMultiplier(1.0).bonusPoints(0.0).exercise(exercise).active(true)
                .visibility(Visibility.ALWAYS);
        testCase.setId(1L);
        return testCase;
    }

    private record Fixture(ProgrammingExerciseFeedbackCreationService service, ProgrammingExerciseTestCaseTestRepository testCaseRepository) {
    }

    private static Fixture fixture() {
        ProgrammingExerciseTestCaseTestRepository testCaseRepository = mock(ProgrammingExerciseTestCaseTestRepository.class);
        var service = new ProgrammingExerciseFeedbackCreationService(testCaseRepository, mock(WebsocketMessagingService.class), mock(ProgrammingExerciseTaskService.class),
                mock(ProgrammingExerciseRepository.class), mock(StaticCodeAnalysisCategoryRepository.class), mock(FeedbackMessageService.class));
        return new Fixture(service, testCaseRepository);
    }

    /**
     * The losing pass must be a no-op rather than an exception: its test cases are already there, written by the pass
     * that beat it.
     */
    @Test
    void aConcurrentInsertOfTheSameTestCasesIsNotAnError() {
        var fixture = fixture();
        ProgrammingExercise exercise = exercise();
        // Empty on the first read - the same view the winning pass had - and populated once its insert has landed.
        when(fixture.testCaseRepository().findByExerciseId(EXERCISE_ID)).thenReturn(Set.of(), Set.of(persisted("testOne", exercise)));
        when(fixture.testCaseRepository().saveAll(anySet())).thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        fixture.service().generateTestCasesFromBuildResult(buildResultWith("testOne"), exercise);

        // One failed insert, then nothing left to write once the winner's row is visible.
        verify(fixture.testCaseRepository(), times(1)).saveAll(anySet());
        verify(fixture.testCaseRepository(), times(2)).findByExerciseId(EXERCISE_ID);
    }

    /**
     * Only the colliding test cases are dropped on the retry. A name the concurrent pass did not write still has to be
     * inserted, or the exercise would silently end up with an incomplete test-case set - which is worse than the
     * original failure, because the grading race this feeds is silent.
     */
    @Test
    void whatTheConcurrentInsertDidNotCoverIsStillSaved() {
        var fixture = fixture();
        ProgrammingExercise exercise = exercise();
        when(fixture.testCaseRepository().findByExerciseId(EXERCISE_ID)).thenReturn(Set.of(), Set.of(persisted("testOne", exercise)));
        when(fixture.testCaseRepository().saveAll(anySet())).thenThrow(new DataIntegrityViolationException("duplicate key"))
                .thenAnswer(invocation -> List.copyOf(invocation.<Set<ProgrammingExerciseTestCase>>getArgument(0)));

        fixture.service().generateTestCasesFromBuildResult(buildResultWith("testOne", "testTwo"), exercise);

        ArgumentCaptor<Set<ProgrammingExerciseTestCase>> saved = ArgumentCaptor.captor();
        verify(fixture.testCaseRepository(), times(2)).saveAll(saved.capture());
        assertThat(saved.getAllValues().getLast()).extracting(ProgrammingExerciseTestCase::getTestName).containsExactly("testTwo");
    }

    /**
     * A violation that is not this race must still reach the caller. Swallowing it would turn a genuine constraint
     * problem into an exercise that quietly has no test cases.
     */
    @Test
    void aViolationThatIsNotAConcurrentInsertStillFails() {
        var fixture = fixture();
        ProgrammingExercise exercise = exercise();
        // Still empty on the second read, so nothing explains the violation.
        when(fixture.testCaseRepository().findByExerciseId(anyLong())).thenReturn(Set.of());
        when(fixture.testCaseRepository().saveAll(anySet())).thenThrow(new DataIntegrityViolationException("not-null constraint violated"));

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() -> fixture.service().generateTestCasesFromBuildResult(buildResultWith("testOne"), exercise));
    }
}
