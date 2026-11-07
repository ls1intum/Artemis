package de.tum.cit.aet.artemis.exercise;

import static de.tum.cit.aet.artemis.core.util.WebsocketDestinationMatchers.topic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.EntityDeletionStatus;
import de.tum.cit.aet.artemis.exercise.domain.EntityDeletionType;
import de.tum.cit.aet.artemis.exercise.dto.EntityDeletionProgressDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.service.DeletionJob;
import de.tum.cit.aet.artemis.exercise.service.EntityDeletionProgressService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.util.AsyncDeletionTestUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.exercise.web.ExerciseWebsocketTopics;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class AsyncExerciseDeletionIntegrationTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "asyncexdel";

    @Autowired
    private RequestUtilService request;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private AsyncDeletionTestUtilService asyncDeletionTestUtilService;

    @Autowired
    private EntityDeletionProgressService entityDeletionProgressService;

    @Autowired
    private DistributedDataProvider distributedDataProvider;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private ExerciseTestRepository exerciseRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    private TextExercise exercise;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 3, 1, 1, 1);
        Course course = textExerciseUtilService.addEnrolledCourseWithOneReleasedTextExercise("Text", TEST_PREFIX);
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
    }

    private String exerciseUrl() {
        return "/api/text/text-exercises/" + exercise.getId();
    }

    private String progressUrl() {
        return "/api/exercise/exercises/" + exercise.getId() + "/deletion-progress";
    }

    private String scopeKey() {
        return EntityDeletionProgressService.exerciseScopeKey(exercise.getId());
    }

    /**
     * Starts a deletion of the exercise with the given job and no further scopes.
     */
    private void start(int totalSteps, DeletionJob job) {
        startHolding(scopeKey(), List.of(), totalSteps, () -> {
        }, job);
    }

    private void startHolding(String scopeKey, List<String> additionalScopeKeys, int totalSteps, Runnable onAccepted, DeletionJob job) {
        entityDeletionProgressService.startAsync(scopeKey, additionalScopeKeys, EntityDeletionType.EXERCISE, exercise.getId(),
                ExerciseWebsocketTopics.DELETION_PROGRESS.at(exercise.getId()), totalSteps, totalSteps, onAccepted, job);
    }

    /**
     * Holds the scopes with a deletion that only ends when the test releases it.
     */
    private CountDownLatch startBlockedDeletion(String scopeKey, List<String> additionalScopeKeys, String step) throws InterruptedException {
        var running = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        startHolding(scopeKey, additionalScopeKeys, 3, () -> {
        }, tracker -> {
            tracker.startStep(step);
            running.countDown();
            release.await(30, TimeUnit.SECONDS);
        });
        assertThat(running.await(30, TimeUnit.SECONDS)).as("the blocked deletion started").isTrue();
        return release;
    }

    private CountDownLatch startBlockedDeletion(String step) throws InterruptedException {
        return startBlockedDeletion(scopeKey(), List.of(), step);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testDeleteExerciseRunsInTheBackgroundAndReportsProgress() throws Exception {
        participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
        participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student2");

        request.delete(exerciseUrl(), HttpStatus.ACCEPTED);

        EntityDeletionProgressDTO result = asyncDeletionTestUtilService.awaitExerciseDeletion(exercise.getId());
        assertThat(result.status()).isEqualTo(EntityDeletionStatus.COMPLETED);
        assertThat(result.weightedProgressPercent()).isEqualTo(100.0);
        assertThat(result.stepsCompleted()).isEqualTo(result.totalSteps());
        assertThat(result.updatedAt()).as("the status carries a heartbeat").isNotNull().isAfterOrEqualTo(result.startedAt());
        assertThat(exerciseRepository.findById(exercise.getId())).as("the exercise is deleted").isEmpty();
        assertThat(studentParticipationRepository.findByExerciseId(exercise.getId())).as("its participations are deleted").isEmpty();

        var progress = ArgumentCaptor.forClass(EntityDeletionProgressDTO.class);
        verify(websocketMessagingService, atLeastOnce()).sendMessage(topic("/topic/exercises/" + exercise.getId() + "/deletion-progress"), progress.capture());
        List<EntityDeletionProgressDTO> messages = progress.getAllValues();
        assertThat(messages.getFirst().status()).isEqualTo(EntityDeletionStatus.IN_PROGRESS);
        assertThat(messages.getLast().status()).isEqualTo(EntityDeletionStatus.COMPLETED);
        assertThat(messages).extracting(EntityDeletionProgressDTO::entityId).containsOnly(exercise.getId());
        assertThat(messages).extracting(EntityDeletionProgressDTO::entityType).containsOnly(EntityDeletionType.EXERCISE);
        assertThat(messages).extracting(EntityDeletionProgressDTO::currentStep).filteredOn(step -> step != null).containsSubsequence("channel", "plagiarism", "participations",
                "exampleSubmissions", "irisSessions", "exercise", "cleanup");
        assertThat(messages).extracting(EntityDeletionProgressDTO::weightedProgressPercent).isSorted();
        assertThat(messages).as("the deletion of the last participation is reported")
                .anyMatch(message -> "participations".equals(message.currentStep()) && message.totalItems() == 2 && message.itemsProcessed() == 2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testDeleteExerciseWhileItIsBeingDeletedIsRejected() throws Exception {
        CountDownLatch release = startBlockedDeletion("participations");
        try {
            request.delete(exerciseUrl(), HttpStatus.CONFLICT);
            assertThat(exerciseRepository.findById(exercise.getId())).as("the rejected request deleted nothing").isPresent();
        }
        finally {
            release.countDown();
        }
        asyncDeletionTestUtilService.awaitExerciseDeletion(exercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testDeleteExerciseWhileItsExamIsBeingDeletedIsRejected() throws Exception {
        // an exam deletion holds the scope of the exam and, in addition, the scope of every one of its exercises
        String examScope = "exam:" + (exercise.getId() + 1_000_000);
        CountDownLatch release = startBlockedDeletion(examScope, List.of(scopeKey()), "exercises");
        try {
            request.delete(exerciseUrl(), HttpStatus.CONFLICT);
            assertThat(exerciseRepository.findById(exercise.getId())).as("the rejected request deleted nothing").isPresent();
        }
        finally {
            release.countDown();
        }
        // the status of the deletion is kept under the scope of the exam
        await().atMost(Duration.ofSeconds(30))
                .until(() -> entityDeletionProgressService.getProgress(examScope).filter(progress -> progress.status() != EntityDeletionStatus.IN_PROGRESS).isPresent());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testRefusedRequestDoesNotRunTheAcceptedHook() throws Exception {
        CountDownLatch release = startBlockedDeletion("participations");
        try {
            var hookRan = new AtomicBoolean(false);
            assertThatThrownBy(() -> startHolding(scopeKey(), List.of(), 1, () -> hookRan.set(true), tracker -> {
            })).isInstanceOf(ConflictException.class);
            assertThat(hookRan).as("a refused request is neither audited nor announced").isFalse();
        }
        finally {
            release.countDown();
        }
        asyncDeletionTestUtilService.awaitExerciseDeletion(exercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testConflictOnAnAdditionalScopeReleasesTheClaimsAlreadyTaken() throws Exception {
        CountDownLatch release = startBlockedDeletion("participations");
        String examScope = "exam:" + (exercise.getId() + 1_000_000);
        try {
            assertThatThrownBy(() -> startHolding(examScope, List.of(scopeKey()), 1, () -> {
            }, tracker -> {
            })).isInstanceOf(ConflictException.class);
        }
        finally {
            release.countDown();
        }
        asyncDeletionTestUtilService.awaitExerciseDeletion(exercise.getId());

        // the refused exam deletion left no claim behind: its scope can be taken right away
        var started = new CountDownLatch(1);
        startHolding(examScope, List.of(), 1, () -> {
        }, tracker -> started.countDown());
        assertThat(started.await(30, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testFailingAcceptedHookDoesNotStartTheDeletionAndReleasesTheScope() throws Exception {
        assertThatThrownBy(() -> startHolding(scopeKey(), List.of(), 1, () -> {
            throw new IllegalStateException("cannot audit");
        }, tracker -> {
        })).isInstanceOf(IllegalStateException.class).hasMessage("cannot audit");
        assertThat(entityDeletionProgressService.getProgress(scopeKey())).as("nothing was announced").isEmpty();

        var started = new CountDownLatch(1);
        start(1, tracker -> started.countDown());
        assertThat(started.await(30, TimeUnit.SECONDS)).as("the scope is free again").isTrue();
        asyncDeletionTestUtilService.awaitExerciseDeletion(exercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetDeletionProgress() throws Exception {
        assertThat(request.getNullable(progressUrl(), HttpStatus.NO_CONTENT, EntityDeletionProgressDTO.class)).as("nothing runs yet").isNull();

        CountDownLatch release = startBlockedDeletion("participations");
        try {
            var progress = request.get(progressUrl(), HttpStatus.OK, EntityDeletionProgressDTO.class);
            assertThat(progress.status()).isEqualTo(EntityDeletionStatus.IN_PROGRESS);
            assertThat(progress.entityType()).isEqualTo(EntityDeletionType.EXERCISE);
            assertThat(progress.entityId()).isEqualTo(exercise.getId());
            assertThat(progress.currentStep()).isEqualTo("participations");
            assertThat(progress.totalSteps()).isEqualTo(3);
            assertThat(progress.updatedAt()).isNotNull();
        }
        finally {
            release.countDown();
        }
        asyncDeletionTestUtilService.awaitExerciseDeletion(exercise.getId());
        var finished = request.get(progressUrl(), HttpStatus.OK, EntityDeletionProgressDTO.class);
        assertThat(finished.status()).as("the outcome stays readable after the deletion ended").isEqualTo(EntityDeletionStatus.COMPLETED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testRunningDeletionWhoseClaimExpiredIsReportedAsInterrupted() throws Exception {
        CountDownLatch release = startBlockedDeletion("participations");
        try {
            // the claim expires when the node of the deletion stops without releasing it
            distributedDataProvider.<String, String>getExpiringMap("entity-deletion-claims", Duration.ofMinutes(10)).remove(scopeKey());

            var progress = request.get(progressUrl(), HttpStatus.OK, EntityDeletionProgressDTO.class);
            assertThat(progress.status()).as("an instructor is not trapped by a deletion that nobody runs any more").isEqualTo(EntityDeletionStatus.FAILED);
            assertThat(progress.errorMessage()).contains("interrupted");
            assertThat(progress.currentStep()).isEqualTo("participations");
        }
        finally {
            release.countDown();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testGetDeletionProgressForbiddenForTutor() throws Exception {
        request.get(progressUrl(), HttpStatus.FORBIDDEN, EntityDeletionProgressDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "USER")
    void testDeleteExerciseForbiddenForEditor() throws Exception {
        request.delete(exerciseUrl(), HttpStatus.FORBIDDEN);
        assertThat(exerciseRepository.findById(exercise.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testFailedDeletionIsReportedAndReleasesTheScope() throws Exception {
        start(2, tracker -> {
            tracker.startStep("channel");
            tracker.completeStep(1.0);
            tracker.startStep("participations");
            throw new IllegalStateException("the repository is gone");
        });

        EntityDeletionProgressDTO failed = asyncDeletionTestUtilService.awaitExerciseDeletionFailure(exercise.getId());
        assertThat(failed.status()).isEqualTo(EntityDeletionStatus.FAILED);
        assertThat(failed.errorMessage()).isEqualTo("the repository is gone");
        assertThat(failed.currentStep()).isEqualTo("participations");
        assertThat(failed.stepsCompleted()).isEqualTo(1);
        assertThat(failed.weightedProgressPercent()).isBetween(0.0, 99.0);

        // A failed deletion does not hold the scope, so the instructor can try again right away: the final status is only visible once the claims are released.
        var retried = new CountDownLatch(1);
        start(1, tracker -> retried.countDown());
        assertThat(retried.await(30, TimeUnit.SECONDS)).isTrue();
        asyncDeletionTestUtilService.awaitExerciseDeletion(exercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testErrorMessageNeverShowsInternals() throws Exception {
        start(1, tracker -> {
            throw new IllegalStateException("could not execute statement [Duplicate entry; SQL [delete from exercise where id=1]]");
        });
        assertThat(asyncDeletionTestUtilService.awaitExerciseDeletionFailure(exercise.getId()).errorMessage()).doesNotContain("SQL").doesNotContain("delete from");

        start(1, tracker -> {
            throw new IllegalStateException("Cannot delete /var/lib/artemis/repos/abc");
        });
        assertThat(asyncDeletionTestUtilService.awaitExerciseDeletionFailure(exercise.getId()).errorMessage()).doesNotContain("/var/lib");

        start(1, tracker -> {
            throw new IllegalStateException("first line\n\tat de.tum.cit.aet.Foo.bar(Foo.java:1)");
        });
        assertThat(asyncDeletionTestUtilService.awaitExerciseDeletionFailure(exercise.getId()).errorMessage()).doesNotContain("\n").doesNotContain("Foo.java");

        start(1, tracker -> {
            throw new IllegalStateException("a ".repeat(400));
        });
        assertThat(asyncDeletionTestUtilService.awaitExerciseDeletionFailure(exercise.getId()).errorMessage()).hasSizeLessThanOrEqualTo(200);

        start(1, tracker -> {
            throw new IllegalStateException((String) null);
        });
        assertThat(asyncDeletionTestUtilService.awaitExerciseDeletionFailure(exercise.getId()).errorMessage()).isNotBlank();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testErrorInTheJobIsReportedAsFailureAndReleasesTheScope() throws Exception {
        start(1, tracker -> {
            throw new AssertionError("an Error is not an Exception");
        });
        EntityDeletionProgressDTO failed = asyncDeletionTestUtilService.awaitExerciseDeletionFailure(exercise.getId());
        assertThat(failed.status()).as("the instructor is not left with a deletion that never ends").isEqualTo(EntityDeletionStatus.FAILED);

        var retried = new CountDownLatch(1);
        start(1, tracker -> retried.countDown());
        assertThat(retried.await(30, TimeUnit.SECONDS)).isTrue();
        asyncDeletionTestUtilService.awaitExerciseDeletion(exercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testRefusedDeleteIsNotAuditedButAnAcceptedOneIs() throws Exception {
        CountDownLatch release = startBlockedDeletion("participations");
        try {
            request.delete(exerciseUrl(), HttpStatus.CONFLICT);
            assertThat(deletionAuditEventsOfThisExercise()).as("a refused request is not logged as a deletion").isZero();
        }
        finally {
            release.countDown();
        }
        asyncDeletionTestUtilService.awaitExerciseDeletion(exercise.getId());

        request.delete(exerciseUrl(), HttpStatus.ACCEPTED);
        asyncDeletionTestUtilService.awaitExerciseDeletion(exercise.getId());
        assertThat(deletionAuditEventsOfThisExercise()).as("an accepted request is logged once").isEqualTo(1);
    }

    /**
     * The audit log is shared by all tests of the run, so only the events about the course of this test count.
     */
    private long deletionAuditEventsOfThisExercise() {
        String courseTitle = exercise.getCourseViaExerciseGroupOrCourseMember().getTitle();
        return auditEventRepository.find(TEST_PREFIX + "instructor1", Instant.EPOCH, Constants.DELETE_EXERCISE).stream()
                .filter(event -> courseTitle.equals(event.getData().get("course"))).count();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testDeleteQuizExerciseCancelsItsScheduleOnlyOnceTheDeletionIsAccepted() throws Exception {
        Course quizCourse = quizExerciseUtilService.addEnrolledCourseWithOneQuizExercise("Quiz", TEST_PREFIX);
        QuizExercise quiz = ExerciseUtilService.getFirstExerciseWithType(quizCourse, QuizExercise.class);
        String quizUrl = "/api/quiz/quiz-exercises/" + quiz.getId();

        // a request that is refused because the quiz is already being deleted must not cancel the schedule of a quiz that still exists
        var running = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        entityDeletionProgressService.startAsync(EntityDeletionProgressService.exerciseScopeKey(quiz.getId()), List.of(), EntityDeletionType.EXERCISE, quiz.getId(),
                ExerciseWebsocketTopics.DELETION_PROGRESS.at(quiz.getId()), 1, 1.0, () -> {
                }, tracker -> {
                    running.countDown();
                    release.await(30, TimeUnit.SECONDS);
                });
        assertThat(running.await(30, TimeUnit.SECONDS)).isTrue();
        try {
            request.delete(quizUrl, HttpStatus.CONFLICT);
            verify(instanceMessageSendService, never()).sendQuizExerciseStartCancel(quiz.getId());
        }
        finally {
            release.countDown();
        }
        asyncDeletionTestUtilService.awaitExerciseDeletion(quiz.getId());

        // the schedule is cancelled in the request, before the deletion starts, so the quiz cannot start while its deletion waits for a free worker
        request.delete(quizUrl, HttpStatus.ACCEPTED);
        verify(instanceMessageSendService).sendQuizExerciseStartCancel(quiz.getId());
        asyncDeletionTestUtilService.awaitExerciseDeletion(quiz.getId());
        assertThat(exerciseRepository.findById(quiz.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testStartingASecondDeletionOfTheSameScopeThrowsConflict() throws Exception {
        CountDownLatch release = startBlockedDeletion("channel");
        try {
            assertThatThrownBy(() -> start(1, tracker -> {
            })).isInstanceOf(ConflictException.class);
        }
        finally {
            release.countDown();
        }
        asyncDeletionTestUtilService.awaitExerciseDeletion(exercise.getId());
        await().atMost(Duration.ofSeconds(30))
                .until(() -> entityDeletionProgressService.getProgress(scopeKey()).filter(p -> p.status() == EntityDeletionStatus.COMPLETED).isPresent());
    }
}
