package de.tum.cit.aet.artemis.exam;

import static de.tum.cit.aet.artemis.core.util.WebsocketDestinationMatchers.topic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.core.util.FileSystemLocation;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.dto.ExamDeletionSummaryDTO;
import de.tum.cit.aet.artemis.exam.dto.ExerciseGroupDeletionSummaryDTO;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exam.web.ExamWebsocketTopics;
import de.tum.cit.aet.artemis.exercise.domain.EntityDeletionStatus;
import de.tum.cit.aet.artemis.exercise.domain.EntityDeletionType;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.dto.EntityDeletionProgressDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.service.EntityDeletionProgressService;
import de.tum.cit.aet.artemis.exercise.util.AsyncDeletionTestUtilService;
import de.tum.cit.aet.artemis.exercise.web.ExerciseWebsocketTopics;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class ExamAsyncDeletionIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "examasyncdel";

    private static final int NUMBER_OF_STUDENTS = 2;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private AsyncDeletionTestUtilService asyncDeletionTestUtilService;

    @Autowired
    private EntityDeletionProgressService entityDeletionProgressService;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private ExamUserRepository examUserRepository;

    @Autowired
    private ExerciseTestRepository exerciseRepository;

    @Autowired
    private ChannelRepository channelRepository;

    private Course course;

    private Exam exam;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, 1, 0, 1);
        course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        exam = examUtilService.addExam(course);
        exam = examUtilService.registerUsersForExamAndSaveExam(exam, TEST_PREFIX, NUMBER_OF_STUDENTS);
        examUtilService.addExamChannel(exam, "exam channel");
        // 6 exercise groups with 10 exercises: two each of text, quiz, file upload, modeling and bonus text (the sixth group is empty)
        exam = examUtilService.addExerciseGroupsAndExercisesToExam(exam, false);
    }

    private String examUrl() {
        return "/api/exam/courses/" + course.getId() + "/exams/" + exam.getId();
    }

    private String topicDestination() {
        return "/topic/courses/" + course.getId() + "/exams/" + exam.getId() + "/deletion-progress";
    }

    private List<Long> exerciseIdsOf(Exam exam) {
        return exam.getExerciseGroups().stream().flatMap(group -> group.getExercises().stream()).map(Exercise::getId).toList();
    }

    private List<EntityDeletionProgressDTO> sentProgress() {
        var progress = ArgumentCaptor.forClass(EntityDeletionProgressDTO.class);
        verify(websocketMessagingService, atLeastOnce()).sendMessage(topic(topicDestination()), progress.capture());
        return progress.getAllValues();
    }

    /**
     * Holds the scope of the exam with a deletion that only ends when the test releases it.
     */
    private CountDownLatch startBlockedExamDeletion() throws InterruptedException {
        return startBlockedExamDeletion(List.of());
    }

    /**
     * Holds the scope of the exam and the given further scopes, like the deletion of an exam holds the scopes of its exercises.
     */
    private CountDownLatch startBlockedExamDeletion(List<String> additionalScopeKeys) throws InterruptedException {
        var running = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        entityDeletionProgressService.startAsync(EntityDeletionProgressService.examScopeKey(exam.getId()), additionalScopeKeys, EntityDeletionType.EXAM, exam.getId(),
                ExamWebsocketTopics.DELETION_PROGRESS.at(course.getId(), exam.getId()), 5, 5.0, () -> {
                }, tracker -> {
                    tracker.startStep("exercises");
                    running.countDown();
                    release.await(30, TimeUnit.SECONDS);
                });
        assertThat(running.await(30, TimeUnit.SECONDS)).as("the blocked deletion started").isTrue();
        return release;
    }

    /**
     * Holds the scope of one exercise with a deletion that only ends when the test releases it.
     */
    private CountDownLatch startBlockedExerciseDeletion(long exerciseId) throws InterruptedException {
        var running = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        entityDeletionProgressService.startAsync(EntityDeletionProgressService.exerciseScopeKey(exerciseId), List.of(), EntityDeletionType.EXERCISE, exerciseId,
                ExerciseWebsocketTopics.DELETION_PROGRESS.at(exerciseId), 3, 3.0, () -> {
                }, tracker -> {
                    tracker.startStep("participations");
                    running.countDown();
                    release.await(30, TimeUnit.SECONDS);
                });
        assertThat(running.await(30, TimeUnit.SECONDS)).as("the blocked deletion started").isTrue();
        return release;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testDeleteExamRunsInTheBackgroundAndReportsProgress() throws Exception {
        List<Long> exerciseIds = exerciseIdsOf(examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(exam.getId()));
        assertThat(exerciseIds).hasSize(10);
        assertThat(examUserRepository.findAllByExamId(exam.getId())).hasSize(NUMBER_OF_STUDENTS);

        request.delete(examUrl(), HttpStatus.ACCEPTED);

        EntityDeletionProgressDTO result = asyncDeletionTestUtilService.awaitExamDeletion(exam.getId());
        assertThat(result.status()).isEqualTo(EntityDeletionStatus.COMPLETED);
        assertThat(result.weightedProgressPercent()).isEqualTo(100.0);
        assertThat(examRepository.findById(exam.getId())).as("the exam is deleted").isEmpty();
        assertThat(exerciseRepository.findAllById(exerciseIds)).as("its exercises are deleted").isEmpty();
        assertThat(examUserRepository.findAllByExamId(exam.getId())).as("its registrations are deleted").isEmpty();

        List<EntityDeletionProgressDTO> messages = sentProgress();
        assertThat(messages.getFirst().status()).isEqualTo(EntityDeletionStatus.IN_PROGRESS);
        assertThat(messages.getLast().status()).isEqualTo(EntityDeletionStatus.COMPLETED);
        assertThat(messages).extracting(EntityDeletionProgressDTO::entityType).containsOnly(EntityDeletionType.EXAM);
        assertThat(messages).extracting(EntityDeletionProgressDTO::currentStep).filteredOn(step -> step != null).containsSubsequence("testRuns", "exercises", "gradingScale",
                "exam", "cleanup");
        assertThat(messages).extracting(EntityDeletionProgressDTO::weightedProgressPercent).isSorted();
        assertThat(messages).as("every exercise is reported with its title").filteredOn(message -> message.currentItemTitle() != null).isNotEmpty();
        assertThat(messages).as("the last exercise is reported as deleted")
                .anyMatch(message -> "exercises".equals(message.currentStep()) && message.totalItems() == 10 && message.itemsProcessed() == 10);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testDeleteExerciseGroupRunsInTheBackgroundAndReportsProgress() throws Exception {
        Exam loadedExam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(exam.getId());
        ExerciseGroup group = loadedExam.getExerciseGroups().getFirst();
        List<Long> groupExerciseIds = group.getExercises().stream().map(Exercise::getId).toList();
        assertThat(groupExerciseIds).hasSize(2);

        request.delete(examUrl() + "/exercise-groups/" + group.getId(), HttpStatus.ACCEPTED);

        EntityDeletionProgressDTO result = asyncDeletionTestUtilService.awaitExerciseGroupDeletion(exam.getId(), group.getId());
        assertThat(result.status()).isEqualTo(EntityDeletionStatus.COMPLETED);
        assertThat(result.entityType()).isEqualTo(EntityDeletionType.EXERCISE_GROUP);
        assertThat(result.entityId()).isEqualTo(group.getId());
        assertThat(exerciseRepository.findAllById(groupExerciseIds)).as("the exercises of the group are deleted").isEmpty();
        Exam afterDeletion = examRepository.findByIdWithExerciseGroupsElseThrow(exam.getId());
        assertThat(afterDeletion.getExerciseGroups()).as("the other exercise groups remain").hasSize(5).extracting(ExerciseGroup::getId).doesNotContain(group.getId());
        assertThat(afterDeletion.getExerciseGroups()).as("without gaps").doesNotContainNull();

        List<EntityDeletionProgressDTO> messages = sentProgress();
        assertThat(messages.getLast().status()).isEqualTo(EntityDeletionStatus.COMPLETED);
        assertThat(messages).extracting(EntityDeletionProgressDTO::currentStep).filteredOn(step -> step != null).containsSubsequence("exercises", "exerciseGroup");
        assertThat(messages).as("the last exercise is reported as deleted")
                .anyMatch(message -> "exercises".equals(message.currentStep()) && message.totalItems() == 2 && message.itemsProcessed() == 2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testDeletingWhileTheExamIsBeingDeletedIsRejected() throws Exception {
        Exam loadedExam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(exam.getId());
        ExerciseGroup group = loadedExam.getExerciseGroups().getFirst();
        CountDownLatch release = startBlockedExamDeletion();
        try {
            request.delete(examUrl(), HttpStatus.CONFLICT);
            request.delete(examUrl() + "/exercise-groups/" + group.getId(), HttpStatus.CONFLICT);
            assertThat(examRepository.findById(exam.getId())).as("the rejected requests deleted nothing").isPresent();
            assertThat(exerciseRepository.findAllById(exerciseIdsOf(loadedExam))).hasSize(10);
        }
        finally {
            release.countDown();
        }
        asyncDeletionTestUtilService.awaitExamDeletion(exam.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetDeletionProgress() throws Exception {
        String progressUrl = examUrl() + "/deletion-progress";
        assertThat(request.getNullable(progressUrl, HttpStatus.NO_CONTENT, EntityDeletionProgressDTO.class)).as("nothing runs yet").isNull();

        CountDownLatch release = startBlockedExamDeletion();
        try {
            var progress = request.get(progressUrl, HttpStatus.OK, EntityDeletionProgressDTO.class);
            assertThat(progress.status()).isEqualTo(EntityDeletionStatus.IN_PROGRESS);
            assertThat(progress.entityType()).isEqualTo(EntityDeletionType.EXAM);
            assertThat(progress.entityId()).isEqualTo(exam.getId());
            assertThat(progress.currentStep()).isEqualTo("exercises");
        }
        finally {
            release.countDown();
        }
        asyncDeletionTestUtilService.awaitExamDeletion(exam.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testDeletingTheExamOrAGroupWhileOneOfItsExercisesIsBeingDeletedIsRejected() throws Exception {
        Exam loadedExam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(exam.getId());
        ExerciseGroup group = loadedExam.getExerciseGroups().getFirst();
        Exercise exerciseOfGroup = group.getExercises().iterator().next();
        String groupUrl = examUrl() + "/exercise-groups/" + group.getId();

        CountDownLatch release = startBlockedExerciseDeletion(exerciseOfGroup.getId());
        try {
            request.delete(examUrl(), HttpStatus.CONFLICT);
            request.delete(groupUrl, HttpStatus.CONFLICT);
            assertThat(examRepository.findById(exam.getId())).as("the rejected requests deleted nothing").isPresent();
            assertThat(exerciseRepository.findAllById(exerciseIdsOf(loadedExam))).hasSize(10);
        }
        finally {
            release.countDown();
        }
        asyncDeletionTestUtilService.awaitExerciseDeletion(exerciseOfGroup.getId());

        // The rejected requests released the scope of the exam again, which they had taken before they found the exercise to be busy.
        request.delete(groupUrl, HttpStatus.ACCEPTED);
        asyncDeletionTestUtilService.awaitExerciseGroupDeletion(exam.getId(), group.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testDeletingAnExerciseWhileItsExamIsBeingDeletedIsRejected() throws Exception {
        Exam loadedExam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(exam.getId());
        List<Long> exerciseIds = exerciseIdsOf(loadedExam);
        Exercise textExercise = loadedExam.getExerciseGroups().stream().flatMap(group -> group.getExercises().stream()).filter(TextExercise.class::isInstance).findFirst()
                .orElseThrow();

        // the deletion of an exam holds the scope of each of its exercises
        CountDownLatch release = startBlockedExamDeletion(exerciseIds.stream().map(EntityDeletionProgressService::exerciseScopeKey).toList());
        try {
            request.delete("/api/text/text-exercises/" + textExercise.getId(), HttpStatus.CONFLICT);
            assertThat(exerciseRepository.findById(textExercise.getId())).as("the rejected request deleted nothing").isPresent();
        }
        finally {
            release.countDown();
        }
        asyncDeletionTestUtilService.awaitExamDeletion(exam.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testDeleteExamDeletesTheImagesOfTheRegisteredStudentsInTheBackground() throws Exception {
        Path signaturePath = giveFirstExamUserASignature();

        request.delete(examUrl(), HttpStatus.ACCEPTED);
        asyncDeletionTestUtilService.awaitExamDeletion(exam.getId());

        verify(fileService).schedulePathForDeletion(signaturePath, 0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testFailingImageCleanupDoesNotFailTheDeletionOfAnExamThatIsGone() throws Exception {
        Path signaturePath = giveFirstExamUserASignature();
        doThrow(new IllegalStateException("disk failure")).when(fileService).schedulePathForDeletion(eq(signaturePath), anyLong());

        request.delete(examUrl(), HttpStatus.ACCEPTED);

        EntityDeletionProgressDTO result = asyncDeletionTestUtilService.awaitExamDeletion(exam.getId());
        assertThat(result.status()).as("the exam does not exist any more, so the instructor must not be told that the deletion failed").isEqualTo(EntityDeletionStatus.COMPLETED);
        assertThat(examRepository.findById(exam.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testSummaryAndRetryWorkAfterAnEarlierDeletionRemovedTheChannel() throws Exception {
        // An earlier deletion that failed after its first step already removed the channel of the exam.
        channelRepository.delete(channelRepository.findChannelByExamId(exam.getId()));

        var summary = request.get(examUrl() + "/deletion-summary", HttpStatus.OK, ExamDeletionSummaryDTO.class);
        assertThat(summary.numberOfCommunicationPosts()).isZero();
        assertThat(summary.numberOfAnswerPosts()).isZero();
        assertThat(summary.numberOfExerciseGroups()).isEqualTo(6);

        request.delete(examUrl(), HttpStatus.ACCEPTED);
        asyncDeletionTestUtilService.awaitExamDeletion(exam.getId());
        assertThat(examRepository.findById(exam.getId())).as("the retry deleted the exam").isEmpty();
    }

    private Path giveFirstExamUserASignature() {
        ExamUser examUser = examUserRepository.findAllByExamId(exam.getId()).getFirst();
        examUser.setSigningImagePath("signature-" + exam.getId() + ".png");
        examUserRepository.save(examUser);
        return new FileSystemLocation.ExamUserSignature(examUser.getSigningImagePath()).path();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testExamDeletionEndpointsForbiddenForTutor() throws Exception {
        Exam loadedExam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(exam.getId());
        ExerciseGroup group = loadedExam.getExerciseGroups().getFirst();
        request.get(examUrl() + "/deletion-progress", HttpStatus.FORBIDDEN, EntityDeletionProgressDTO.class);
        request.get(examUrl() + "/deletion-summary", HttpStatus.FORBIDDEN, ExamDeletionSummaryDTO.class);
        request.get(examUrl() + "/exercise-groups/" + group.getId() + "/deletion-summary", HttpStatus.FORBIDDEN, ExerciseGroupDeletionSummaryDTO.class);
        request.delete(examUrl(), HttpStatus.FORBIDDEN);
        request.delete(examUrl() + "/exercise-groups/" + group.getId(), HttpStatus.FORBIDDEN);
        assertThat(examRepository.findById(exam.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testExamDeletionSummaryCountsTheContent() throws Exception {
        Exam loadedExam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(exam.getId());
        Exercise firstExercise = loadedExam.getExerciseGroups().getFirst().getExercises().iterator().next();
        var participation = participationUtilService.createAndSaveParticipationForExercise(firstExercise, TEST_PREFIX + "student1");
        participationUtilService.createSubmissionAndResult(participation, 50L, true);
        examUtilService.addStudentExamWithUser(exam, TEST_PREFIX + "student1");

        var summary = request.get(examUrl() + "/deletion-summary", HttpStatus.OK, ExamDeletionSummaryDTO.class);

        assertThat(summary.numberOfExerciseGroups()).isEqualTo(6);
        assertThat(summary.numberOfTextExercises()).isEqualTo(4);
        assertThat(summary.numberOfQuizExercises()).isEqualTo(2);
        assertThat(summary.numberOfFileUploadExercises()).isEqualTo(2);
        assertThat(summary.numberOfModelingExercises()).isEqualTo(2);
        assertThat(summary.numberOfProgrammingExercises()).isZero();
        assertThat(summary.numberOfStudentParticipations()).isEqualTo(1);
        assertThat(summary.numberOfSubmissions()).isEqualTo(1);
        assertThat(summary.numberOfResults()).isEqualTo(1);
        assertThat(summary.numberRegisteredStudents()).isEqualTo(NUMBER_OF_STUDENTS);
        assertThat(summary.numberNotStartedExams()).isEqualTo(1);
        assertThat(summary.numberOfTestRuns()).isZero();
        assertThat(summary.numberOfComplaints()).isZero();
        assertThat(summary.numberOfPlagiarismComparisons()).isZero();
        assertThat(summary.numberOfExamSessions()).isZero();
        assertThat(summary.numberOfExamLiveEvents()).isZero();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testExerciseGroupDeletionSummaryCountsTheContent() throws Exception {
        Exam loadedExam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(exam.getId());
        ExerciseGroup textGroup = loadedExam.getExerciseGroups().getFirst();
        ExerciseGroup quizGroup = loadedExam.getExerciseGroups().get(1);
        Exercise textExercise = textGroup.getExercises().iterator().next();
        participationUtilService.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student1");
        participationUtilService.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student2");
        examUtilService.addStudentExamWithUser(exam, TEST_PREFIX + "student1");

        var textSummary = request.get(examUrl() + "/exercise-groups/" + textGroup.getId() + "/deletion-summary", HttpStatus.OK, ExerciseGroupDeletionSummaryDTO.class);
        assertThat(textSummary.numberOfTextExercises()).isEqualTo(2);
        assertThat(textSummary.numberOfQuizExercises()).isZero();
        assertThat(textSummary.numberOfStudentParticipations()).isEqualTo(2);
        assertThat(textSummary.numberOfBuilds()).isZero();

        var quizSummary = request.get(examUrl() + "/exercise-groups/" + quizGroup.getId() + "/deletion-summary", HttpStatus.OK, ExerciseGroupDeletionSummaryDTO.class);
        assertThat(quizSummary.numberOfQuizExercises()).isEqualTo(2);
        assertThat(quizSummary.numberOfTextExercises()).isZero();
        assertThat(quizSummary.numberOfStudentParticipations()).isZero();
    }
}
