package de.tum.cit.aet.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.dto.ExamScoresDTO;
import de.tum.cit.aet.artemis.exam.dto.StudentExamWithGradeDTO;
import de.tum.cit.aet.artemis.exam.service.StudentExamService;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.service.ExerciseConfigurationService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ExamTestRunResultConsistencyTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "examrunscore";

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private StudentExamService studentExamService;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private StudentExamTestRepository studentExamRepository;

    @Autowired
    private QuizExerciseTestRepository quizExerciseRepository;

    @Autowired
    private StudentParticipationTestRepository participationRepository;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private ResultTestRepository resultRepository;

    @Autowired
    private ExerciseConfigurationService exerciseConfigurationService;

    private QuizExercise quiz;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        var group = examUtilService.addEnrolledExerciseGroupWithExamAndCourse(true, TEST_PREFIX);
        var exam = group.getExam();
        exam.setExamMaxPoints(10);
        exam.setNumberOfExercisesInExam(1);
        examRepository.save(exam);
        quiz = QuizExerciseFactory.createQuizForExam(group);
        quiz.setMaxPoints(10.0);
        quiz = quizExerciseRepository.save(quiz);
        exerciseConfigurationService.initialize(quiz);
    }

    @ParameterizedTest
    @CsvSource({ "false,", "true,", "false,/grade-summary", "true,/grade-summary" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void quizPointsPercentageAndTotalUseTheSameScore(boolean completed, String suffix) throws Exception {
        StudentExam run = createRun();
        var participation = participationRepository.findByExerciseIdAndStudentId(quiz.getId(), run.getUser().getId()).getFirst();
        var submission = submissionRepository.findAllByParticipationId(participation.getId()).getFirst();
        storeScore(submission, 80.0, completed);
        run.setSubmitted(true);
        studentExamRepository.save(run);

        var grade = request.get(examPath() + "/student-exams/" + run.getId() + (suffix == null ? "" : suffix), HttpStatus.OK, StudentExamWithGradeDTO.class);

        assertThat(grade.achievedPointsPerExercise().get(quiz.getId())).isEqualTo(8.0);
        assertThat(grade.studentResult().exerciseGroupIdToExerciseResult().values()).singleElement().satisfies(exercise -> {
            assertThat(exercise.achievedPoints()).isEqualTo(8.0);
            assertThat(exercise.achievedScore()).isEqualTo(80.0);
        });
        assertThat(grade.studentResult().overallPointsAchieved()).isEqualTo(8.0);
        assertThat(grade.studentResult().overallScoreAchieved()).isEqualTo(80.0);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void cohortScoresKeepRegularAndInstructorTestRunGradesSeparate(boolean completed) throws Exception {
        StudentExam run = createRun();
        var testParticipation = participationRepository.findByExerciseIdAndStudentId(quiz.getId(), run.getUser().getId()).getFirst();
        storeScore(submissionRepository.findAllByParticipationId(testParticipation.getId()).getFirst(), 80.0, completed);
        run.setSubmitted(true);
        studentExamRepository.save(run);

        StudentParticipation regularParticipation = new StudentParticipation();
        regularParticipation.setExercise(quiz);
        regularParticipation.setParticipant(run.getUser());
        regularParticipation.setTestRun(false);
        regularParticipation = participationRepository.save(regularParticipation);
        QuizSubmission regularSubmission = new QuizSubmission();
        regularSubmission.setParticipation(regularParticipation);
        storeScore(regularSubmission, 40.0, true);
        StudentExam regularExam = new StudentExam();
        regularExam.setExam(quiz.getExam());
        regularExam.setUser(run.getUser());
        regularExam.setWorkingTime(3600);
        regularExam.setExercises(new ArrayList<>(List.of(quiz)));
        regularExam.setSubmitted(true);
        regularExam.setTestRun(false);
        studentExamRepository.saveAndFlush(regularExam);

        var scores = request.get(examPath() + "/scores", HttpStatus.OK, ExamScoresDTO.class);

        assertThat(scores.studentResults()).hasSize(2).extracting(ExamScoresDTO.StudentResultDTO::overallScoreAchieved).containsExactlyInAnyOrder(40.0, 80.0);
        assertThat(scores.studentResults()).extracting(ExamScoresDTO.StudentResultDTO::overallPointsAchieved).containsExactlyInAnyOrder(4.0, 8.0);
        assertThat(scores.studentResults()).allSatisfy(student -> assertThat(student.exerciseGroupIdToExerciseResult().values()).singleElement().satisfies(exercise -> {
            assertThat(exercise.achievedScore()).isEqualTo(student.overallScoreAchieved());
            assertThat(exercise.achievedPoints()).isEqualTo(student.overallPointsAchieved());
        }));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void repeatedInstructorRunsKeepExistingParticipationBehavior() {
        StudentExam first = createRun();
        StudentExam second = createRun();

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(participationRepository.findByExerciseIdAndStudentId(quiz.getId(), first.getUser().getId())).hasSize(1);
    }

    private void storeScore(Submission submission, double score, boolean completed) {
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission = submissionRepository.save(submission);
        Result result = new Result();
        result.setSubmission(submission);
        result.setExerciseId(quiz.getId());
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setRated(true);
        result.setScore(score);
        result.setCompletionDate(completed ? ZonedDateTime.now() : null);
        resultRepository.saveAndFlush(result);
    }

    private StudentExam createRun() {
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        return studentExamService.createTestRun(quiz.getExam(), List.of(quiz.getId()), 3600);
    }

    private String examPath() {
        return "/api/exam/courses/" + quiz.getExam().getCourse().getId() + "/exams/" + quiz.getExam().getId();
    }
}
