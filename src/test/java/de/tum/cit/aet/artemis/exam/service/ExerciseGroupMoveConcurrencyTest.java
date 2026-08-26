package de.tum.cit.aet.artemis.exam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Verifies that moving an exam exercise between groups is serialized against test run creation.
 * <p>
 * Both operations take a pessimistic write lock on the exam row. Without it, a test run could resolve its exercises,
 * the move could commit, and the test run could then persist a selection that no longer holds one exercise per group —
 * the move's {@code NOT EXISTS} guard would see no committed student exam and let the move through.
 */
class ExerciseGroupMoveConcurrencyTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "exercisegroupmoveconcurrency";

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private StudentExamTestRepository studentExamRepository;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ExerciseGroupService exerciseGroupService;

    @Autowired
    private StudentExamService studentExamService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ExecutorService executor;

    private Exam exam;

    private List<Long> testRunExerciseIds;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        Course course = courseUtilService.addEmptyCourse();
        exam = examUtilService.addExam(course);
        exam = examUtilService.addExerciseGroupsAndExercisesToExam(exam, false);
        // The client builds this list by picking one exercise per group, which is exactly the invariant the lock protects.
        testRunExerciseIds = exam.getExerciseGroups().stream().map(group -> group.getExercises().iterator().next().getId()).toList();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateTestRunWaitsForTheExamRowLockHeldByAMove() throws Exception {
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        TransactionTemplate lockHolder = new TransactionTemplate(transactionManager);

        // Hold the exam row exactly as ExerciseGroupService#moveExerciseToGroup does, for as long as we choose.
        Future<?> holder = executor.submit(() -> lockHolder.execute(status -> {
            examRepository.findByIdWithPessimisticWriteLockElseThrow(exam.getId());
            lockAcquired.countDown();
            try {
                releaseLock.await(30, TimeUnit.SECONDS);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        assertThat(lockAcquired.await(30, TimeUnit.SECONDS)).as("the lock holder must reach the exam row").isTrue();

        SecurityContext securityContext = SecurityContextHolder.getContext();
        Future<?> testRunCreation = executor.submit(() -> {
            SecurityContextHolder.setContext(securityContext);
            return studentExamService.createTestRun(exam, testRunExerciseIds, 6000);
        });

        // The whole point of the fix: creation must not persist its selection while the move holds the row. We watch the
        // committed StudentExam rather than the method's return, because the save happens early — an unlocked creation
        // commits it in milliseconds, while the rest of the method (participation setup) runs for far longer. Under the
        // lock the row can never appear, so requiring "still absent" to hold for a window cannot make this flaky.
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(studentExamRepository.findAllByExamId_AndTestRunIsTrue(exam.getId()))
                .as("no test run may be committed while the move holds the exam row").isEmpty());
        assertThat(testRunCreation.isDone()).as("test run creation must still be blocked on the exam row lock").isFalse();

        releaseLock.countDown();
        holder.get(30, TimeUnit.SECONDS);
        assertThat(testRunCreation.get(60, TimeUnit.SECONDS)).as("test run creation must proceed once the lock is released").isNotNull();

        // With the test run committed, the guard now sees it and the move is rejected instead of silently desyncing it.
        ExerciseGroup sourceGroup = exam.getExerciseGroups().getFirst();
        ExerciseGroup targetGroup = exam.getExerciseGroups().get(1);
        Exercise movedExercise = sourceGroup.getExercises().iterator().next();
        assertThatThrownBy(() -> exerciseGroupService.moveExerciseToGroup(exam.getId(), movedExercise.getId(), targetGroup.getId())).isInstanceOf(ConflictException.class);
    }
}
