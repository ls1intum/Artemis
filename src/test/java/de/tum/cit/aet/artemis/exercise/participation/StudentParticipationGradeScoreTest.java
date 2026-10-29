package de.tum.cit.aet.artemis.exercise.participation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.CourseGradeScoreDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Pins which submission and which result of a participation the course grade score queries report.
 * <p>
 * Those queries select the relevant row by excluding every newer one with a correlated {@code NOT EXISTS}. The rules
 * are easy to break while reformulating them, so each of them is characterised here:
 * <ul>
 * <li>the latest submission of a participation wins,</li>
 * <li>the latest result of that submission wins,</li>
 * <li>except for quizzes, where the <i>first</i> submission wins,</li>
 * <li>and a requested AI feedback of a programming exercise, which only copies a test score, never wins.</li>
 * </ul>
 */
class StudentParticipationGradeScoreTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "gradescorequery";

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    private ZonedDateTime now;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        now = ZonedDateTime.now();
    }

    @Test
    void shouldReportOnlyTheResultOfTheLatestSubmission() {
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise(TEST_PREFIX + "latest-submission");
        TextExercise exercise = (TextExercise) course.getExercises().iterator().next();
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
        addTextSubmissionWithResult(participation, now.minusHours(3), now.minusHours(2), 50.0);
        addTextSubmissionWithResult(participation, now.minusHours(1), now.minusMinutes(30), 80.0);

        var grades = studentParticipationRepository.findIndividualGradesByCourseId(course.getId());

        assertThat(grades).extracting(CourseGradeScoreDTO::score).containsExactly(80.0);
    }

    @Test
    void shouldReportOnlyTheLatestResultOfTheLatestSubmission() {
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise(TEST_PREFIX + "latest-result");
        TextExercise exercise = (TextExercise) course.getExercises().iterator().next();
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
        var submission = addTextSubmissionWithResult(participation, now.minusHours(1), now.minusMinutes(50), 50.0);
        participationUtilService.addResultToSubmission(submission, AssessmentType.MANUAL, null, 80.0, true, now.minusMinutes(10));

        var grades = studentParticipationRepository.findIndividualGradesByCourseId(course.getId());

        assertThat(grades).extracting(CourseGradeScoreDTO::score).containsExactly(80.0);
    }

    /**
     * A student requests AI feedback before the first build finishes. The AI feedback copies the score of that moment (0), and its
     * generation finishes after the build reported 60%, so it is the result completed last.
     */
    @Test
    void shouldNotReportARequestedAiFeedbackOfAProgrammingExercise() {
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        ProgrammingExercise exercise = (ProgrammingExercise) course.getExercises().iterator().next();
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
        var submission = new ProgrammingSubmission();
        submission.setSubmissionDate(now.minusHours(1));
        submission.setSubmitted(true);
        var savedSubmission = participationUtilService.addSubmission(participation, submission);
        participationUtilService.addResultToSubmission(savedSubmission, AssessmentType.AUTOMATIC_ATHENA, null, 0.0, true, now.minusMinutes(10));
        participationUtilService.addResultToSubmission(savedSubmission, AssessmentType.AUTOMATIC, null, 60.0, true, now.minusMinutes(30));
        long studentId = userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId();

        assertThat(studentParticipationRepository.findIndividualGradesByCourseId(course.getId())).extracting(CourseGradeScoreDTO::score).containsExactly(60.0);
        assertThat(studentParticipationRepository.findIndividualGradesByCourseIdAndStudentId(Set.of(course.getId()), studentId)).extracting(CourseGradeScoreDTO::score)
                .containsExactly(60.0);
        assertThat(studentParticipationRepository.findIndividualGradesForCourseOverview(Set.of(exercise.getId()), studentId, now)).extracting(CourseGradeScoreDTO::score)
                .containsExactly(60.0);
    }

    /**
     * Quiz submissions are stored in reverse order, so the grade queries deliberately pick the earliest submission of a
     * quiz participation instead of the latest one.
     */
    @Test
    void shouldReportTheFirstSubmissionForQuizzes() {
        Course course = quizExerciseUtilService.addCourseWithOneQuizExercise(TEST_PREFIX + "first-submission");
        QuizExercise exercise = (QuizExercise) course.getExercises().iterator().next();
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
        addQuizSubmissionWithResult(participation, now.minusHours(3), now.minusHours(2), 30.0);
        addQuizSubmissionWithResult(participation, now.minusHours(1), now.minusMinutes(30), 90.0);

        var grades = studentParticipationRepository.findIndividualQuizGradesByCourseId(course.getId());

        assertThat(grades).extracting(CourseGradeScoreDTO::score).containsExactly(30.0);
    }

    private TextSubmission addTextSubmissionWithResult(StudentParticipation participation, ZonedDateTime submissionDate, ZonedDateTime completionDate, double score) {
        var submission = new TextSubmission();
        submission.setSubmissionDate(submissionDate);
        submission.setSubmitted(true);
        var savedSubmission = (TextSubmission) participationUtilService.addSubmission(participation, submission);
        participationUtilService.addResultToSubmission(savedSubmission, AssessmentType.MANUAL, null, score, true, completionDate);
        return savedSubmission;
    }

    private void addQuizSubmissionWithResult(StudentParticipation participation, ZonedDateTime submissionDate, ZonedDateTime completionDate, double score) {
        var submission = new QuizSubmission();
        submission.setSubmissionDate(submissionDate);
        submission.setSubmitted(true);
        var savedSubmission = participationUtilService.addSubmission(participation, submission);
        participationUtilService.addResultToSubmission(savedSubmission, AssessmentType.AUTOMATIC, null, score, true, completionDate);
    }
}
