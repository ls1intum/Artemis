package de.tum.cit.aet.artemis.exercise.participation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.CourseGradeScoreDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
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
 * <li>except for quizzes, where the <i>first</i> submission wins.</li>
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
    private ResultTestRepository resultRepository;

    @Autowired
    private TeamRepository teamRepository;

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

    @ParameterizedTest
    @CsvSource(value = { "false,MANUAL", "true,MANUAL", "false,NULL", "true,NULL" }, nullValues = "NULL")
    void shouldKeepOfficialGradesDuringAndAfterAthenaGeneration(boolean teamExercise, AssessmentType officialType) {
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise(TEST_PREFIX + "athena");
        TextExercise exercise = (TextExercise) course.getExercises().iterator().next();
        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        StudentParticipation participation;
        if (teamExercise) {
            Team team = new Team();
            team.setShortName(TEST_PREFIX + "athena");
            team.setExercise(exercise);
            team.addStudents(student);
            team = teamRepository.save(team);
            participation = participationUtilService.addTeamParticipationForExercise(exercise, team.getId());
        }
        else {
            participation = participationUtilService.createAndSaveParticipationForExercise(exercise, student.getLogin());
        }
        var submission = addTextSubmissionWithResult(participation, now.minusHours(1), now.minusMinutes(30), 80.0);
        var official = resultRepository.findDistinctBySubmissionId(submission.getId()).orElseThrow();
        official.setAssessmentType(officialType);
        resultRepository.save(official);
        Result ai = new Result().submission(submission).assessmentType(AssessmentType.AUTOMATIC_ATHENA).rated(true);
        ai.setExerciseId(exercise.getId());
        ai = resultRepository.save(ai);
        assertOfficialGrade(course, exercise, student.getId(), teamExercise);

        ai.setCompletionDate(now.minusMinutes(10));
        ai.setSuccessful(true);
        ai = resultRepository.save(ai);
        assertOfficialGrade(course, exercise, student.getId(), teamExercise);

        ai.setScore(60.0);
        resultRepository.save(ai);
        assertOfficialGrade(course, exercise, student.getId(), teamExercise);

        resultRepository.deleteById(official.getId());
        if (teamExercise) {
            assertThat(studentParticipationRepository.findTeamGradesByCourseId(course.getId())).isEmpty();
            assertThat(studentParticipationRepository.findTeamGradesByCourseIdAndStudentId(Set.of(course.getId()), student.getId())).isEmpty();
            assertThat(studentParticipationRepository.findTeamGradesForCourseOverview(Set.of(exercise.getId()), student.getId())).isEmpty();
        }
        else {
            assertThat(studentParticipationRepository.findIndividualGradesByCourseId(course.getId())).isEmpty();
            assertThat(studentParticipationRepository.findIndividualGradesByCourseIdAndStudentId(Set.of(course.getId()), student.getId())).isEmpty();
            assertThat(studentParticipationRepository.findIndividualGradesForCourseOverview(Set.of(exercise.getId()), student.getId())).isEmpty();
        }
    }

    private void assertOfficialGrade(Course course, TextExercise exercise, long studentId, boolean teamExercise) {
        var courseGrades = teamExercise ? studentParticipationRepository.findTeamGradesByCourseId(course.getId())
                : studentParticipationRepository.findIndividualGradesByCourseId(course.getId());
        var studentGrades = teamExercise ? studentParticipationRepository.findTeamGradesByCourseIdAndStudentId(Set.of(course.getId()), studentId)
                : studentParticipationRepository.findIndividualGradesByCourseIdAndStudentId(Set.of(course.getId()), studentId);
        var overviewGrades = teamExercise ? studentParticipationRepository.findTeamGradesForCourseOverview(Set.of(exercise.getId()), studentId)
                : studentParticipationRepository.findIndividualGradesForCourseOverview(Set.of(exercise.getId()), studentId);
        assertThat(courseGrades).extracting(CourseGradeScoreDTO::score).containsExactly(80.0);
        assertThat(studentGrades).extracting(CourseGradeScoreDTO::score).containsExactly(80.0);
        assertThat(overviewGrades).extracting(CourseGradeScoreDTO::score).containsExactly(80.0);
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
