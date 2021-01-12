package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;

public class SubmissionServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    CourseService courseService;

    @Autowired
    ExamService examService;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    StudentParticipationRepository studentParticipationRepository;

    @Autowired
    SubmissionService submissionService;

    @Autowired
    UserRepository userRepository;

    private User student1;

    private User tutor1;

    private User tutor2;

    private Course course;

    private TextExercise examTextExercise;

    private ModelingExercise examModelingExercise;

    private ProgrammingExercise examProgrammingExercise;

    private Submission submission1;

    private Submission submission2;

    private Optional<Submission> unassessedSubmissionCorrectionRound0;

    private Optional<Submission> unassessedSubmissionCorrectionRound1;

    private List<Submission> submissionListTutor1CorrectionRound0;

    private List<Submission> submissionListTutor2CorrectionRound0;

    private List<Submission> submissionListTutor1CorrectionRound1;

    private List<Submission> submissionListTutor2CorrectionRound1;

    @BeforeEach
    void init() {
        Exam exam;
        List<User> users = database.addUsers(2, 2, 1);
        student1 = users.get(0);
        tutor1 = users.get(2);
        tutor2 = users.get(3);

        course = database.createCourse();
        exam = database.addExam(course);

        exam.setNumberOfCorrectionRoundsInExam(2);
        exam = examService.save(exam);

        exam = database.addExerciseGroupsAndExercisesToExam(exam, true);

        examTextExercise = (TextExercise) exam.getExerciseGroups().get(0).getExercises().stream().filter(exercise -> exercise instanceof TextExercise).findAny().orElse(null);

        examModelingExercise = (ModelingExercise) exam.getExerciseGroups().get(3).getExercises().stream().filter(exercise -> exercise instanceof ModelingExercise).findAny()
                .orElse(null);
        examProgrammingExercise = (ProgrammingExercise) exam.getExerciseGroups().get(4).getExercises().stream().filter(exercise -> exercise instanceof ProgrammingExercise)
                .findAny().orElse(null);

    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();

        if (submissionListTutor1CorrectionRound0 != null) {
            submissionListTutor1CorrectionRound0.clear();
        }
        if (submissionListTutor2CorrectionRound0 != null) {
            submissionListTutor2CorrectionRound0.clear();
        }
        if (submissionListTutor1CorrectionRound1 != null) {
            submissionListTutor1CorrectionRound1.clear();
        }
        if (submissionListTutor2CorrectionRound1 != null) {
            submissionListTutor2CorrectionRound1.clear();
        }
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCheckSubmissionAllowancegroupCheck() {
        student1.setGroups(Collections.singleton("another-group"));
        userRepository.save(student1);
        Optional<ResponseEntity<Submission>> result = submissionService.checkSubmissionAllowance(examTextExercise, null, student1);
        assertThat(result.isPresent()).isTrue();
        assertThat(result.get()).isEqualTo(forbidden());
    }

    private void queryTestingBasics(Exercise exercise) {
        Exam exam = exercise.getExerciseGroup().getExam();

        exam.setNumberOfCorrectionRoundsInExam(2);

        examService.save(exam);

        submission1.submitted(true);
        submission2.submitted(true);

        if (exercise instanceof ProgrammingExercise) {
            database.addProgrammingSubmission(examProgrammingExercise, (ProgrammingSubmission) submission1, "student1");
            database.addProgrammingSubmission(examProgrammingExercise, (ProgrammingSubmission) submission2, "student1");
        }
        else {
            database.addSubmission(exercise, submission1, "student1");
            database.addSubmission(exercise, submission2, "student2");
        }

    }

    private void loadQueryResults(Exercise exercise) {
        unassessedSubmissionCorrectionRound0 = submissionService.getRandomSubmissionEligibleForNewAssessment(exercise, true, 0L);
        unassessedSubmissionCorrectionRound1 = submissionService.getRandomSubmissionEligibleForNewAssessment(exercise, true, 1L);

        submissionListTutor1CorrectionRound0 = submissionService.getAllSubmissionsAssessedByTutorForCorrectionRoundAndExercise(exercise.getId(), tutor1, true, 0L);
        submissionListTutor2CorrectionRound0 = submissionService.getAllSubmissionsAssessedByTutorForCorrectionRoundAndExercise(exercise.getId(), tutor2, true, 0L);

        submissionListTutor1CorrectionRound1 = submissionService.getAllSubmissionsAssessedByTutorForCorrectionRoundAndExercise(exercise.getId(), tutor1, true, 1L);
        submissionListTutor2CorrectionRound1 = submissionService.getAllSubmissionsAssessedByTutorForCorrectionRoundAndExercise(exercise.getId(), tutor2, true, 1L);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TUTOR")
    public void testProgrammingExerciseGetRandomSubmissionEligibleForNewAssessmentNoAssessments() {

        submission1 = new ProgrammingSubmission();
        submission2 = new ProgrammingSubmission();

        // setup
        queryTestingBasics(this.examProgrammingExercise);

        loadQueryResults(this.examProgrammingExercise);

        assertThat(examProgrammingExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2L);

        assertThat(unassessedSubmissionCorrectionRound0.isPresent()).isTrue();
        assertThat(unassessedSubmissionCorrectionRound0.get()).isIn(submission1, submission2);
        assertThat(unassessedSubmissionCorrectionRound1.isPresent()).isFalse();

        assertThat(submissionListTutor1CorrectionRound0.size()).isEqualTo(0);
        assertThat(submissionListTutor2CorrectionRound0.size()).isEqualTo(0);
        assertThat(submissionListTutor1CorrectionRound1.size()).isEqualTo(0);
        assertThat(submissionListTutor2CorrectionRound1.size()).isEqualTo(0);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TUTOR")
    public void testTextExerciseGetRandomSubmissionEligibleForNewAssessmentNoAssessments() {
        submission1 = new TextSubmission();
        submission2 = new TextSubmission();
        // setup
        queryTestingBasics(this.examTextExercise);

        loadQueryResults(this.examTextExercise);

        assertThat(examTextExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2L);

        assertThat(unassessedSubmissionCorrectionRound0.isPresent()).isTrue();
        assertThat(unassessedSubmissionCorrectionRound0.get()).isIn(submission1, submission2);
        assertThat(unassessedSubmissionCorrectionRound1.isPresent()).isFalse();

        assertThat(submissionListTutor1CorrectionRound0.size()).isEqualTo(0);
        assertThat(submissionListTutor2CorrectionRound0.size()).isEqualTo(0);
        assertThat(submissionListTutor1CorrectionRound1.size()).isEqualTo(0);
        assertThat(submissionListTutor2CorrectionRound1.size()).isEqualTo(0);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TUTOR")
    public void testTextExerciseGetRandomSubmissionEligibleForNewAssessmentOneAssessmentsWithoutLock() {
        submission1 = new TextSubmission();
        submission2 = new TextSubmission();
        // setup
        queryTestingBasics(examTextExercise);

        database.addResultToSubmission(submission1, AssessmentType.MANUAL, tutor1, 10L, true);

        // checks
        loadQueryResults(examTextExercise);

        assertThat(examTextExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2L);

        assertThat(unassessedSubmissionCorrectionRound0.isPresent()).isTrue();
        assertThat(unassessedSubmissionCorrectionRound0.get()).isEqualTo(submission2);
        assertThat(unassessedSubmissionCorrectionRound1.isPresent()).isTrue();
        assertThat(unassessedSubmissionCorrectionRound1.get()).isEqualTo(submission1);

        assertThat(submissionListTutor1CorrectionRound0.size()).isEqualTo(1);
        assertThat(submissionListTutor1CorrectionRound0.get(0)).isEqualTo(submission1);
        assertThat(submissionListTutor2CorrectionRound0.size()).isEqualTo(0);
        assertThat(submissionListTutor1CorrectionRound1.size()).isEqualTo(0);
        assertThat(submissionListTutor2CorrectionRound1.size()).isEqualTo(0);

    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TUTOR")
    public void testTextExerciseGetRandomSubmissionEligibleForNewAssessmentOneAssessmentsWithLock() {
        submission1 = new TextSubmission();
        submission2 = new TextSubmission();
        // setup
        queryTestingBasics(this.examTextExercise);

        Result resultWithLock;

        resultWithLock = submissionService.saveNewEmptyResult(submission1);
        resultWithLock.setAssessor(tutor1);
        resultWithLock.setAssessmentType(AssessmentType.MANUAL);
        resultRepository.save(resultWithLock);

        // checks
        loadQueryResults(examTextExercise);

        assertThat(examTextExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2L);

        assertThat(unassessedSubmissionCorrectionRound0.isPresent()).isTrue();
        assertThat(unassessedSubmissionCorrectionRound0.get()).isEqualTo(submission2);
        assertThat(unassessedSubmissionCorrectionRound1.isPresent()).isFalse();

        assertThat(submissionListTutor1CorrectionRound0.size()).isEqualTo(1);
        assertThat(submissionListTutor1CorrectionRound0.get(0)).isEqualTo(submission1);
        assertThat(submissionListTutor2CorrectionRound0.size()).isEqualTo(0);
        assertThat(submissionListTutor1CorrectionRound1.size()).isEqualTo(0);
        assertThat(submissionListTutor2CorrectionRound1.size()).isEqualTo(0);

    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TUTOR")
    public void testTextExerciseGetRandomSubmissionEligibleForNewAssessmentOneAssessmentsInSecondCorrectionRoundWithoutLock() {
        submission1 = new TextSubmission();
        submission2 = new TextSubmission();
        // setup
        queryTestingBasics(this.examTextExercise);

        database.addResultToSubmission(submission1, AssessmentType.MANUAL, tutor1, 10L, true);
        database.addResultToSubmission(submission1, AssessmentType.MANUAL, tutor2, 20L, true);

        // checks
        loadQueryResults(examTextExercise);

        assertThat(submission1.getResults().size()).isEqualTo(2L);

        assertThat(examTextExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2L);

        assertThat(unassessedSubmissionCorrectionRound0.isPresent()).isTrue();
        assertThat(unassessedSubmissionCorrectionRound0.get()).isEqualTo(submission2);
        assertThat(unassessedSubmissionCorrectionRound1.isPresent()).isFalse();

        assertThat(submissionListTutor1CorrectionRound0.size()).isEqualTo(1);
        assertThat(submissionListTutor1CorrectionRound0.get(0)).isEqualTo(submission1);
        assertThat(submissionListTutor2CorrectionRound0.size()).isEqualTo(0);
        assertThat(submissionListTutor1CorrectionRound1.size()).isEqualTo(0);
        assertThat(submissionListTutor2CorrectionRound1.size()).isEqualTo(1);
        assertThat(submissionListTutor2CorrectionRound1.get(0)).isEqualTo(submission1);

    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TUTOR")
    public void testTextExerciseGetRandomSubmissionEligibleForNewAssessmentOneAssessmentsInSecondCorrectionRoundWithLock() {
        submission1 = new TextSubmission();
        submission2 = new TextSubmission();
        // setup
        queryTestingBasics(this.examTextExercise);

        database.addResultToSubmission(submission1, AssessmentType.MANUAL, tutor1, 10L, true);

        Result resultForSecondCorrectionWithLock;

        resultForSecondCorrectionWithLock = submissionService.saveNewEmptyResult(submission1);
        resultForSecondCorrectionWithLock.setAssessor(tutor2);
        resultForSecondCorrectionWithLock.setAssessmentType(AssessmentType.MANUAL);
        resultRepository.save(resultForSecondCorrectionWithLock);

        // checks
        loadQueryResults(examTextExercise);

        assertThat(submission1.getResults().size()).isEqualTo(2L);

        assertThat(examTextExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2L);

        assertThat(unassessedSubmissionCorrectionRound0.isPresent()).isTrue();
        assertThat(unassessedSubmissionCorrectionRound0.get()).isEqualTo(submission2);
        assertThat(unassessedSubmissionCorrectionRound1.isPresent()).isFalse();

        assertThat(submissionListTutor1CorrectionRound0.size()).isEqualTo(1);
        assertThat(submissionListTutor1CorrectionRound0.get(0)).isEqualTo(submission1);
        assertThat(submissionListTutor2CorrectionRound0.size()).isEqualTo(0);
        assertThat(submissionListTutor1CorrectionRound1.size()).isEqualTo(0);
        assertThat(submissionListTutor2CorrectionRound1.size()).isEqualTo(1);
        assertThat(submissionListTutor2CorrectionRound1.get(0)).isEqualTo(submission1);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TUTOR")
    public void testModelingExerciseGetRandomSubmissionEligibleForNewAssessmentNoAssessments() {
        submission1 = new ModelingSubmission();
        submission2 = new ModelingSubmission();
        // setup
        queryTestingBasics(this.examModelingExercise);

        // checks
        loadQueryResults(examModelingExercise);

        assertThat(examModelingExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2L);

        assertThat(unassessedSubmissionCorrectionRound0.isPresent()).isTrue();
        assertThat(unassessedSubmissionCorrectionRound0.get()).isIn(submission1, submission2);
        assertThat(unassessedSubmissionCorrectionRound1.isPresent()).isFalse();

        assertThat(submissionListTutor1CorrectionRound0.size()).isEqualTo(0);
        assertThat(submissionListTutor2CorrectionRound0.size()).isEqualTo(0);
        assertThat(submissionListTutor1CorrectionRound1.size()).isEqualTo(0);
        assertThat(submissionListTutor2CorrectionRound1.size()).isEqualTo(0);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TUTOR")
    public void testModelingExerciseGetRandomSubmissionEligibleForNewAssessmentOneAssessmentsWithoutLock() {
        submission1 = new ModelingSubmission();
        submission2 = new ModelingSubmission();
        // setup
        queryTestingBasics(this.examModelingExercise);

        database.addResultToSubmission(submission1, AssessmentType.MANUAL, tutor1, 10L, true);

        // checks
        loadQueryResults(examModelingExercise);

        assertThat(examModelingExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2L);

        assertThat(unassessedSubmissionCorrectionRound0.isPresent()).isTrue();
        assertThat(unassessedSubmissionCorrectionRound0.get()).isEqualTo(submission2);
        assertThat(unassessedSubmissionCorrectionRound1.isPresent()).isTrue();
        assertThat(unassessedSubmissionCorrectionRound1.get()).isEqualTo(submission1);

        assertThat(submissionListTutor1CorrectionRound0.size()).isEqualTo(1);
        assertThat(submissionListTutor1CorrectionRound0.get(0)).isEqualTo(submission1);
        assertThat(submissionListTutor2CorrectionRound0.size()).isEqualTo(0);
        assertThat(submissionListTutor1CorrectionRound1.size()).isEqualTo(0);
        assertThat(submissionListTutor2CorrectionRound1.size()).isEqualTo(0);

    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TUTOR")
    public void testModelingExerciseGetRandomSubmissionEligibleForNewAssessmentOneAssessmentsWithLock() {
        submission1 = new ModelingSubmission();
        submission2 = new ModelingSubmission();
        // setup
        queryTestingBasics(this.examModelingExercise);

        Result resultWithLock;

        resultWithLock = submissionService.saveNewEmptyResult(submission1);
        resultWithLock.setAssessor(tutor1);
        resultWithLock.setAssessmentType(AssessmentType.MANUAL);
        resultRepository.save(resultWithLock);

        // checks
        loadQueryResults(examModelingExercise);

        assertThat(examModelingExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2L);

        assertThat(unassessedSubmissionCorrectionRound0.isPresent()).isTrue();
        assertThat(unassessedSubmissionCorrectionRound0.get()).isEqualTo(submission2);
        assertThat(unassessedSubmissionCorrectionRound1.isPresent()).isFalse();

        assertThat(submissionListTutor1CorrectionRound0.size()).isEqualTo(1);
        assertThat(submissionListTutor1CorrectionRound0.get(0)).isEqualTo(submission1);
        assertThat(submissionListTutor2CorrectionRound0.size()).isEqualTo(0);
        assertThat(submissionListTutor1CorrectionRound1.size()).isEqualTo(0);
        assertThat(submissionListTutor2CorrectionRound1.size()).isEqualTo(0);

    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TUTOR")
    public void testModelingExerciseGetRandomSubmissionEligibleForNewAssessmentOneAssessmentsInSecondCorrectionRoundWithoutLock() {
        submission1 = new ModelingSubmission();
        submission2 = new ModelingSubmission();
        // setup
        queryTestingBasics(this.examModelingExercise);

        database.addResultToSubmission(submission1, AssessmentType.MANUAL, tutor1, 10L, true);
        database.addResultToSubmission(submission1, AssessmentType.MANUAL, tutor2, 20L, true);

        // checks
        loadQueryResults(examModelingExercise);

        assertThat(submission1.getResults().size()).isEqualTo(2L);

        assertThat(examModelingExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2L);

        assertThat(unassessedSubmissionCorrectionRound0.isPresent()).isTrue();
        assertThat(unassessedSubmissionCorrectionRound0.get()).isEqualTo(submission2);
        assertThat(unassessedSubmissionCorrectionRound1.isPresent()).isFalse();

        assertThat(submissionListTutor1CorrectionRound0.size()).isEqualTo(1);
        assertThat(submissionListTutor1CorrectionRound0.get(0)).isEqualTo(submission1);
        assertThat(submissionListTutor2CorrectionRound0.size()).isEqualTo(0);
        assertThat(submissionListTutor1CorrectionRound1.size()).isEqualTo(0);
        assertThat(submissionListTutor2CorrectionRound1.size()).isEqualTo(1);
        assertThat(submissionListTutor2CorrectionRound1.get(0)).isEqualTo(submission1);

    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TUTOR")
    public void testModelingExerciseGetRandomSubmissionEligibleForNewAssessmentOneAssessmentsInSecondCorrectionRoundWithLock() {
        submission1 = new ModelingSubmission();
        submission2 = new ModelingSubmission();
        // setup
        queryTestingBasics(this.examModelingExercise);

        database.addResultToSubmission(submission1, AssessmentType.MANUAL, tutor1, 10L, true);

        Result resultForSecondCorrectionWithLock = submissionService.saveNewEmptyResult(submission1);
        resultForSecondCorrectionWithLock.setAssessor(tutor2);
        resultForSecondCorrectionWithLock.setAssessmentType(AssessmentType.MANUAL);
        resultRepository.save(resultForSecondCorrectionWithLock);

        // checks
        loadQueryResults(examModelingExercise);

        assertThat(submission1.getResults().size()).isEqualTo(2L);

        assertThat(examModelingExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2L);

        assertThat(unassessedSubmissionCorrectionRound0.isPresent()).isTrue();
        assertThat(unassessedSubmissionCorrectionRound0.get()).isEqualTo(submission2);
        assertThat(unassessedSubmissionCorrectionRound1.isPresent()).isFalse();

        assertThat(submissionListTutor1CorrectionRound0.size()).isEqualTo(1);
        assertThat(submissionListTutor1CorrectionRound0.get(0)).isEqualTo(submission1);
        assertThat(submissionListTutor2CorrectionRound0.size()).isEqualTo(0);
        assertThat(submissionListTutor1CorrectionRound1.size()).isEqualTo(0);
        assertThat(submissionListTutor2CorrectionRound1.size()).isEqualTo(1);
        assertThat(submissionListTutor2CorrectionRound1.get(0)).isEqualTo(submission1);
    }

}
