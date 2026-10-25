package de.tum.cit.aet.artemis.exercise.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

@Isolated
class TeamParticipationConcurrencyTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "teamstartconcurrency";

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private StudentParticipationTestRepository participationRepository;

    @Autowired
    private ParticipationService participationService;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Test
    void concurrentTeamStartRecoversTheInitializedParticipationAfterAnInsertConflict() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 1);
        var course = courseUtilService.addEnrolledCourseWithModelingAndTextExercise(TEST_PREFIX);
        var exercise = (ModelingExercise) course.getExercises().stream().filter(ModelingExercise.class::isInstance).findFirst().orElseThrow();
        exercise.setMode(ExerciseMode.TEAM);
        exerciseRepository.saveAndFlush(exercise);
        var team = new Team();
        team.setShortName(TEST_PREFIX);
        team.setExercise(exercise);
        team.addStudents(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        team.addStudents(userUtilService.getUserByLogin(TEST_PREFIX + "student2"));
        team = teamRepository.saveAndFlush(team);
        long teamId = team.getId();
        long exerciseId = exercise.getId();

        // Keep coordination local to this isolated test without changing the shared Spring context's bean definitions.
        var participationRepository = mock(StudentParticipationTestRepository.class, delegatesTo(this.participationRepository));
        var targetService = AopTestUtils.getUltimateTargetObject(participationService);
        ReflectionTestUtils.setField(targetService, "studentParticipationRepository", participationRepository);
        try {
            var firstLookup = new CountDownLatch(1);
            var secondEntered = new CountDownLatch(1);
            var creatorInserted = new CountDownLatch(1);
            var insertRejected = new CountDownLatch(1);
            var creatorFinished = new CountDownLatch(1);
            var firstThread = new AtomicReference<Thread>();
            var secondThread = new AtomicReference<Thread>();
            var secondLookups = new AtomicInteger();
            var insertConflict = new AtomicReference<DataIntegrityViolationException>();
            var participationQueries = mockingDetails(participationRepository).getMockCreationSettings().getDefaultAnswer();

            // Hold the first empty lookup until the second has also read no participation.
            // The recovery lookup waits for the winner's initialized row, isolating the insert-conflict path from submission-initialization races.
            doAnswer(invocation -> {
                if (Thread.currentThread() == secondThread.get() && secondLookups.incrementAndGet() > 1) {
                    assertThat(creatorFinished.await(10, TimeUnit.SECONDS)).as("winning request completed before the recovery lookup").isTrue();
                    return participationQueries.answer(invocation);
                }
                Object result = participationQueries.answer(invocation);
                if (Thread.currentThread() == firstThread.get()) {
                    firstLookup.countDown();
                    assertThat(secondEntered.await(10, TimeUnit.SECONDS)).as("second team member reached the start operation").isTrue();
                }
                else if (Thread.currentThread() == secondThread.get()) {
                    secondEntered.countDown();
                }
                return result;
            }).when(participationRepository).findWithEagerSubmissionsAndTeamStudentsByExerciseIdAndTeamId(exerciseId, teamId);

            // Keep the committed winner UNINITIALIZED until the second insert actually violates the database constraint.
            doAnswer(invocation -> {
                StudentParticipation participation = invocation.getArgument(0);
                boolean initialInsert = participation.getId() == null;
                if (initialInsert && Thread.currentThread() == secondThread.get()) {
                    assertThat(creatorInserted.await(10, TimeUnit.SECONDS)).as("winning participation was inserted before the competing insert").isTrue();
                }
                try {
                    Object result = participationQueries.answer(invocation);
                    if (initialInsert && Thread.currentThread() == firstThread.get()) {
                        creatorInserted.countDown();
                        assertThat(insertRejected.await(10, TimeUnit.SECONDS)).as("competing insert was rejected while the winner was UNINITIALIZED").isTrue();
                    }
                    return result;
                }
                catch (DataIntegrityViolationException conflict) {
                    if (initialInsert && Thread.currentThread() == secondThread.get()) {
                        insertConflict.set(conflict);
                        insertRejected.countDown();
                    }
                    throw conflict;
                }
            }).when(participationRepository).saveAndFlush(any(StudentParticipation.class));

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var first = executor.submit(() -> {
                    firstThread.set(Thread.currentThread());
                    try {
                        return startParticipation(exerciseId, "student1");
                    }
                    finally {
                        creatorFinished.countDown();
                    }
                });
                assertThat(firstLookup.await(10, TimeUnit.SECONDS)).as("first team member read the initial participation state").isTrue();
                var second = executor.submit(() -> {
                    secondThread.set(Thread.currentThread());
                    return startParticipation(exerciseId, "student2");
                });

                var firstParticipation = first.get(20, TimeUnit.SECONDS);
                var secondParticipation = second.get(20, TimeUnit.SECONDS);
                assertThat(insertConflict.get()).as("the competing insert reached the real database uniqueness constraint").isNotNull();
                assertThat(firstParticipation.getId()).isEqualTo(secondParticipation.getId());
                assertThat(firstParticipation.getInitializationState()).isEqualTo(InitializationState.INITIALIZED);
                assertThat(secondParticipation.getInitializationState()).isEqualTo(InitializationState.INITIALIZED);
                assertThat(firstParticipation.getSubmissions()).hasSize(1);
                assertThat(secondParticipation.getSubmissions()).hasSize(1);
                assertThat(startParticipation(exerciseId, "student1").getId()).isEqualTo(firstParticipation.getId());
                assertThat(participationRepository.findByExerciseId(exerciseId)).hasSize(1);
                assertThat(submissionRepository.findAllByParticipationId(firstParticipation.getId())).hasSize(1);
            }
        }
        finally {
            ReflectionTestUtils.setField(targetService, "studentParticipationRepository", this.participationRepository);
        }
    }

    private StudentParticipation startParticipation(long exerciseId, String student) throws Exception {
        userUtilService.changeUser(TEST_PREFIX + student);
        try {
            return request.postWithResponseBody("/api/exercise/exercises/" + exerciseId + "/participations", null, StudentParticipation.class, HttpStatus.CREATED);
        }
        finally {
            TestSecurityContextHolder.clearContext();
        }
    }
}
