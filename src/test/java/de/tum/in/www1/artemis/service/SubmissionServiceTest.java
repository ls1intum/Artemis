package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.web.rest.dto.SubmissionWithComplaintDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

class SubmissionServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    private User student1;

    private User tutor1;

    private User tutor2;

    private TextExercise examTextExercise;

    private ModelingExercise examModelingExercise;

    private ProgrammingExercise examProgrammingExercise;

    private FileUploadExercise examFileUploadExercise;

    private Submission submission1;

    private Submission submission2;

    private Optional<Submission> unassessedSubmissionCorrectionRound0Tutor1;

    private Optional<Submission> unassessedSubmissionCorrectionRound1Tutor1;

    private Optional<Submission> unassessedSubmissionCorrectionRound0Tutor2;

    private Optional<Submission> unassessedSubmissionCorrectionRound1Tutor2;

    private List<Submission> submissionListTutor1CorrectionRound0;

    private List<Submission> submissionListTutor2CorrectionRound0;

    private List<Submission> submissionListTutor1CorrectionRound1;

    private List<Submission> submissionListTutor2CorrectionRound1;

    // set to true, if a tutor is only able to assess a submission if he has not assessed it any prior correction rounds
    private final boolean tutorAssessUnique = true;

    @BeforeEach
    void init() {
        List<User> users = database.addUsers(3, 2, 0, 1);
        student1 = users.get(0);
        tutor1 = users.get(3);
        tutor2 = users.get(4);

        Course course = database.createCourse();
        Exam exam = database.addExam(course);

        exam.setNumberOfCorrectionRoundsInExam(2);
        exam = examRepository.save(exam);

        exam = database.addExerciseGroupsAndExercisesToExam(exam, true);
        examTextExercise = (TextExercise) exam.getExerciseGroups().get(0).getExercises().stream().filter(ex -> ex instanceof TextExercise).findAny().orElse(null);
        examModelingExercise = (ModelingExercise) exam.getExerciseGroups().get(3).getExercises().stream().filter(ex -> ex instanceof ModelingExercise).findAny().orElse(null);
        examProgrammingExercise = (ProgrammingExercise) exam.getExerciseGroups().get(6).getExercises().stream().filter(ex -> ex instanceof ProgrammingExercise).findAny()
                .orElse(null);
        examFileUploadExercise = (FileUploadExercise) exam.getExerciseGroups().get(2).getExercises().stream().filter(exy -> exy instanceof FileUploadExercise).findAny()
                .orElse(null);
    }

    @AfterEach
    void tearDown() {
        // database.resetDatabase();

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
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCheckSubmissionAllowanceGroupCheck() {
        student1.setGroups(Collections.singleton("another-group"));
        userRepository.save(student1);
        assertThrows(AccessForbiddenException.class, () -> submissionService.checkSubmissionAllowanceElseThrow(examTextExercise, null, student1));
    }

    private void queryTestingBasics(Exercise exercise) {
        Exam exam = exercise.getExerciseGroup().getExam();

        exam.setNumberOfCorrectionRoundsInExam(2);

        examRepository.save(exam);

        if (submission1 != null && submission2 != null) {
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
    }

    private void getTutorSpecificCallsTutor1(Exercise exercise) {
        database.changeUser("tutor1");
        unassessedSubmissionCorrectionRound0Tutor1 = submissionService.getRandomAssessableSubmission(exercise, true, 0);
        unassessedSubmissionCorrectionRound1Tutor1 = submissionService.getRandomAssessableSubmission(exercise, true, 1);
        submissionListTutor1CorrectionRound0 = submissionService.getAllSubmissionsAssessedByTutorForCorrectionRoundAndExerciseIgnoreTestRuns(exercise.getId(), tutor1, true, 0);
        submissionListTutor1CorrectionRound1 = submissionService.getAllSubmissionsAssessedByTutorForCorrectionRoundAndExerciseIgnoreTestRuns(exercise.getId(), tutor1, true, 1);
    }

    private void getTutorSpecificCallsTutor2(Exercise exercise) {
        database.changeUser("tutor2");
        unassessedSubmissionCorrectionRound0Tutor2 = submissionService.getRandomAssessableSubmission(exercise, true, 0);
        unassessedSubmissionCorrectionRound1Tutor2 = submissionService.getRandomAssessableSubmission(exercise, true, 1);
        submissionListTutor2CorrectionRound0 = submissionService.getAllSubmissionsAssessedByTutorForCorrectionRoundAndExerciseIgnoreTestRuns(exercise.getId(), tutor2, true, 0);
        submissionListTutor2CorrectionRound1 = submissionService.getAllSubmissionsAssessedByTutorForCorrectionRoundAndExerciseIgnoreTestRuns(exercise.getId(), tutor2, true, 1);
    }

    private void getQueryResults(Exercise exercise) {
        getTutorSpecificCallsTutor1(exercise);
        getTutorSpecificCallsTutor2(exercise);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testProgrammingExerciseGetRandomSubmissionEligibleForNewAssessmentNoAssessments() {

        submission1 = new ProgrammingSubmission();
        submission2 = new ProgrammingSubmission();

        // setup
        queryTestingBasics(this.examProgrammingExercise);

        getQueryResults(this.examProgrammingExercise);

        assertThat(examProgrammingExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2);

        assertThat(unassessedSubmissionCorrectionRound0Tutor1).isPresent();
        assertThat(unassessedSubmissionCorrectionRound0Tutor1.get()).isIn(submission1, submission2);
        assertThat(unassessedSubmissionCorrectionRound1Tutor1).isEmpty();

        assertThat(submissionListTutor1CorrectionRound0).isEmpty();
        assertThat(submissionListTutor2CorrectionRound0).isEmpty();
        assertThat(submissionListTutor1CorrectionRound1).isEmpty();
        assertThat(submissionListTutor2CorrectionRound1).isEmpty();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testFileUploadExerciseGetRandomSubmissionEligibleForNewAssessment() {
        submission1 = new FileUploadSubmission();
        submission2 = new FileUploadSubmission();
        // setup
        queryTestingBasics(this.examFileUploadExercise);

        getQueryResults(this.examFileUploadExercise);

        assertThat(examFileUploadExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2L);

        assertThat(unassessedSubmissionCorrectionRound0Tutor1).isPresent();
        assertThat(unassessedSubmissionCorrectionRound0Tutor1.get()).isIn(submission1, submission2);
        assertThat(unassessedSubmissionCorrectionRound1Tutor1).isEmpty();

        assertThat(submissionListTutor1CorrectionRound0).isEmpty();
        assertThat(submissionListTutor2CorrectionRound0).isEmpty();
        assertThat(submissionListTutor1CorrectionRound1).isEmpty();
        assertThat(submissionListTutor2CorrectionRound1).isEmpty();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testFileUploadExerciseGetRandomSubmissionEligibleForNewAssessmentWithoutSubmission() {
        // setup
        queryTestingBasics(this.examFileUploadExercise);

        getQueryResults(this.examFileUploadExercise);

        assertThat(examFileUploadExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2L);

        assertThat(unassessedSubmissionCorrectionRound0Tutor1).isEmpty();
        assertThat(unassessedSubmissionCorrectionRound1Tutor1).isEmpty();

        assertThat(submissionListTutor1CorrectionRound0).isEmpty();
        assertThat(submissionListTutor2CorrectionRound0).isEmpty();
        assertThat(submissionListTutor1CorrectionRound1).isEmpty();
        assertThat(submissionListTutor2CorrectionRound1).isEmpty();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testTextExerciseGetRandomSubmissionEligibleForNewAssessmentNoAssessments() {
        submission1 = new TextSubmission();
        submission2 = new TextSubmission();
        // setup
        queryTestingBasics(this.examTextExercise);

        getQueryResults(this.examTextExercise);

        assertThat(examTextExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2L);

        assertThat(unassessedSubmissionCorrectionRound0Tutor1).isPresent();
        assertThat(unassessedSubmissionCorrectionRound0Tutor1.get()).isIn(submission1, submission2);
        assertThat(unassessedSubmissionCorrectionRound1Tutor1).isEmpty();

        assertThat(submissionListTutor1CorrectionRound0).isEmpty();
        assertThat(submissionListTutor2CorrectionRound0).isEmpty();
        assertThat(submissionListTutor1CorrectionRound1).isEmpty();
        assertThat(submissionListTutor2CorrectionRound1).isEmpty();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testTextExerciseGetRandomSubmissionEligibleForNewAssessmentOneAssessmentsWithoutLock() {
        submission1 = new TextSubmission();
        submission2 = new TextSubmission();
        // setup
        queryTestingBasics(examTextExercise);

        database.addResultToSubmission(submission1, AssessmentType.MANUAL, tutor1, 10D, true);

        // checks
        getQueryResults(examTextExercise);

        assertThat(examTextExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2L);

        assertThat(unassessedSubmissionCorrectionRound0Tutor1).contains(submission2);
        assertThat(unassessedSubmissionCorrectionRound0Tutor2).contains(submission2);

        if (tutorAssessUnique) {
            assertThat(unassessedSubmissionCorrectionRound1Tutor1).isEmpty();
        }
        else {
            assertThat(unassessedSubmissionCorrectionRound1Tutor1).contains(submission1);
        }
        assertThat(unassessedSubmissionCorrectionRound1Tutor2).contains(submission1);

        assertThat(submissionListTutor1CorrectionRound0).hasSize(1);
        assertThat(submissionListTutor1CorrectionRound0.get(0)).isEqualTo(submission1);
        assertThat(submissionListTutor2CorrectionRound0).isEmpty();
        assertThat(submissionListTutor1CorrectionRound1).isEmpty();
        assertThat(submissionListTutor2CorrectionRound1).isEmpty();

    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testTextExerciseGetRandomSubmissionEligibleForNewAssessmentOneAssessmentsWithLock() {
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
        getQueryResults(examTextExercise);

        assertThat(examTextExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2L);

        assertThat(unassessedSubmissionCorrectionRound0Tutor1).contains(submission2);
        assertThat(unassessedSubmissionCorrectionRound1Tutor1).isEmpty();

        assertThat(submissionListTutor1CorrectionRound0).hasSize(1);
        assertThat(submissionListTutor1CorrectionRound0.get(0)).isEqualTo(submission1);
        assertThat(submissionListTutor2CorrectionRound0).isEmpty();
        assertThat(submissionListTutor1CorrectionRound1).isEmpty();
        assertThat(submissionListTutor2CorrectionRound1).isEmpty();

    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testTextExerciseGetRandomSubmissionEligibleForNewAssessmentOneAssessmentsInSecondCorrectionRoundWithoutLock() {
        submission1 = new TextSubmission();
        submission2 = new TextSubmission();
        // setup
        queryTestingBasics(this.examTextExercise);

        database.addResultToSubmission(submission1, AssessmentType.MANUAL, tutor1, 10D, true);
        database.addResultToSubmission(submission1, AssessmentType.MANUAL, tutor2, 20D, true);

        // checks
        getQueryResults(examTextExercise);

        assertThat(submission1.getResults()).hasSize(2);

        assertThat(examTextExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2);

        assertThat(unassessedSubmissionCorrectionRound0Tutor1).contains(submission2);
        assertThat(unassessedSubmissionCorrectionRound1Tutor1).isEmpty();

        assertThat(submissionListTutor1CorrectionRound0).hasSize(1);
        assertThat(submissionListTutor1CorrectionRound0.get(0)).isEqualTo(submission1);
        assertThat(submissionListTutor2CorrectionRound0).isEmpty();
        assertThat(submissionListTutor1CorrectionRound1).isEmpty();
        assertThat(submissionListTutor2CorrectionRound1).hasSize(1);
        assertThat(submissionListTutor2CorrectionRound1.get(0)).isEqualTo(submission1);

    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testTextExerciseGetRandomSubmissionEligibleForNewAssessmentOneAssessmentsInSecondCorrectionRoundWithLock() {
        submission1 = new TextSubmission();
        submission2 = new TextSubmission();
        // setup
        queryTestingBasics(this.examTextExercise);

        database.addResultToSubmission(submission1, AssessmentType.MANUAL, tutor1, 10D, true);

        Result resultForSecondCorrectionWithLock;

        resultForSecondCorrectionWithLock = submissionService.saveNewEmptyResult(submission1);
        resultForSecondCorrectionWithLock.setAssessor(tutor2);
        resultForSecondCorrectionWithLock.setAssessmentType(AssessmentType.MANUAL);
        resultRepository.save(resultForSecondCorrectionWithLock);

        // checks
        getQueryResults(examTextExercise);

        assertThat(submission1.getResults()).hasSize(2);

        assertThat(examTextExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2);

        assertThat(unassessedSubmissionCorrectionRound0Tutor1).contains(submission2);
        assertThat(unassessedSubmissionCorrectionRound1Tutor1).isEmpty();

        assertThat(submissionListTutor1CorrectionRound0).hasSize(1);
        assertThat(submissionListTutor1CorrectionRound0.get(0)).isEqualTo(submission1);
        assertThat(submissionListTutor2CorrectionRound0).isEmpty();
        assertThat(submissionListTutor1CorrectionRound1).isEmpty();
        assertThat(submissionListTutor2CorrectionRound1).hasSize(1);
        assertThat(submissionListTutor2CorrectionRound1.get(0)).isEqualTo(submission1);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testModelingExerciseGetRandomSubmissionEligibleForNewAssessmentNoAssessments() {
        submission1 = new ModelingSubmission();
        submission2 = new ModelingSubmission();
        // setup
        queryTestingBasics(this.examModelingExercise);

        // checks
        getQueryResults(examModelingExercise);

        assertThat(examModelingExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2L);

        assertThat(unassessedSubmissionCorrectionRound0Tutor1).isPresent();
        assertThat(unassessedSubmissionCorrectionRound0Tutor1.get()).isIn(submission1, submission2);
        assertThat(unassessedSubmissionCorrectionRound1Tutor1).isEmpty();

        assertThat(submissionListTutor1CorrectionRound0).isEmpty();
        assertThat(submissionListTutor2CorrectionRound0).isEmpty();
        assertThat(submissionListTutor1CorrectionRound1).isEmpty();
        assertThat(submissionListTutor2CorrectionRound1).isEmpty();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testModelingExerciseGetRandomSubmissionEligibleForNewAssessmentOneAssessmentsWithoutLock() {
        submission1 = new ModelingSubmission();
        submission2 = new ModelingSubmission();
        // setup
        queryTestingBasics(this.examModelingExercise);

        database.addResultToSubmission(submission1, AssessmentType.MANUAL, tutor1, 10D, true);

        // checks
        getQueryResults(examModelingExercise);

        assertThat(examModelingExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2);

        assertThat(unassessedSubmissionCorrectionRound0Tutor1).contains(submission2);

        if (tutorAssessUnique) {
            assertThat(unassessedSubmissionCorrectionRound1Tutor1).isEmpty();
        }
        else {
            assertThat(unassessedSubmissionCorrectionRound1Tutor1).contains(submission1);
        }

        assertThat(unassessedSubmissionCorrectionRound1Tutor2).contains(submission1);

        assertThat(submissionListTutor1CorrectionRound0).hasSize(1);
        assertThat(submissionListTutor1CorrectionRound0.get(0)).isEqualTo(submission1);

        assertThat(submissionListTutor2CorrectionRound0).isEmpty();
        assertThat(submissionListTutor1CorrectionRound1).isEmpty();
        assertThat(submissionListTutor2CorrectionRound1).isEmpty();

    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testModelingExerciseGetRandomSubmissionEligibleForNewAssessmentOneAssessmentsWithLock() {
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
        getQueryResults(examModelingExercise);

        assertThat(examModelingExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2L);

        assertThat(unassessedSubmissionCorrectionRound0Tutor1).contains(submission2);
        assertThat(unassessedSubmissionCorrectionRound1Tutor1).isEmpty();

        assertThat(submissionListTutor1CorrectionRound0).hasSize(1);
        assertThat(submissionListTutor1CorrectionRound0.get(0)).isEqualTo(submission1);
        assertThat(submissionListTutor2CorrectionRound0).isEmpty();
        assertThat(submissionListTutor1CorrectionRound1).isEmpty();
        assertThat(submissionListTutor2CorrectionRound1).isEmpty();

    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testModelingExerciseGetRandomSubmissionEligibleForNewAssessmentOneAssessmentsInSecondCorrectionRoundWithoutLock() {
        submission1 = new ModelingSubmission();
        submission2 = new ModelingSubmission();
        // setup
        queryTestingBasics(this.examModelingExercise);

        database.addResultToSubmission(submission1, AssessmentType.MANUAL, tutor1, 10D, true);
        database.addResultToSubmission(submission1, AssessmentType.MANUAL, tutor2, 20D, true);

        // checks
        getQueryResults(examModelingExercise);

        assertThat(submission1.getResults()).hasSize(2);

        assertThat(examModelingExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2);

        assertThat(unassessedSubmissionCorrectionRound0Tutor1).contains(submission2);
        assertThat(unassessedSubmissionCorrectionRound1Tutor1).isEmpty();

        assertThat(submissionListTutor1CorrectionRound0).hasSize(1);
        assertThat(submissionListTutor1CorrectionRound0.get(0)).isEqualTo(submission1);
        assertThat(submissionListTutor2CorrectionRound0).isEmpty();
        assertThat(submissionListTutor1CorrectionRound1).isEmpty();
        assertThat(submissionListTutor2CorrectionRound1).hasSize(1);
        assertThat(submissionListTutor2CorrectionRound1.get(0)).isEqualTo(submission1);

    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testModelingExerciseGetRandomSubmissionEligibleForNewAssessmentOneAssessmentsInSecondCorrectionRoundWithLock() {
        submission1 = new ModelingSubmission();
        submission2 = new ModelingSubmission();
        // setup
        queryTestingBasics(this.examModelingExercise);

        database.addResultToSubmission(submission1, AssessmentType.MANUAL, tutor1, 10D, true);

        Result resultForSecondCorrectionWithLock = submissionService.saveNewEmptyResult(submission1);
        resultForSecondCorrectionWithLock.setAssessor(tutor2);
        resultForSecondCorrectionWithLock.setAssessmentType(AssessmentType.MANUAL);
        resultRepository.save(resultForSecondCorrectionWithLock);

        // checks
        getQueryResults(examModelingExercise);

        assertThat(submission1.getResults()).hasSize(2);

        assertThat(examModelingExercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam()).isEqualTo(2);

        assertThat(unassessedSubmissionCorrectionRound0Tutor1).contains(submission2);
        assertThat(unassessedSubmissionCorrectionRound1Tutor1).isEmpty();

        assertThat(submissionListTutor1CorrectionRound0).hasSize(1);
        assertThat(submissionListTutor1CorrectionRound0.get(0)).isEqualTo(submission1);
        assertThat(submissionListTutor2CorrectionRound0).isEmpty();
        assertThat(submissionListTutor1CorrectionRound1).isEmpty();
        assertThat(submissionListTutor2CorrectionRound1).hasSize(1);
        assertThat(submissionListTutor2CorrectionRound1.get(0)).isEqualTo(submission1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetSubmissionsWithComplaintsForExerciseAsInstructor() {
        var participation1 = database.createAndSaveParticipationForExercise(examTextExercise, "student1");
        var participation2 = database.createAndSaveParticipationForExercise(examTextExercise, "student2");
        var participation3 = database.createAndSaveParticipationForExercise(examTextExercise, "student3");
        // noinspection unused
        var submissionWithoutComplaint = database.addSubmissionWithFinishedResultsWithAssessor(participation1, new TextSubmission(), "tutor2");
        var submissionWithComplaintSameTutor = database.addSubmissionWithFinishedResultsWithAssessor(participation2, new TextSubmission(), "instructor1");
        var submissionWithComplaintOtherTutor = database.addSubmissionWithFinishedResultsWithAssessor(participation3, new TextSubmission(), "tutor2");
        database.addComplaintToSubmission(submissionWithComplaintSameTutor, "student2", ComplaintType.COMPLAINT);
        database.addComplaintToSubmission(submissionWithComplaintOtherTutor, "student3", ComplaintType.COMPLAINT);

        List<SubmissionWithComplaintDTO> dtoList = submissionService.getSubmissionsWithComplaintsForExercise(examTextExercise.getId(), true);

        List<Submission> submissionsFromDTO = dtoList.stream().map(SubmissionWithComplaintDTO::submission).filter(Objects::nonNull).toList();
        List<Complaint> complaintsFromDTO = dtoList.stream().map(SubmissionWithComplaintDTO::complaint).filter(Objects::nonNull).toList();

        assertThat(dtoList).hasSize(2);
        assertThat(complaintsFromDTO).hasSize(2);
        assertThat(submissionsFromDTO).isEqualTo(List.of(submissionWithComplaintSameTutor, submissionWithComplaintOtherTutor));

        dtoList.forEach(dto -> {
            if (dto.submission().equals(submissionWithComplaintSameTutor)) {
                assertThat(complaintRepository.findByResultSubmissionId(dto.submission().getId()).orElseThrow().getStudent().getLogin()).isEqualTo("student2");
            }
            else if (dto.submission().equals(submissionWithComplaintOtherTutor)) {
                assertThat(complaintRepository.findByResultSubmissionId(dto.submission().getId()).orElseThrow().getStudent().getLogin()).isEqualTo("student3");
            }
            else {
                fail("Unreachable statement");
            }
        });
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetSubmissionsWithComplaintsForExerciseAsTutor() {
        var participation1 = database.createAndSaveParticipationForExercise(examTextExercise, "student1");
        var participation2 = database.createAndSaveParticipationForExercise(examTextExercise, "student2");
        var participation3 = database.createAndSaveParticipationForExercise(examTextExercise, "student3");
        // noinspection unused
        var submissionWithoutComplaint = database.addSubmissionWithFinishedResultsWithAssessor(participation1, new TextSubmission(), "tutor2");
        var submissionWithComplaintSameTutor = database.addSubmissionWithFinishedResultsWithAssessor(participation2, new TextSubmission(), "tutor1");
        var submissionWithComplaintOtherTutor = database.addSubmissionWithFinishedResultsWithAssessor(participation3, new TextSubmission(), "tutor2");
        database.addComplaintToSubmission(submissionWithComplaintSameTutor, "student2", ComplaintType.COMPLAINT);
        database.addComplaintToSubmission(submissionWithComplaintOtherTutor, "student3", ComplaintType.COMPLAINT);

        List<SubmissionWithComplaintDTO> dtoList = submissionService.getSubmissionsWithComplaintsForExercise(examTextExercise.getId(), false);

        List<Submission> submissionsFromDTO = dtoList.stream().map(SubmissionWithComplaintDTO::submission).filter(Objects::nonNull).toList();
        List<Complaint> complaintsFromDTO = dtoList.stream().map(SubmissionWithComplaintDTO::complaint).filter(Objects::nonNull).toList();

        assertThat(dtoList).hasSize(1);
        assertThat(complaintsFromDTO).hasSize(1);
        assertThat(submissionsFromDTO).isEqualTo(List.of(submissionWithComplaintOtherTutor));
        dtoList.forEach(dto -> {
            if (dto.submission().equals(submissionWithComplaintOtherTutor)) {
                assertThat(complaintRepository.findByResultSubmissionId(dto.submission().getId()).orElseThrow().getStudent().getLogin()).isEqualTo("student3");
            }
            else {
                fail("Unreachable statement");
            }
        });
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetSubmissionsWithMoreFeedbackRequestsForExerciseAsTutor() {
        var participation1 = database.createAndSaveParticipationForExercise(examTextExercise, "student1");
        var participation2 = database.createAndSaveParticipationForExercise(examTextExercise, "student2");
        var participation3 = database.createAndSaveParticipationForExercise(examTextExercise, "student3");
        // noinspection unused
        var submissionWithoutRequest = database.addSubmissionWithFinishedResultsWithAssessor(participation1, new TextSubmission(), "tutor2");
        var submissionWithRequestSameTutor = database.addSubmissionWithFinishedResultsWithAssessor(participation2, new TextSubmission(), "tutor1");
        var submissionWithRequestOtherTutor = database.addSubmissionWithFinishedResultsWithAssessor(participation3, new TextSubmission(), "tutor2");
        database.addComplaintToSubmission(submissionWithRequestSameTutor, "student2", ComplaintType.MORE_FEEDBACK);
        database.addComplaintToSubmission(submissionWithRequestOtherTutor, "student3", ComplaintType.MORE_FEEDBACK);

        List<SubmissionWithComplaintDTO> dtoList = submissionService.getSubmissionsWithMoreFeedbackRequestsForExercise(examTextExercise.getId());

        List<Submission> submissionsFromDTO = dtoList.stream().map(SubmissionWithComplaintDTO::submission).filter(Objects::nonNull).toList();
        List<Complaint> requestsFromDTO = dtoList.stream().map(SubmissionWithComplaintDTO::complaint).filter(Objects::nonNull).toList();

        assertThat(dtoList).hasSize(1);
        assertThat(requestsFromDTO).hasSize(1);
        assertThat(submissionsFromDTO).isEqualTo(List.of(submissionWithRequestSameTutor));
        dtoList.forEach(dto -> {
            if (dto.submission().equals(submissionWithRequestSameTutor)) {
                assertThat(complaintRepository.findByResultSubmissionId(dto.submission().getId()).orElseThrow().getStudent().getLogin()).isEqualTo("student2");
            }
            else {
                fail("Unreachable statement");
            }
        });
    }
}
