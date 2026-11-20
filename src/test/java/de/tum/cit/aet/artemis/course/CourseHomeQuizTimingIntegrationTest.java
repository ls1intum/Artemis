package de.tum.cit.aet.artemis.course;

import static de.tum.cit.aet.artemis.core.config.Constants.QUIZ_GRACE_PERIOD_IN_SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase;
import de.tum.cit.aet.artemis.course.service.home.CourseHomeLearningService;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.service.ExerciseConfigurationService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.repository.QuizBatchRepository;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizSubmissionTestRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

@WithMockUser(username = CourseHomeQuizTimingIntegrationTest.PREFIX + "student1", roles = "USER")
class CourseHomeQuizTimingIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    static final String PREFIX = "homequiztiming";

    @Autowired
    private CourseHomeLearningService learning;

    @Autowired
    private QuizExerciseTestRepository quizzes;

    @Autowired
    private QuizBatchRepository batches;

    @Autowired
    private QuizSubmissionTestRepository submissions;

    @Autowired
    private StudentParticipationTestRepository participations;

    @Autowired
    private ExerciseConfigurationService configurations;

    private Course course;

    private User student;

    private ZonedDateTime now;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(PREFIX, 2, 0, 0, 1);
        course = courseUtilService.createEnrolledCourse(PREFIX);
        student = userUtilService.getUserByLogin(PREFIX + "student1");
        now = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }

    @ParameterizedTest
    @EnumSource(QuizMode.class)
    void activeAttemptUsesItsOwnDeadlineAndExpiresAfterGrace(QuizMode mode) {
        var quiz = quiz(mode, now.plusDays(10));
        var batch = batch(quiz, now.minusMinutes(5));
        join(quiz, batch, student, false);
        if (mode != QuizMode.SYNCHRONIZED) {
            join(quiz, batch(quiz, now.minusMinutes(9)), userUtilService.getUserByLogin(PREFIX + "student2"), false);
        }
        var deadline = batch.getStartTime().plusSeconds(quiz.getDuration());
        var open = learning.load(course.getId(), student, now.toInstant(), now.minusDays(2).toInstant());
        assertThat(open.progress().currentlyDueExercises()).isOne();
        assertThat(open.candidates()).anyMatch(c -> c.welcomeCase() == CourseHomeWelcomeCase.DEADLINE_SOON && c.item().dueAt().equals(deadline.toInstant()));
        assertThat(open.candidates()).allSatisfy(c -> assertThat(c.item().dueAt()).isEqualTo(deadline.toInstant()));
        assertThat(open.nextRefreshAt()).isEqualTo(deadline.toInstant());

        var grace = learning.load(course.getId(), student, deadline.toInstant(), now.minusDays(2).toInstant());
        assertThat(grace.candidates()).anyMatch(c -> c.welcomeCase() == CourseHomeWelcomeCase.IN_PROGRESS);
        assertThat(grace.nextRefreshAt()).isEqualTo(deadline.plusSeconds(QUIZ_GRACE_PERIOD_IN_SECONDS).toInstant());

        var expired = learning.load(course.getId(), student, deadline.plusSeconds(QUIZ_GRACE_PERIOD_IN_SECONDS).toInstant(), now.minusDays(2).toInstant());
        assertThat(expired.candidates()).isEmpty();
        assertThat(expired.progress().currentlyDueExercises()).isZero();
        assertThat(expired.progress().submittedExercises()).isZero();
    }

    @Test
    void unstartedIndividualAttemptRetainsCourseDeadlineAndIgnoresOtherStudentsBatches() {
        var due = now.plusDays(2);
        var quiz = quiz(QuizMode.INDIVIDUAL, due);
        join(quiz, batch(quiz, now.minusMinutes(9)), userUtilService.getUserByLogin(PREFIX + "student2"), false);
        var snapshot = learning.load(course.getId(), student, now.toInstant(), now.minusDays(2).toInstant());
        assertThat(snapshot.candidates()).isNotEmpty().allSatisfy(c -> assertThat(c.item().dueAt()).isEqualTo(due.toInstant()));
    }

    @Test
    void synchronizedBatchDeadlineIsRelevantBeforeTheStudentOpensTheAttempt() {
        var quiz = quiz(QuizMode.SYNCHRONIZED, now.plusDays(10));
        var batch = batch(quiz, now.minusMinutes(5));
        var snapshot = learning.load(course.getId(), student, now.toInstant(), now.minusDays(2).toInstant());
        assertThat(snapshot.candidates()).isNotEmpty().allSatisfy(c -> assertThat(c.item().dueAt()).isEqualTo(batch.getStartTime().plusSeconds(600).toInstant()));
        assertThat(snapshot.nextRefreshAt()).isEqualTo(batch.getStartTime().plusSeconds(600).toInstant());
    }

    @Test
    void quizFeedbackBecomesNewAtPublicationEvenAfterAnInterveningHomeVisit() {
        var publication = now.plusHours(1);
        var quiz = quiz(QuizMode.INDIVIDUAL, publication);
        quiz.setAssessmentDueDate(now.plusDays(1));
        quizzes.saveAndFlush(quiz);
        var submission = join(quiz, batch(quiz, now.minusHours(4)), student, true);
        var result = new Result();
        result.setExerciseId(quiz.getId());
        result.setSubmission(submission);
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setRated(true);
        result.setScore(0.0);
        result.setCompletionDate(now.minusHours(4));
        resultRepository.saveAndFlush(result);
        var since = now.minusHours(1).toInstant();
        var before = learning.load(course.getId(), student, now.toInstant(), since);
        assertThat(before.candidates()).noneMatch(c -> c.welcomeCase() == CourseHomeWelcomeCase.NEW_FEEDBACK);
        assertThat(before.nextRefreshAt()).isEqualTo(publication.toInstant());
        var released = learning.load(course.getId(), student, publication.plusSeconds(1).toInstant(), since);
        assertThat(released.candidates()).filteredOn(c -> c.welcomeCase() == CourseHomeWelcomeCase.NEW_FEEDBACK).singleElement()
                .satisfies(c -> assertThat(c.item().updatedAt()).isEqualTo(publication.toInstant()));
        var seen = learning.load(course.getId(), student, publication.plusMinutes(1).toInstant(), publication.plusSeconds(10).toInstant());
        assertThat(seen.candidates()).noneMatch(c -> c.welcomeCase() == CourseHomeWelcomeCase.NEW_FEEDBACK);
    }

    private QuizExercise quiz(QuizMode mode, ZonedDateTime due) {
        var quiz = QuizExerciseFactory.createQuiz(course, now.minusDays(1), due, mode);
        quiz.setDuration(600);
        quiz.setQuizBatches(Set.of());
        quiz.setAssessmentDueDate(null);
        quiz = quizzes.saveAndFlush(quiz);
        configurations.initialize(quiz);
        return quiz;
    }

    private QuizBatch batch(QuizExercise quiz, ZonedDateTime start) {
        var batch = new QuizBatch();
        batch.setQuizExercise(quiz);
        batch.setStartTime(start);
        return batches.saveAndFlush(batch);
    }

    private QuizSubmission join(QuizExercise quiz, QuizBatch batch, User user, boolean submitted) {
        var participation = new StudentParticipation();
        participation.setExercise(quiz);
        participation.setParticipant(user);
        participation.setInitializationState(InitializationState.INITIALIZED);
        participation.setInitializationDate(batch.getStartTime());
        participation = participations.saveAndFlush(participation);
        var submission = new QuizSubmission();
        submission.setParticipation(participation);
        submission.setQuizBatch(batch.getId());
        submission.setSubmitted(submitted);
        submission.setSubmissionDate(batch.getStartTime());
        return submissions.saveAndFlush(submission);
    }
}
