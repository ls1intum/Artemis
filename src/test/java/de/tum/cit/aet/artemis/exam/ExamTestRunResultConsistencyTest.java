package de.tum.cit.aet.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;

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
    private TextExerciseRepository textExerciseRepository;

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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void cohortScoresExcludeInstructorTestRunsWithoutChangingTheirSummary(boolean completed) throws Exception {
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

        assertThat(scores.studentResults()).extracting(ExamScoresDTO.StudentResultDTO::overallScoreAchieved, ExamScoresDTO.StudentResultDTO::overallPointsAchieved)
                .containsExactly(tuple(40.0, 4.0));
        assertThat(scores.averagePointsAchieved()).isEqualTo(4.0);
        assertThat(scores.studentResults()).allSatisfy(student -> assertThat(student.exerciseGroupIdToExerciseResult().values()).singleElement().satisfies(exercise -> {
            assertThat(exercise.achievedScore()).isEqualTo(student.overallScoreAchieved());
            assertThat(exercise.achievedPoints()).isEqualTo(student.overallPointsAchieved());
        }));
        assertGrade(run, "/grade-summary", quiz.getId(), 80.0, 8.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void instructorTestRunsDoNotCreateCohortRowsOrAverages() throws Exception {
        StudentExam run = createRun();
        var participation = participationRepository.findByExerciseIdAndStudentId(quiz.getId(), run.getUser().getId()).getFirst();
        storeScore(submissionRepository.findAllByParticipationId(participation.getId()).getFirst(), 80.0, true);
        run.setSubmitted(true);
        studentExamRepository.save(run);

        var scores = request.get(examPath() + "/scores", HttpStatus.OK, ExamScoresDTO.class);

        assertThat(scores.studentResults()).isNullOrEmpty();
        assertThat(scores.averagePointsAchieved()).isZero();
        assertThat(scores.exerciseGroups()).singleElement().satisfies(group -> assertThat(group.numberOfParticipants()).isZero());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "/grade-summary" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void completedTestRunAssessmentIsNotCountedAgainOrReplacedByADraft(String suffix) throws Exception {
        var text = TextExerciseFactory.generateTextExerciseForExam(quiz.getExerciseGroup());
        text.setMaxPoints(10.0);
        text = textExerciseRepository.save(text);
        exerciseConfigurationService.initialize(text);
        StudentExam run = createRun(text.getId());
        var participation = participationRepository.findByExerciseIdAndStudentId(text.getId(), run.getUser().getId()).getFirst();
        var completed = storeScore(submissionRepository.findAllByParticipationId(participation.getId()).getFirst(), 80.0, true);
        completed.setAssessmentType(AssessmentType.AUTOMATIC_ATHENA);
        completed.setSuccessful(true);
        resultRepository.saveAndFlush(completed);
        var draft = storeResult(completed.getSubmission(), 40.0, false);
        draft.setAssessmentType(AssessmentType.MANUAL);
        draft.setAssessor(run.getUser());
        resultRepository.saveAndFlush(draft);
        run.setSubmitted(true);
        studentExamRepository.save(run);

        assertGrade(run, suffix, text.getId(), 80.0, 8.0);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "/grade-summary" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void onlyTheNewestUndatedQuizResultCounts(String suffix) throws Exception {
        StudentExam run = createRun();
        var participation = participationRepository.findByExerciseIdAndStudentId(quiz.getId(), run.getUser().getId()).getFirst();
        var first = storeScore(submissionRepository.findAllByParticipationId(participation.getId()).getFirst(), 80.0, false);
        storeResult(first.getSubmission(), 40.0, false);
        run.setSubmitted(true);
        studentExamRepository.save(run);

        assertGrade(run, suffix, quiz.getId(), 40.0, 4.0);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "/grade-summary" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void equalCompletionDatesSelectOnlyTheNewestResult(String suffix) throws Exception {
        StudentExam run = createRun();
        var participation = participationRepository.findByExerciseIdAndStudentId(quiz.getId(), run.getUser().getId()).getFirst();
        var first = storeScore(submissionRepository.findAllByParticipationId(participation.getId()).getFirst(), 80.0, true);
        var second = storeResult(first.getSubmission(), 40.0, true);
        second.setCompletionDate(first.getCompletionDate());
        resultRepository.saveAndFlush(second);
        run.setSubmitted(true);
        studentExamRepository.save(run);

        assertGrade(run, suffix, quiz.getId(), 40.0, 4.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void repeatedInstructorRunsKeepExistingParticipationBehavior() {
        StudentExam first = createRun();
        StudentExam second = createRun();

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(participationRepository.findByExerciseIdAndStudentId(quiz.getId(), first.getUser().getId())).hasSize(1);
    }

    private void assertGrade(StudentExam run, String suffix, long exerciseId, double score, double points) throws Exception {
        var grade = request.get(examPath() + "/student-exams/" + run.getId() + suffix, HttpStatus.OK, StudentExamWithGradeDTO.class);
        assertThat(grade.achievedPointsPerExercise()).containsEntry(exerciseId, points);
        assertThat(grade.studentResult()).extracting(ExamScoresDTO.StudentResultDTO::overallScoreAchieved, ExamScoresDTO.StudentResultDTO::overallPointsAchieved)
                .containsExactly(score, points);
        assertThat(grade.studentResult().exerciseGroupIdToExerciseResult().values()).singleElement().satisfies(exercise -> {
            assertThat(exercise.exerciseId()).isEqualTo(exerciseId);
            assertThat(exercise.achievedScore()).isEqualTo(score);
            assertThat(exercise.achievedPoints()).isEqualTo(points);
        });
    }

    private Result storeScore(Submission submission, double score, boolean completed) {
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission = submissionRepository.save(submission);
        return storeResult(submission, score, completed);
    }

    private Result storeResult(Submission submission, double score, boolean completed) {
        Result result = new Result();
        result.setSubmission(submission);
        result.setExerciseId(submission.getParticipation().getExercise().getId());
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setRated(true);
        result.setScore(score);
        result.setCompletionDate(completed ? ZonedDateTime.now() : null);
        return resultRepository.saveAndFlush(result);
    }

    private StudentExam createRun() {
        return createRun(quiz.getId());
    }

    private StudentExam createRun(long exerciseId) {
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        return studentExamService.createTestRun(quiz.getExam(), List.of(exerciseId), 3600);
    }

    private String examPath() {
        return "/api/exam/courses/" + quiz.getExam().getCourse().getId() + "/exams/" + quiz.getExam().getId();
    }
}
