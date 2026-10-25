package de.tum.cit.aet.artemis.exercise.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
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
    void concurrentTeamStartsReturnTheSameInitializedParticipationWithOneSubmission() throws Exception {
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
        var teamRepository = mock(TeamRepository.class, delegatesTo(this.teamRepository));
        var participationRepository = mock(StudentParticipationTestRepository.class, delegatesTo(this.participationRepository));
        var targetService = AopTestUtils.getUltimateTargetObject(participationService);
        ReflectionTestUtils.setField(targetService, "teamRepository", teamRepository);
        ReflectionTestUtils.setField(targetService, "studentParticipationRepository", participationRepository);
        try {
            var firstLookup = new CountDownLatch(1);
            var secondEntered = new CountDownLatch(1);
            var firstThread = new AtomicReference<Thread>();
            var secondThread = new AtomicReference<Thread>();
            var participationQueries = mockingDetails(participationRepository).getMockCreationSettings().getDefaultAnswer();
            var teamQueries = mockingDetails(teamRepository).getMockCreationSettings().getDefaultAnswer();

            // Without the team lock, hold the first empty lookup until the second has also read no participation.
            // This forces the duplicate creation observed in CI rather than depending on scheduler timing.
            doAnswer(invocation -> {
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

            // With the fix, the second request enters the locking operation before its lookup. Let the first finish;
            // the second then reads the committed participation and submission after acquiring the same database lock.
            doAnswer(invocation -> {
                if (Thread.currentThread() == secondThread.get()) {
                    secondEntered.countDown();
                }
                return teamQueries.answer(invocation);
            }).when(teamRepository).initializeParticipationWithTeamLock(eq(teamId), any());

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var first = executor.submit(() -> {
                    firstThread.set(Thread.currentThread());
                    return startParticipation(exerciseId, "student1");
                });
                assertThat(firstLookup.await(10, TimeUnit.SECONDS)).as("first team member read the initial participation state").isTrue();
                var second = executor.submit(() -> {
                    secondThread.set(Thread.currentThread());
                    return startParticipation(exerciseId, "student2");
                });

                var firstParticipation = first.get(20, TimeUnit.SECONDS);
                var secondParticipation = second.get(20, TimeUnit.SECONDS);
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
            ReflectionTestUtils.setField(targetService, "teamRepository", this.teamRepository);
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
