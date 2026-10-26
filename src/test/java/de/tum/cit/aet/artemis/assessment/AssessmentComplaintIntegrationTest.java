package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import tools.jackson.databind.JsonNode;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintResponse;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.ComplaintAction;
import de.tum.cit.aet.artemis.assessment.dto.ComplaintDTO;
import de.tum.cit.aet.artemis.assessment.dto.ComplaintRequestDTO;
import de.tum.cit.aet.artemis.assessment.dto.ComplaintResponseUpdateDTO;
import de.tum.cit.aet.artemis.assessment.dto.FeedbackDTO;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.service.AssessmentService;
import de.tum.cit.aet.artemis.assessment.test_repository.ComplaintResponseTestRepository;
import de.tum.cit.aet.artemis.assessment.test_repository.ExampleSubmissionTestRepository;
import de.tum.cit.aet.artemis.assessment.util.ComplaintUtilService;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.util.TestResourceUtils;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamFactory;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionWithComplaintDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.dto.ComplaintResponseRequestDTO;
import de.tum.cit.aet.artemis.modeling.dto.ModelingAssessmentUpdateDTO;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseParticipationUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class AssessmentComplaintIntegrationTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "assessmentcomplaintintegration";

    @Autowired
    private ComplaintRepository complaintRepo;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private ExampleSubmissionTestRepository exampleSubmissionRepository;

    @Autowired
    private AssessmentService assessmentService;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private ComplaintResponseTestRepository complaintResponseTestRepository;

    @Autowired
    private ExamTestRepository examTestRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ProgrammingExerciseParticipationUtilService programmingExerciseParticipationUtilService;

    @Autowired
    private FileUploadExerciseUtilService fileUploadExerciseUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ComplaintUtilService complaintUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    private ModelingExercise modelingExercise;

    private ModelingSubmission modelingSubmission;

    private Result modelingAssessment;

    private Complaint complaint;

    private ComplaintRequestDTO complaintRequest;

    private Course course;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 2, 2, 0, 1);

        // Initialize with 3 max complaints and 7-day max complaint due date
        course = courseUtilService.addEnrolledCourseWithModelingAndTextAndFileUploadExercise(TEST_PREFIX);
        modelingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ModelingExercise.class);
        saveModelingSubmissionAndAssessment();
        complaint = new Complaint().result(modelingAssessment).complaintText("This is not fair").complaintType(ComplaintType.COMPLAINT);
        complaintRequest = new ComplaintRequestDTO(modelingAssessment.getId(), "This is not fair", ComplaintType.COMPLAINT, Optional.empty());
    }

    /**
     * Deleting an exercise that carries a resolved complaint (a Complaint with an answered ComplaintResponse) must not
     * fail the cascade. Regression test for a Hibernate TransientPropertyValueException
     * ("Persistent instance of Complaint references an unsaved transient instance of ComplaintResponse").
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void deleteModelingExerciseWithResolvedComplaint_shouldNotFail() throws Exception {
        complaint.setAccepted(true);
        complaint = complaintRepo.save(complaint);
        ComplaintResponse complaintResponse = new ComplaintResponse();
        complaintResponse.setComplaint(complaint);
        complaintResponse.setResponseText("resolved");
        complaintResponse.setSubmittedTime(ZonedDateTime.now());
        complaintResponse.setReviewer(userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"));
        complaintResponseTestRepository.save(complaintResponse);

        // The assessment saved in setup schedules an asynchronous participant-score update that can re-create a
        // participant_score during the delete cascade. Deletion is resilient to this by design (the
        // participant_score -> result foreign keys are ON DELETE SET NULL and ParticipationDeletionService bulk
        // deletes any re-created scores after the results are gone), so the delete must succeed without draining
        // the scheduler first. This test deliberately does not drain, to keep exercising that race path.
        request.delete("/api/modeling/modeling-exercises/" + modelingExercise.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void submitComplaintAboutModelingAssessmentResultBeforeDueDate() throws Exception {
        exerciseUtilService.updateExerciseDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(2));
        exerciseUtilService.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(1));
        modelingAssessment.setCompletionDate(modelingExercise.getDueDate().minusDays(1));
        resultRepository.save(modelingAssessment);

        verifySuccessfulComplaint();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void submitComplaintAboutModelingAssessmentResultBeforeAssessmentDueDate() throws Exception {
        exerciseUtilService.updateExerciseDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(3));
        exerciseUtilService.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(1));
        modelingAssessment.setCompletionDate(modelingExercise.getAssessmentDueDate().minusDays(1));
        resultRepository.save(modelingAssessment);

        verifySuccessfulComplaint();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void submitComplaintAboutModelingAssessmentResultAfterAssessmentDueDate() throws Exception {
        exerciseUtilService.updateExerciseDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(3));
        exerciseUtilService.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(2));
        modelingAssessment.setCompletionDate(modelingExercise.getAssessmentDueDate().plusDays(1));
        resultRepository.save(modelingAssessment);

        verifySuccessfulComplaint();
    }

    private void verifySuccessfulComplaint() throws Exception {
        request.post("/api/assessment/complaints", complaintRequest, HttpStatus.CREATED);

        Optional<Complaint> storedComplaint = complaintRepo.findByResultId(modelingAssessment.getId());
        assertThat(storedComplaint).as("complaint is saved").isPresent();
        assertThat(storedComplaint.orElseThrow().getComplaintText()).as("complaint text got correctly saved").isEqualTo(complaint.getComplaintText());
        assertThat(storedComplaint.orElseThrow().isAccepted()).as("accepted flag of complaint is not set").isNull();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).orElseThrow();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is true").isTrue();
        Result result = storedComplaint.orElseThrow().getResult();
        assertThat(result.getId()).isEqualTo(storedResult.getId());
        // set a date to UTC for comparison
        storedResult.setCompletionDate(ZonedDateTime.ofInstant(storedResult.getCompletionDate().toInstant(), ZoneId.of("UTC")));
        // TODO add assertion
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void submitComplaintAboutModellingAssessment_complaintLimitNotReached() throws Exception {
        exerciseUtilService.updateExerciseDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(2));
        exerciseUtilService.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(1));

        // 2 complaints are allowed, the course is created with 3 max complaints
        complaintUtilService.addComplaints(TEST_PREFIX + "student1", modelingAssessment.getSubmission(), 2, ComplaintType.COMPLAINT);

        request.post("/api/assessment/complaints", complaintRequest, HttpStatus.CREATED);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is saved").isPresent();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).orElseThrow();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is true").isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void submitComplaintAboutAutomaticResult_turnsItIntoTheManualResultOfTheFirstCorrectionRound() throws Exception {
        exerciseUtilService.updateExerciseDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(2));
        exerciseUtilService.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(1));
        // an automatic result belongs to no correction round
        modelingAssessment.setAssessmentType(AssessmentType.AUTOMATIC);
        modelingAssessment.setCorrectionRound(null);
        resultRepository.save(modelingAssessment);

        request.post("/api/assessment/complaints", complaintRequest, HttpStatus.CREATED);

        // a tutor opens the complaint through the result of round 0, a manual result without a round would be found by nobody
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).orElseThrow();
        assertThat(storedResult.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
        assertThat(storedResult.getCorrectionRound()).isZero();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void submitComplaintAboutModelingAssessment_complaintLimitReached() throws Exception {
        complaintUtilService.addComplaints(TEST_PREFIX + "student1", modelingAssessment.getSubmission(), 3, ComplaintType.COMPLAINT);

        request.post("/api/assessment/complaints", complaintRequest, HttpStatus.BAD_REQUEST);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is not saved").isNotPresent();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).orElseThrow();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is false").isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void requestMoreFeedbackAboutModelingAssessment_noLimit() throws Exception {
        exerciseUtilService.updateExerciseDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(2));
        exerciseUtilService.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(1));

        complaintUtilService.addComplaints(TEST_PREFIX + "student1", modelingAssessment.getSubmission(), 3, ComplaintType.MORE_FEEDBACK);

        request.post("/api/assessment/complaints", complaintRequest, HttpStatus.CREATED);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is saved").isPresent();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).orElseThrow();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is true").isTrue();

        // Only one complaint is possible for exercise regardless of its type
        var moreFeedbackRequest = new ComplaintRequestDTO(modelingAssessment.getId(), "Please explain", ComplaintType.MORE_FEEDBACK, Optional.empty());
        request.post("/api/assessment/complaints", moreFeedbackRequest, HttpStatus.BAD_REQUEST);
        assertThat(complaintRepo.findByResultId(modelingAssessment.getId()).orElseThrow().getComplaintType()).as("more feedback request is not saved")
                .isNotEqualTo(ComplaintType.MORE_FEEDBACK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void submitComplaintAboutModelingAssessment_validDueDate() throws Exception {
        // Set the due date for the mock course to 2 weeks. Complaint created one week after the result date is fine.
        course.setMaxComplaintTimeDays(14);
        courseRepository.save(course);

        exerciseUtilService.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusWeeks(1));
        exerciseUtilService.updateResultCompletionDate(modelingAssessment.getId(), ZonedDateTime.now().minusWeeks(1));

        request.post("/api/assessment/complaints", complaintRequest, HttpStatus.CREATED);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is saved").isPresent();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).orElseThrow();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is true").isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void submitComplaintAboutPreliminaryAthenaFeedback_isRejected() throws Exception {
        modelingAssessment.setAssessmentType(AssessmentType.AUTOMATIC_ATHENA);
        resultRepository.save(modelingAssessment);

        request.post("/api/assessment/complaints", complaintRequest, HttpStatus.BAD_REQUEST);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is not saved").isNotPresent();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).orElseThrow();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is false").isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void submitComplaintAboutModelingAssessment_assessmentTooOld() throws Exception {
        // 3 weeks is already past the due date
        exerciseUtilService.updateExerciseDueDate(modelingExercise.getId(), ZonedDateTime.now().minusWeeks(4));
        exerciseUtilService.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusWeeks(3));
        exerciseUtilService.updateResultCompletionDate(modelingAssessment.getId(), ZonedDateTime.now().minusWeeks(2));

        request.post("/api/assessment/complaints", complaintRequest, HttpStatus.BAD_REQUEST);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is not saved").isNotPresent();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).orElseThrow();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is false").isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "USER")
    void submitComplaintResponse_rejectComplaint() throws Exception {
        complaint = complaintRepo.save(complaint);
        // creating the initial complaintResponse
        complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", complaint);
        ComplaintResponseUpdateDTO complaintResponseUpdate = new ComplaintResponseUpdateDTO("rejected", false, ComplaintAction.RESOLVE_COMPLAINT);

        request.patch("/api/assessment/complaints/" + complaint.getId() + "/response", complaintResponseUpdate, HttpStatus.OK);

        Complaint storedComplaint = complaintRepo.findByResultId(modelingAssessment.getId()).orElseThrow();
        assertThat(storedComplaint.isAccepted()).as("complaint is not accepted").isFalse();
        Result storedResult = resultRepository.findWithBidirectionalSubmissionAndFeedbackAndAssessorAndAssessmentNoteAndTeamStudentsByIdElseThrow(modelingAssessment.getId());
        Result updatedResult = storedResult.getSubmission().getLatestResult();
        participationUtilService.checkFeedbackCorrectlyStored(modelingAssessment.getFeedbacks(), updatedResult.getFeedbacks(), FeedbackType.MANUAL);
        // the typed automatic feedback collections are lazy and irrelevant here (programming exercises only)
        final String[] ignoringFields = { "feedbacks", "testCaseFeedbacks", "scaFeedbacks", "submission", "participation", "assessor" };
        assertThat(storedResult).as("only feedbacks are changed in the result").usingRecursiveComparison().ignoringFields(ignoringFields).isEqualTo(modelingAssessment);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "USER")
    void submitComplaintResponse_afterTwoCorrectionRounds_keepsBothRoundsAndAddsTheNextOne() throws Exception {
        // A second corrector assesses the same submission, so the submission carries one result per correction round.
        // Written through the result repository, which owns the foreign key: re-saving the submission entity here would
        // merge two detached copies of the first round's result, which is a property of the fixture and not of the flow.
        // Assessed by the instructor, not by tutor2: tutor2 answers the complaint below, and nobody may resolve a
        // complaint about an assessment they wrote themselves.
        Result secondRoundAssessment = participationUtilService.addResultToSubmission(AssessmentType.MANUAL, ZonedDateTime.now(), modelingSubmission, TEST_PREFIX + "instructor1",
                List.of());

        assertThat(modelingAssessment.getCorrectionRound()).as("the first assessment belongs to the first correction round").isZero();
        assertThat(secondRoundAssessment.getCorrectionRound()).as("the second assessment belongs to the second correction round").isEqualTo(1);

        // The student complains about the result of the second round, which is the one they were shown.
        Complaint complaintAboutSecondRound = complaintRepo
                .save(new Complaint().result(secondRoundAssessment).complaintText("The second corrector was unfair").complaintType(ComplaintType.COMPLAINT));
        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", complaintAboutSecondRound);
        complaintResponse.getComplaint().setAccepted(true);
        complaintResponse.setResponseText("Accepted");

        List<Feedback> feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        feedbacks.forEach(feedback -> feedback.setType(FeedbackType.MANUAL));
        Result resultAfterComplaint = request.putWithResponseBody("/api/modeling/modeling-submissions/" + modelingSubmission.getId() + "/assessment-after-complaint",
                modelingAssessmentUpdate(feedbacks, complaintResponse), Result.class, HttpStatus.OK);

        // Accepting a complaint adds a further result rather than replacing the one complained about, and it takes the
        // round after the last one. The client relies on that: it opens the round after the one with the complaint.
        assertThat(resultAfterComplaint).isNotNull();
        assertThat(resultAfterComplaint.getCorrectionRound()).as("the result of an accepted complaint follows the last correction round").isEqualTo(2);

        // Neither of the two rounds loses its own result or moves to another round.
        Submission storedSubmission = submissionRepository.findByIdWithResultsElseThrow(modelingSubmission.getId());
        assertThat(storedSubmission.getResults()).as("the two rounds and the complaint result are all kept").hasSize(3);
        assertThat(storedSubmission.getResultForCorrectionRound(0)).as("the first round still resolves to its own result").isNotNull().extracting(Result::getId)
                .isEqualTo(modelingAssessment.getId());
        assertThat(storedSubmission.getResultForCorrectionRound(1)).as("the second round still resolves to its own result").isNotNull().extracting(Result::getId)
                .isEqualTo(secondRoundAssessment.getId());
        assertThat(storedSubmission.getResultForCorrectionRound(2)).as("the complaint result is the one of the following round").isNotNull().extracting(Result::getId)
                .isEqualTo(resultAfterComplaint.getId());
        // The student is shown the newest result, which is the one the complaint produced.
        assertThat(storedSubmission.getLatestResult()).isNotNull().extracting(Result::getId).isEqualTo(resultAfterComplaint.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "USER")
    void submitComplaintResponse_afterDeletingTheFirstRound_doesNotReuseTheRemainingRound() throws Exception {
        // Two correction rounds, then an instructor deletes the result of the first one, so only round 1 is left.
        Result secondRoundAssessment = participationUtilService.addResultToSubmission(AssessmentType.MANUAL, ZonedDateTime.now(), modelingSubmission, TEST_PREFIX + "instructor1",
                List.of());
        assertThat(secondRoundAssessment.getCorrectionRound()).isEqualTo(1);
        Submission submissionWithResults = submissionRepository.findByIdWithResultsElseThrow(modelingSubmission.getId());
        assessmentService.deleteAssessment(submissionWithResults, submissionWithResults.getResultForCorrectionRound(0));

        Complaint complaintAboutSecondRound = complaintRepo
                .save(new Complaint().result(secondRoundAssessment).complaintText("The second corrector was unfair").complaintType(ComplaintType.COMPLAINT));
        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", complaintAboutSecondRound);
        complaintResponse.getComplaint().setAccepted(true);
        complaintResponse.setResponseText("Accepted");

        List<Feedback> feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        feedbacks.forEach(feedback -> feedback.setType(FeedbackType.MANUAL));
        Result resultAfterComplaint = request.putWithResponseBody("/api/modeling/modeling-submissions/" + modelingSubmission.getId() + "/assessment-after-complaint",
                modelingAssessmentUpdate(feedbacks, complaintResponse), Result.class, HttpStatus.OK);

        // The complaint result takes the round after the highest remaining one. Counting the remaining results would
        // give it round 1, which the complained-about result still holds, and one of the two would then be unreachable.
        assertThat(resultAfterComplaint).isNotNull();
        assertThat(resultAfterComplaint.getCorrectionRound()).as("the result of an accepted complaint follows the highest remaining round").isEqualTo(2);

        Submission storedSubmission = submissionRepository.findByIdWithResultsElseThrow(modelingSubmission.getId());
        assertThat(storedSubmission.getResults()).as("the remaining round and the complaint result are kept").hasSize(2);
        assertThat(storedSubmission.getResults()).extracting(Result::getCorrectionRound).as("each result has its own round").doesNotHaveDuplicates();
        assertThat(storedSubmission.getResultForCorrectionRound(0)).as("the deleted round stays empty").isNull();
        assertThat(storedSubmission.getResultForCorrectionRound(1)).as("the complained-about result still resolves to its round").isNotNull().extracting(Result::getId)
                .isEqualTo(secondRoundAssessment.getId());
        assertThat(storedSubmission.getResultForCorrectionRound(2)).as("the complaint result is the one of the following round").isNotNull().extracting(Result::getId)
                .isEqualTo(resultAfterComplaint.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "USER")
    void submitComplaintResponse_updateAssessment() throws Exception {
        complaint = complaintRepo.save(complaint);
        // creating the initial complaintResponse
        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(true);
        complaintResponse.setResponseText("Accepted");

        List<Feedback> feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        feedbacks.forEach((feedback -> feedback.setType(FeedbackType.MANUAL)));
        final var assessmentUpdate = modelingAssessmentUpdate(feedbacks, complaintResponse);
        Result receivedResult = request.putWithResponseBody("/api/modeling/modeling-submissions/" + modelingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate,
                Result.class, HttpStatus.OK);

        assertThat(((StudentParticipation) receivedResult.getSubmission().getParticipation()).getStudent()).as("student is hidden in response").isEmpty();
        Complaint storedComplaint = complaintRepo.findByResultId(modelingAssessment.getId()).orElseThrow();
        assertThat(storedComplaint.isAccepted()).as("complaint is accepted").isTrue();
        Result result = storedComplaint.getResult();
        // set dates to UTC and round to milliseconds for comparison
        result.setCompletionDate(ZonedDateTime.ofInstant(result.getCompletionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        modelingAssessment.setCompletionDate(ZonedDateTime.ofInstant(modelingAssessment.getCompletionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).orElseThrow();
        Result resultAfterComplaintResponse = resultRepository.findByIdWithEagerFeedbacksAndAssessor(receivedResult.getId()).orElseThrow();
        participationUtilService.checkFeedbackCorrectlyStored(feedbacks, resultAfterComplaintResponse.getFeedbacks(), FeedbackType.MANUAL);
        assertThat(storedResult.getAssessor()).as("assessor is still the original one").isEqualTo(modelingAssessment.getAssessor());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "USER")
    void submitComplaintResponseComplaintResponseTextLimitExceeded() throws Exception {
        complaint = complaintRepo.save(complaint);
        course = courseUtilService.updateCourseComplaintResponseTextLimit(course, 25);
        // creating the initial complaintResponse
        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(true);
        // 26 characters
        complaintResponse.setResponseText("abcdefghijklmnopqrstuvwxyz");

        List<Feedback> feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        feedbacks.forEach((feedback -> feedback.setType(FeedbackType.MANUAL)));
        final var assessmentUpdate = modelingAssessmentUpdate(feedbacks, complaintResponse);
        request.putWithResponseBody("/api/modeling/modeling-submissions/" + modelingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate, Result.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "USER")
    void submitComplaintResponseComplaintResponseTextLimitNotExceeded() throws Exception {
        complaint = complaintRepo.save(complaint);
        course = courseUtilService.updateCourseComplaintResponseTextLimit(course, 26);
        // creating the initial complaintResponse
        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(true);
        // 26 characters
        complaintResponse.setResponseText("abcdefghijklmnopqrstuvwxyz");

        List<Feedback> feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        feedbacks.forEach((feedback -> feedback.setType(FeedbackType.MANUAL)));
        final var assessmentUpdate = modelingAssessmentUpdate(feedbacks, complaintResponse);
        request.putWithResponseBody("/api/modeling/modeling-submissions/" + modelingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate, Result.class,
                HttpStatus.OK);
        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "USER")
    void submitComplaintResponse_examExercise() throws Exception {
        TextExercise examExercise = examUtilService.addEnrolledCourseExamExerciseGroupWithOneTextExercise(TEST_PREFIX);
        Course examCourse = examExercise.getCourseViaExerciseGroupOrCourseMember();

        Exam exam = examExercise.getExam();
        exam.setExamStudentReviewStart(ZonedDateTime.now().minusHours(1));
        exam.setExamStudentReviewEnd(ZonedDateTime.now().plusHours(1));
        examTestRepository.save(exam);

        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("This is my submission", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(examExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        Complaint examExerciseComplaint = new Complaint().result(textSubmission.getLatestResult()).complaintType(ComplaintType.COMPLAINT);
        examExerciseComplaint = complaintRepo.save(examExerciseComplaint);

        examCourse = courseUtilService.updateCourseComplaintResponseTextLimit(examCourse, 20);
        courseRepository.save(examCourse);

        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", examExerciseComplaint);
        complaintResponse.getComplaint().setAccepted(true);
        // 26 characters, above course limit but valid for exam exercises (where complaint limits don't apply)
        complaintResponse.setResponseText("abcdefghijklmnopqrstuvwxyz");
        ComplaintResponseUpdateDTO complaintResponseUpdate = new ComplaintResponseUpdateDTO("abcdefghijklmnopqrstuvwxyz", true, ComplaintAction.RESOLVE_COMPLAINT);

        request.patch("/api/assessment/complaints/" + examExerciseComplaint.getId() + "/response", complaintResponseUpdate, HttpStatus.OK);

        assertThat(textSubmission.getLatestResult()).isNotNull();
        assertThat(complaintRepo.findByResultId(textSubmission.getLatestResult().getId())).isPresent();

        Complaint finalExamExerciseComplaint = examExerciseComplaint;
        await().untilAsserted(() -> assertThat(complaintResponseTestRepository.findByComplaintId(finalExamExerciseComplaint.getId())).isPresent());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void complaintListRejectsCombinedCourseAndExerciseSelectors() throws Exception {
        var selector = "?complaintType=COMPLAINT&courseId=" + course.getId() + "&exerciseId=" + modelingExercise.getId();
        request.getList("/api/assessment/complaints" + selector, HttpStatus.BAD_REQUEST, ComplaintDTO.class);
        request.getList("/api/assessment/complaints/for-tutor" + selector, HttpStatus.BAD_REQUEST, ComplaintDTO.class);
    }

    @ParameterizedTest
    @EnumSource(CourseRole.class)
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void complaintReadAudiencesUsePersistedCourseRoles(CourseRole role) throws Exception {
        User caller = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        userUtilService.unenrollUserFromCourse(caller, course);
        userUtilService.enrollUserInCourse(caller, course, role);
        var staffStatus = role.isAtLeast(CourseRole.TEACHING_ASSISTANT) ? HttpStatus.OK : HttpStatus.FORBIDDEN;
        var instructorStatus = role == CourseRole.INSTRUCTOR ? HttpStatus.OK : HttpStatus.FORBIDDEN;
        var detail = "/api/assessment/complaints/for-assessment?submissionId=" + modelingSubmission.getId();
        var emptyDetail = request.get(detail, staffStatus, String.class);
        if (staffStatus == HttpStatus.OK) {
            assertThat(emptyDetail).isEmpty();
        }
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaintRepo.save(complaint);
        var detailResponse = request.get(detail, staffStatus, ComplaintDTO.class);
        if (staffStatus == HttpStatus.OK) {
            assertThat(detailResponse.result().id()).isEqualTo(modelingAssessment.getId());
            assertThat(detailResponse.result().submission().participation()).isNull();
            assertThat(detailResponse.result().assessor()).isNotNull();
            if (role == CourseRole.INSTRUCTOR) {
                assertThat(detailResponse.participant().id()).isEqualTo(complaint.getStudent().getId());
            }
            else {
                assertThat(detailResponse.participant()).isNull();
            }
        }
        request.get("/api/assessment/complaints?submissionId=" + modelingSubmission.getId(), HttpStatus.FORBIDDEN, ComplaintDTO.class);
        for (String selector : List.of("courseId=" + course.getId(), "exerciseId=" + modelingExercise.getId())) {
            request.getList("/api/assessment/complaints?complaintType=COMPLAINT&" + selector, instructorStatus, ComplaintDTO.class);
            request.getList("/api/assessment/complaints/for-tutor?complaintType=COMPLAINT&allComplaintsForTutor=true&" + selector, staffStatus, ComplaintDTO.class);
        }
        var queue = "/api/exercise/exercises/" + modelingExercise.getId() + "/submissions-with-complaints";
        request.getList(queue, staffStatus, SubmissionWithComplaintDTO.class);
        request.getList(queue + "/for-instructor", instructorStatus, SubmissionWithComplaintDTO.class);
        userUtilService.unenrollUserFromCourse(caller, course);
        userUtilService.enrollUserInCourse(caller, courseUtilService.addEmptyCourse(), CourseRole.INSTRUCTOR);
        request.get(detail, HttpStatus.FORBIDDEN, ComplaintDTO.class);
        request.getList(queue, HttpStatus.FORBIDDEN, SubmissionWithComplaintDTO.class);
        request.getList(queue + "/for-instructor", HttpStatus.FORBIDDEN, SubmissionWithComplaintDTO.class);
        for (String selector : List.of("courseId=" + course.getId(), "exerciseId=" + modelingExercise.getId())) {
            request.getList("/api/assessment/complaints?complaintType=COMPLAINT&" + selector, HttpStatus.FORBIDDEN, ComplaintDTO.class);
            request.getList("/api/assessment/complaints/for-tutor?complaintType=COMPLAINT&" + selector, HttpStatus.FORBIDDEN, ComplaintDTO.class);
        }
    }

    @ParameterizedTest
    @CsvSource({ "true,tutor", "false,tutor", "true,student", "false,student", "true,foreign", "false,foreign", "true,demoted", "false,demoted" })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void emptyAssessmentComplaintAuthorizesTemplateAndSolutionSubmissions(boolean template, String audience) throws Exception {
        var programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        if (template) {
            programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        }
        else {
            programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        }
        Result producedResult = template ? programmingExerciseUtilService.addTemplateSubmissionWithResult(programmingExercise)
                : programmingExerciseUtilService.addSolutionSubmissionWithResult(programmingExercise);
        Long submissionId = producedResult.getSubmission().getId();
        Long participationId = producedResult.getSubmission().getParticipation().getId();
        Long resultId = producedResult.getId();
        String callerLogin = TEST_PREFIX + (audience.equals("student") ? "student1" : audience.equals("foreign") ? "tutor2" : "tutor1");
        User caller = userUtilService.getUserByLogin(callerLogin);
        if (audience.equals("foreign")) {
            userUtilService.unenrollUserFromCourse(caller, course);
            userUtilService.enrollUserInCourse(caller, courseUtilService.addEmptyCourse(), CourseRole.INSTRUCTOR);
        }
        else if (audience.equals("demoted")) {
            userUtilService.unenrollUserFromCourse(caller, course);
            userUtilService.enrollUserInCourse(caller, course, CourseRole.STUDENT);
        }
        userUtilService.changeUser(callerLogin);
        assertThat(request.get("/api/assessment/complaints?submissionId=" + submissionId, HttpStatus.OK, String.class)).isEmpty();
        var expectedStatus = audience.equals("tutor") ? HttpStatus.OK : HttpStatus.FORBIDDEN;
        var staffResponse = request.get("/api/assessment/complaints/for-assessment?submissionId=" + submissionId, expectedStatus, String.class);
        if (expectedStatus == HttpStatus.OK) {
            assertThat(staffResponse).isEmpty();
        }
        var storedSubmission = submissionRepository.findWithEagerResultsAndAssessorById(submissionId).orElseThrow();
        assertThat(storedSubmission.getParticipation()).isInstanceOf(template ? TemplateProgrammingExerciseParticipation.class : SolutionProgrammingExerciseParticipation.class);
        assertThat(storedSubmission.getParticipation().getId()).isEqualTo(participationId);
        assertThat(storedSubmission.getParticipation().getExercise().getId()).isEqualTo(programmingExercise.getId());
        assertThat(storedSubmission.getResults()).extracting(Result::getId).containsExactly(resultId);
        assertThat(resultRepository.findById(resultId).orElseThrow().getSubmission().getId()).isEqualTo(submissionId);
        assertThat(complaintRepo.findByResultSubmissionId(submissionId)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void emptyAssessmentComplaintWithoutParticipationReturnsNotFound() throws Exception {
        var example = participationUtilService.addExampleSubmission(participationUtilService.generateExampleSubmission("{}", modelingExercise, true));
        Submission exampleSubmission = participationUtilService.addResultToSubmission(example.getSubmission(), null, modelingExercise.getId());
        Long submissionId = exampleSubmission.getId();
        Long resultId = exampleSubmission.getLatestResult().getId();
        assertThat(exampleSubmission.getParticipation()).isNull();
        assertThat(request.get("/api/assessment/complaints?submissionId=" + submissionId, HttpStatus.OK, String.class)).isEmpty();
        request.get("/api/assessment/complaints/for-assessment?submissionId=" + submissionId, HttpStatus.NOT_FOUND, Void.class);
        var storedExample = exampleSubmissionRepository.findById(example.getId()).orElseThrow();
        assertThat(storedExample.getExercise().getId()).isEqualTo(modelingExercise.getId());
        assertThat(storedExample.getSubmission().getId()).isEqualTo(submissionId);
        var storedSubmission = submissionRepository.findWithEagerResultsAndAssessorById(submissionId).orElseThrow();
        assertThat(storedSubmission.getParticipation()).isNull();
        assertThat(storedSubmission.isExampleSubmission()).isTrue();
        assertThat(storedSubmission.getResults()).extracting(Result::getId).containsExactly(resultId);
        assertThat(resultRepository.findById(resultId).orElseThrow().getSubmission().getId()).isEqualTo(submissionId);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void complaintReviewQueuesExcludeStudentAssessorTestRuns() throws Exception {
        User instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        var participation = (StudentParticipation) modelingSubmission.getParticipation();
        participation.setParticipant(instructor);
        studentParticipationRepository.save(participation);
        modelingAssessment.setAssessor(instructor);
        modelingAssessment.setHasComplaint(true);
        resultRepository.save(modelingAssessment);
        complaint.setParticipant(instructor);
        complaintRepo.save(complaint);
        var url = "/api/exercise/exercises/" + modelingExercise.getId() + "/submissions-with-complaints";
        assertThat(request.getList(url, HttpStatus.OK, SubmissionWithComplaintDTO.class)).isEmpty();
        assertThat(request.getList(url + "/for-instructor", HttpStatus.OK, SubmissionWithComplaintDTO.class)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void complaintOwnerRouteRejectsUnrelatedInstructor() throws Exception {
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaintRepo.save(complaint);
        request.get("/api/assessment/complaints?submissionId=" + modelingSubmission.getId(), HttpStatus.FORBIDDEN, ComplaintDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void complaintOwnerRouteKeepsStudentProjectionForInstructorOwner() throws Exception {
        var participation = (StudentParticipation) modelingSubmission.getParticipation();
        participation.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"));
        studentParticipationRepository.save(participation);
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"));
        complaint = complaintRepo.save(complaint);
        complaintResponseTestRepository.save(complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", complaint));
        var received = request.get("/api/assessment/complaints?submissionId=" + modelingSubmission.getId(), HttpStatus.OK, ComplaintDTO.class);
        assertThat(received.participant()).isNull();
        assertThat(received.result().assessor()).isNull();
        assertThat(received.result().submission().participation()).isNull();
        assertThat(received.complaintResponse().reviewer()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void emptyOwnerComplaintRemainsAccessibleBeforeOwnership() throws Exception {
        assertThat(request.get("/api/assessment/complaints?submissionId=" + modelingSubmission.getId(), HttpStatus.OK, String.class)).isEmpty();
        request.get("/api/assessment/complaints/for-assessment?submissionId=" + modelingSubmission.getId(), HttpStatus.FORBIDDEN, Void.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void assessmentComplaintChecksActualExerciseEvenWhenEmptyAndAfterDemotion() throws Exception {
        var url = "/api/assessment/complaints/for-assessment?submissionId=" + modelingSubmission.getId();
        assertThat(request.get(url, HttpStatus.OK, String.class)).isEmpty();
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaint = complaintRepo.save(complaint);
        complaintResponseTestRepository.save(complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", complaint));
        var received = request.get(url, HttpStatus.OK, ComplaintDTO.class);
        assertThat(received.complaintResponse().reviewer().getLogin()).isEqualTo(TEST_PREFIX + "tutor2");
        assertThat(received.result().id()).isEqualTo(modelingAssessment.getId());
        assertThat(received.result().assessor()).isNotNull();
        assertThat(received.participant()).isNull();
        assertThat(received.result().submission().participation()).isNull();
        var storedComplaint = complaintRepo.findById(complaint.getId()).orElseThrow();
        assertThat(storedComplaint.getResult().getId()).isEqualTo(modelingAssessment.getId());
        assertThat(storedComplaint.getResult().getSubmission().getParticipation().getId()).isEqualTo(modelingSubmission.getParticipation().getId());
        var emptySubmission = participationUtilService.addSubmission(modelingExercise, new ModelingSubmission(), TEST_PREFIX + "student2");
        userUtilService.unenrollUserFromCourse(userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"), course);
        request.get(url, HttpStatus.FORBIDDEN, ComplaintDTO.class);
        request.get("/api/assessment/complaints/for-assessment?submissionId=" + emptySubmission.getId(), HttpStatus.FORBIDDEN, Void.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void tutorCannotUseInstructorListOrOverrideTutorScope() throws Exception {
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaintRepo.save(complaint);
        var selector = "?complaintType=COMPLAINT&exerciseId=" + modelingExercise.getId();
        request.getList("/api/assessment/complaints" + selector, HttpStatus.FORBIDDEN, ComplaintDTO.class);
        var mine = request.getList("/api/assessment/complaints/for-tutor" + selector + "&tutorId=" + userUtilService.getUserByLogin(TEST_PREFIX + "tutor2").getId(), HttpStatus.OK,
                ComplaintDTO.class);
        assertThat(mine).extracting(ComplaintDTO::id).containsExactly(complaint.getId());
        assertThat(mine.getFirst().participant()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void instructorChoosingTutorListReceivesAnonymousProjection() throws Exception {
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaintRepo.save(complaint);
        var selector = "?complaintType=COMPLAINT&courseId=" + course.getId();
        var identified = request.getList("/api/assessment/complaints" + selector, HttpStatus.OK, ComplaintDTO.class);
        assertThat(identified).hasSize(1);
        assertThat(identified.getFirst().participant()).isNotNull();
        var anonymous = request.getList("/api/assessment/complaints/for-tutor" + selector + "&allComplaintsForTutor=true", HttpStatus.OK, ComplaintDTO.class);
        assertThat(anonymous).hasSize(1);
        assertThat(anonymous.getFirst().participant()).isNull();
        assertThat(anonymous.getFirst().result().assessor()).isNull();
        assertThat(anonymous.getFirst().assessorKey()).isEqualTo(String.valueOf(modelingAssessment.getAssessor().getId()));
        assertThat(anonymous.getFirst().assessorLabel()).isNotBlank();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void complaintReviewQueueUsesExplicitAudienceEvenForInstructor() throws Exception {
        modelingAssessment.setAssessor(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"));
        modelingAssessment.setHasComplaint(true);
        resultRepository.save(modelingAssessment);
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaintRepo.save(complaint);
        var url = "/api/exercise/exercises/" + modelingExercise.getId() + "/submissions-with-complaints";
        assertThat(request.getList(url, HttpStatus.OK, SubmissionWithComplaintDTO.class)).isEmpty();
        var instructorQueue = request.getList(url + "/for-instructor", HttpStatus.OK, SubmissionWithComplaintDTO.class);
        assertThat(instructorQueue).hasSize(1);
        assertThat(instructorQueue.getFirst().complaint().id()).isEqualTo(complaint.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getComplaintByResultIdNoComplaintExists() throws Exception {
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("submissionId", modelingSubmission.getId().toString());

        request.get("/api/assessment/complaints", HttpStatus.OK, Void.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getComplaintByResultId_assessorHiddenForStudent() throws Exception {
        // Get a fresh copy of the result from the database
        var freshResult = resultRepository.findById(modelingAssessment.getId()).orElseThrow();

        // Create a fresh complaint object instead of using the one from setup
        var freshComplaint = new Complaint().result(freshResult).complaintText("This is not fair").complaintType(ComplaintType.COMPLAINT);

        complaintRepo.saveAndFlush(freshComplaint);

        final var params = new LinkedMultiValueMap<String, String>();
        params.add("submissionId", modelingSubmission.getId().toString());
        ComplaintDTO receivedComplaint = request.get("/api/assessment/complaints", HttpStatus.OK, ComplaintDTO.class, params);

        assertThat(receivedComplaint.result().assessor()).as("assessor is not set").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2")
    void getComplaintByResultId_studentAndNotOwner_forbidden() throws Exception {
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaintRepo.save(complaint);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("submissionId", modelingSubmission.getId().toString());

        request.get("/api/assessment/complaints", HttpStatus.FORBIDDEN, ComplaintDTO.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1")
    void getComplaintByResultId_instructor_sensitiveDataHidden() throws Exception {
        complaintRepo.save(complaint);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("submissionId", modelingSubmission.getId().toString());

        final var received = request.get("/api/assessment/complaints/for-assessment", HttpStatus.OK, ComplaintDTO.class, params);

        assertThat(received.result().submission().participation()).as("Complaint should not contain participation").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1")
    void getComplaintByResultId_tutor_sensitiveDataHidden() throws Exception {
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaintRepo.save(complaint);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("submissionId", modelingSubmission.getId().toString());

        final var received = request.get("/api/assessment/complaints/for-assessment", HttpStatus.OK, ComplaintDTO.class, params);

        assertThat(received.participant()).as("Tutors should not see the student of a complaint").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getComplaintByResultId_student_sensitiveDataHidden() throws Exception {
        complaint = complaintRepo.save(complaint);
        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", complaint);
        complaintResponseTestRepository.save(complaintResponse);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("submissionId", modelingSubmission.getId().toString());

        final var received = request.get("/api/assessment/complaints", HttpStatus.OK, ComplaintDTO.class, params);

        assertThat(received.participant()).as("The participant should always be hidden").isNull();
        assertThat(received.result().assessor()).as("Students should not see the initial assessor").isNull();
        assertThat(received.complaintResponse().reviewer()).as("Students should not see the complaint reviewer").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void getComplaintsByCourseIdTutorIsNotTutorForCourse() throws Exception {
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaintRepo.save(complaint);
        User tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        userUtilService.unenrollUserFromCourse(tutor, course);

        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        params.add("courseId", modelingExercise.getCourseViaExerciseGroupOrCourseMember().getId().toString());

        request.getList("/api/assessment/complaints", HttpStatus.FORBIDDEN, ComplaintDTO.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void getComplaintsByCourseId_tutor_sensitiveDataHidden() throws Exception {
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaintRepo.save(complaint);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        params.add("courseId", modelingExercise.getCourseViaExerciseGroupOrCourseMember().getId().toString());

        final var complaints = request.getList("/api/assessment/complaints/for-tutor", HttpStatus.OK, ComplaintDTO.class, params);

        complaints.forEach(c -> checkComplaintContainsNoSensitiveData(c, true));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void getComplaintsByCourseId_tutor_allComplaintsForTutor() throws Exception {
        complaint.getResult().setAssessor(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"));
        resultRepository.save(complaint.getResult());
        complaintRepo.save(complaint);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        params.add("courseId", modelingExercise.getCourseViaExerciseGroupOrCourseMember().getId().toString());

        final var tutorComplaints = request.getList("/api/assessment/complaints/for-tutor", HttpStatus.OK, ComplaintDTO.class, params);
        assertThat(tutorComplaints).isEmpty();

        params.add("allComplaintsForTutor", "true");
        final var allComplaints = request.getList("/api/assessment/complaints/for-tutor", HttpStatus.OK, ComplaintDTO.class, params);

        assertThat(allComplaints).hasSize(1);
        allComplaints.forEach(c -> checkComplaintContainsNoSensitiveData(c, true));

        // Check assessor is filtered out if the user was not the assessor.
        allComplaints.forEach(c -> assertThat(c.result().assessor()).isNull());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void getComplaintsByCourseId_tutor_allComplaintsForTutor_exposesAssessorKeyAndLabelForMultipleForeignTutors() throws Exception {
        User instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        User tutor2 = userUtilService.getUserByLogin(TEST_PREFIX + "tutor2");
        // Names must not embed logins — otherwise "label must not contain login" is a false failure.
        instructor.setFirstName("Alice");
        instructor.setLastName("Instructor");
        tutor2.setFirstName("Bob");
        tutor2.setLastName("Tutor");
        userTestRepository.save(instructor);
        userTestRepository.save(tutor2);

        complaint.getResult().setAssessor(instructor);
        resultRepository.save(complaint.getResult());
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaintRepo.save(complaint);

        ModelingSubmission secondSubmission = ParticipationFactory
                .generateModelingSubmission(TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727.json"), true);
        secondSubmission = modelingExerciseUtilService.addModelingSubmission(modelingExercise, secondSubmission, TEST_PREFIX + "student2");
        Result secondAssessment = modelingExerciseUtilService.addModelingAssessmentForSubmission(modelingExercise, secondSubmission,
                "test-data/model-assessment/assessment.54727.v2.json", TEST_PREFIX + "tutor2", true);
        Complaint secondComplaint = new Complaint().result(secondAssessment).complaintText("Also unfair").complaintType(ComplaintType.COMPLAINT)
                .participant(userUtilService.getUserByLogin(TEST_PREFIX + "student2"));
        complaintRepo.save(secondComplaint);

        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        params.add("courseId", modelingExercise.getCourseViaExerciseGroupOrCourseMember().getId().toString());
        params.add("allComplaintsForTutor", "true");

        final var allComplaints = request.getList("/api/assessment/complaints/for-tutor", HttpStatus.OK, ComplaintDTO.class, params);

        assertThat(allComplaints).hasSize(2);
        allComplaints.forEach(c -> {
            checkComplaintContainsNoSensitiveData(c, true);
            assertThat(c.result().assessor()).as("foreign assessor public-info must stay redacted").isNull();
            assertThat(c.assessorKey()).isNotBlank();
            assertThat(c.assessorLabel()).isNotBlank();
            assertThat(c.assessorLabel()).doesNotContain(instructor.getLogin()).doesNotContain(tutor2.getLogin());
        });

        assertThat(allComplaints).extracting(ComplaintDTO::assessorKey).containsExactlyInAnyOrder(String.valueOf(instructor.getId()), String.valueOf(tutor2.getId()));
        assertThat(allComplaints).extracting(ComplaintDTO::assessorLabel).containsExactlyInAnyOrder("Alice Instructor", "Bob Tutor");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void getComplaintsByExerciseId_tutor_allComplaintsForTutor_returnsOnlyThatExercise() throws Exception {
        User instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        instructor.setFirstName("Alice");
        instructor.setLastName("Instructor");
        userTestRepository.save(instructor);

        complaint.getResult().setAssessor(instructor);
        resultRepository.save(complaint.getResult());
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaintRepo.save(complaint);

        // A complaint on a different exercise of the same course must not leak into the exercise-scoped response.
        TextExercise textExercise = ExerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Other exercise", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student2", TEST_PREFIX + "tutor2");
        Complaint otherExerciseComplaint = new Complaint().result(textSubmission.getLatestResult()).complaintText("Other exercise complaint").complaintType(ComplaintType.COMPLAINT)
                .participant(userUtilService.getUserByLogin(TEST_PREFIX + "student2"));
        complaintRepo.save(otherExerciseComplaint);

        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        params.add("exerciseId", modelingExercise.getId().toString());
        params.add("allComplaintsForTutor", "true");

        final var allComplaints = request.getList("/api/assessment/complaints/for-tutor", HttpStatus.OK, ComplaintDTO.class, params);

        assertThat(allComplaints).hasSize(1);
        ComplaintDTO received = allComplaints.getFirst();
        assertThat(received.id()).isEqualTo(complaint.getId());
        checkComplaintContainsNoSensitiveData(received, true);
        assertThat(received.result().assessor()).as("foreign assessor public-info must stay redacted").isNull();
        assertThat(received.assessorKey()).isEqualTo(String.valueOf(instructor.getId()));
        assertThat(received.assessorLabel()).isEqualTo("Alice Instructor");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void getComplaintsForAssessmentDashboardTutorIsNotTutorForCourse() throws Exception {
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaintRepo.save(complaint);
        User tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        userUtilService.unenrollUserFromCourse(tutor, course);

        final var params = new LinkedMultiValueMap<String, String>();
        request.getList("/api/exercise/exercises/" + modelingExercise.getId() + "/submissions-with-complaints", HttpStatus.FORBIDDEN, Complaint.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void getComplaintsForAssessmentDashboard_complaintOnAthenaResult_returnsComplaint() throws Exception {
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaint.getResult().setHasComplaint(true);
        complaint.getResult().setAssessmentType(AssessmentType.AUTOMATIC_ATHENA);
        complaint.getResult().setSuccessful(true);
        complaint.getResult().setAssessor(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"));
        resultRepository.save(complaint.getResult());
        complaintRepo.save(complaint);

        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        final var submissionWithComplaintDTOs = request.getList("/api/exercise/exercises/" + modelingExercise.getId() + "/submissions-with-complaints", HttpStatus.OK,
                SubmissionWithComplaintDTO.class, params);

        assertThat(submissionWithComplaintDTOs).hasSize(1);
        assertThat(submissionWithComplaintDTOs.getFirst().complaint().result().assessmentType()).isEqualTo(AssessmentType.AUTOMATIC_ATHENA);
        // the listed submission has its Athena results stripped, so the dashboard can only read `successful` off the
        // complaint - without it the row shows the spinner and "AI feedback in progress" forever
        assertThat(submissionWithComplaintDTOs.getFirst().complaint().result().successful()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitComplaintAboutPreliminaryAthenaExamFeedback_isRejected() throws Exception {
        final TextExercise examExercise = examUtilService.addEnrolledCourseExamWithReviewDatesExerciseGroupWithOneTextExercise(TEST_PREFIX);
        final TextSubmission submission = ParticipationFactory.generateTextSubmission("This is my submission", Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(examExercise, submission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        final Result result = submission.getLatestResult();
        assertThat(result).isNotNull();
        result.setAssessmentType(AssessmentType.AUTOMATIC_ATHENA);
        resultRepository.save(result);
        final var requestDto = new ComplaintRequestDTO(result.getId(), "This is not fair", ComplaintType.COMPLAINT, Optional.of(examExercise.getExam().getId()));

        request.post("/api/assessment/complaints", requestDto, HttpStatus.BAD_REQUEST);

        assertThat(complaintRepo.findByResultId(result.getId())).isNotPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void getComplaintsForAssessmentDashboard_rejectedComplaint_staysRejectedOnTheWire() throws Exception {
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaint.setAccepted(false);
        complaint.getResult().setHasComplaint(true);
        complaint.getResult().setAssessor(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"));
        resultRepository.save(complaint.getResult());
        complaintRepo.save(complaint);

        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        final var json = request.get("/api/exercise/exercises/" + modelingExercise.getId() + "/submissions-with-complaints", HttpStatus.OK, JsonNode.class, params);

        assertThat(json).hasSize(1);
        final var listed = json.get(0);
        // the dashboard prints "rejected" for false and "no reply" for absent, so a dropped false would silently
        // reopen every rejected complaint on the tutor dashboard
        assertThat(listed.path("complaint").path("complaintIsAccepted").isBoolean()).isTrue();
        assertThat(listed.path("complaint").path("complaintIsAccepted").asBoolean()).isFalse();
        // the evaluate link opens the assessment of the listed submission's participation
        assertThat(listed.path("submission").path("participation").path("id").asLong()).isEqualTo(modelingSubmission.getParticipation().getId());
        // completeComplainedResult matches the complained result in the listed results by id, and the dashboard filters those
        // listed results by assessment type before it links into the assessment editor
        final long complainedResultId = listed.path("complaint").path("result").path("id").asLong();
        assertThat(complainedResultId).isEqualTo(complaint.getResult().getId());
        assertThat(listed.path("submission").path("results")).anySatisfy(result -> {
            assertThat(result.path("id").asLong()).isEqualTo(complainedResultId);
            assertThat(result.path("assessmentType").asString()).isEqualTo(complaint.getResult().getAssessmentType().name());
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void getComplaintsForAssessmentDashboard_sameTutorAsAssessor_studentInfoHidden() throws Exception {
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaintRepo.save(complaint);
        complaint.getResult().setHasComplaint(true);
        resultRepository.save(complaint.getResult());

        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        final var submissionWithComplaintDTOs = request.getList("/api/exercise/exercises/" + modelingExercise.getId() + "/submissions-with-complaints", HttpStatus.OK,
                SubmissionWithComplaintDTO.class, params);

        submissionWithComplaintDTOs.forEach(dto -> {
            assertThat(dto.complaint().participant()).as("No student information").isNull();
            assertThat(dto.complaint().result().submission().participation().exercise()).as("No additional exercise information").isNull();
            assertThat(dto.submission().participation().participantName()).as("No student information in participation").isNull();
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void getComplaintsForAssessmentDashboardTestRunTutorIsNotTutorForCourse() throws Exception {
        User instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        complaint.setParticipant(instructor);
        complaint.getResult().setAssessor(instructor);
        resultRepository.save(complaint.getResult());
        complaint = complaintRepo.save(complaint);
        userUtilService.unenrollUserFromCourse(instructor, course);

        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        params.add("exerciseId", modelingExercise.getId().toString());
        request.getList("/api/assessment/complaints", HttpStatus.FORBIDDEN, Complaint.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void getComplaintsForAssessmentDashboard_testRun() throws Exception {
        User instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        complaint.setParticipant(instructor);
        complaint.getResult().setAssessor(instructor);
        resultRepository.save(complaint.getResult());
        complaint = complaintRepo.save(complaint);

        final var params = new LinkedMultiValueMap<String, String>();
        params.add("exerciseId", modelingExercise.getId().toString());

        final var complaints = request.getList("/api/assessment/complaints", HttpStatus.OK, ComplaintDTO.class, params);
        assertThat(complaints).hasSize(1);
        complaints.forEach(compl -> {
            assertThat(compl.result().id()).isEqualTo(complaint.getResult().getId());
            assertThat(compl.participant()).as("No student information").isNull();
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void getComplaintsForAssessmentDashboard_testRun_emptyComplaints() throws Exception {

        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        params.add("exerciseId", modelingExercise.getId().toString());

        final var complaints = request.getList("/api/assessment/complaints", HttpStatus.OK, Complaint.class, params);
        assertThat(complaints).hasSize(0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void getSubmittedComplaints_byComplaintType() throws Exception {
        complaintUtilService.addComplaints(TEST_PREFIX + "student1", modelingAssessment.getSubmission(), 1, ComplaintType.COMPLAINT);
        complaintUtilService.addComplaints(TEST_PREFIX + "student1", modelingAssessment.getSubmission(), 2, ComplaintType.MORE_FEEDBACK);

        String complaintsUrl = "/api/assessment/complaints";
        LinkedMultiValueMap<String, String> paramsExercise = new LinkedMultiValueMap<>();
        LinkedMultiValueMap<String, String> paramsCourse = new LinkedMultiValueMap<>();
        paramsExercise.add("complaintType", ComplaintType.COMPLAINT.toString());
        paramsExercise.add("exerciseId", modelingExercise.getId().toString());
        paramsCourse.add("complaintType", ComplaintType.COMPLAINT.toString());
        paramsCourse.add("courseId", modelingExercise.getCourseViaExerciseGroupOrCourseMember().getId().toString());
        List<ComplaintDTO> complaintsByCourse = request.getList(complaintsUrl, HttpStatus.OK, ComplaintDTO.class, paramsCourse);
        List<ComplaintDTO> complaintsByExercise = request.getList(complaintsUrl, HttpStatus.OK, ComplaintDTO.class, paramsExercise);
        assertThat(complaintsByExercise).hasSameSizeAs(complaintsByCourse).hasSize(1);
        assertThat(complaintsByCourse).hasSize(1).allMatch(complaint -> complaint.complaintType() == ComplaintType.COMPLAINT);

        // The exercise (eagerly loaded via the complaint entity graph) must be exposed in the reduced result DTO so the client can render the exercise title.
        ComplaintDTO.ResultSimpleDTO resultDTO = complaintsByCourse.getFirst().result();
        assertThat(resultDTO.exerciseTitle()).as("Exercise title is exposed for complaint lists").isEqualTo(modelingExercise.getTitle());
        assertThat(resultDTO.submission().participation().exercise().id()).as("Exercise id is exposed for complaint lists").isEqualTo(modelingExercise.getId());

        paramsCourse.set("complaintType", ComplaintType.MORE_FEEDBACK.toString());
        paramsExercise.set("complaintType", ComplaintType.MORE_FEEDBACK.toString());
        complaintsByCourse = request.getList(complaintsUrl, HttpStatus.OK, ComplaintDTO.class, paramsCourse);
        complaintsByExercise = request.getList(complaintsUrl, HttpStatus.OK, ComplaintDTO.class, paramsExercise);
        assertThat(complaintsByCourse).hasSameSizeAs(complaintsByExercise).hasSize(2);
        assertThat(complaintsByCourse).hasSize(2).allMatch(complaint -> complaint.complaintType() == ComplaintType.MORE_FEEDBACK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void getSubmittedComplaintsForProgrammingExercise() throws Exception {
        var programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        var programmingSubmission = ParticipationFactory.generateProgrammingSubmission(true);

        programmingExerciseUtilService.addProgrammingSubmissionWithResultAndAssessor(programmingExercise, programmingSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1",
                AssessmentType.MANUAL, false);
        courseRepository.save(course);
        complaintUtilService.addComplaintToSubmission(programmingSubmission, TEST_PREFIX + "student1", ComplaintType.COMPLAINT);
        var programmingResultWithComplaint = programmingSubmission.getResultWithComplaint();
        assertThat(programmingResultWithComplaint).isNotNull();
        var programmingComplaint = complaintRepo.findByResultId(programmingResultWithComplaint.getId()).orElseThrow();
        programmingComplaint.setComplaintText("Programming exercise complaint");
        complaintRepo.save(programmingComplaint);

        String coursesUrl = "/api/assessment/complaints";
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("complaintType", ComplaintType.COMPLAINT.toString());
        params.add("courseId", course.getId().toString());
        List<ComplaintDTO> complaints = request.getList(coursesUrl, HttpStatus.OK, ComplaintDTO.class, params);
        assertThat(complaints).hasSize(1);
        ComplaintDTO complaintFromServer = complaints.getFirst();
        assertThat(complaintFromServer.id()).isEqualTo(programmingComplaint.getId());
        assertThat(complaintFromServer.complaintText()).isEqualTo(programmingComplaint.getComplaintText());

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void getSubmittedComplaintsForFileUploadExercise() throws Exception {
        var fileUploadExercise = ExerciseUtilService.getFirstExerciseWithType(course, FileUploadExercise.class);
        var fileUploadSubmission = ParticipationFactory.generateFileUploadSubmission(true);

        fileUploadSubmission = fileUploadExerciseUtilService.saveFileUploadSubmissionWithResultAndAssessor(fileUploadExercise, fileUploadSubmission, TEST_PREFIX + "student1",
                TEST_PREFIX + "tutor1");
        courseRepository.save(course);
        complaintUtilService.addComplaintToSubmission(fileUploadSubmission, TEST_PREFIX + "student1", ComplaintType.COMPLAINT);
        var fileUploadResultWithComplaint = fileUploadSubmission.getResultWithComplaint();
        assertThat(fileUploadResultWithComplaint).isNotNull();
        var fileUploadComplaint = complaintRepo.findByResultId(fileUploadResultWithComplaint.getId()).orElseThrow();
        fileUploadComplaint.setComplaintText("File upload complaint");
        complaintRepo.save(fileUploadComplaint);

        String coursesUrl = "/api/assessment/complaints/for-tutor";
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("complaintType", ComplaintType.COMPLAINT.toString());
        params.add("courseId", course.getId().toString());
        List<ComplaintDTO> complaints = request.getList(coursesUrl, HttpStatus.OK, ComplaintDTO.class, params);
        assertThat(complaints).hasSize(1);
        ComplaintDTO complaintFromServer = complaints.getFirst();
        assertThat(complaintFromServer.id()).isEqualTo(fileUploadComplaint.getId());
        assertThat(complaintFromServer.complaintText()).isEqualTo(fileUploadComplaint.getComplaintText());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getSubmittedComplaints_asStudent_forbidden() throws Exception {
        complaintRepo.save(complaint);

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("complaintType", ComplaintType.COMPLAINT.toString());
        params.add("exerciseId", modelingExercise.getId().toString());

        request.getList("/api/assessment/complaints", HttpStatus.FORBIDDEN, ComplaintResponse.class, params);
    }

    private void saveModelingSubmissionAndAssessment() throws Exception {
        modelingSubmission = ParticipationFactory.generateModelingSubmission(TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727.json"), true);
        modelingSubmission = modelingExerciseUtilService.addModelingSubmission(modelingExercise, modelingSubmission, TEST_PREFIX + "student1");
        modelingAssessment = modelingExerciseUtilService.addModelingAssessmentForSubmission(modelingExercise, modelingSubmission,
                "test-data/model-assessment/assessment.54727.v2.json", TEST_PREFIX + "tutor1", true);
    }

    private void checkComplaintContainsNoSensitiveData(ComplaintDTO receivedComplaint, boolean shouldStudentBeFilteredOut) {
        if (shouldStudentBeFilteredOut) {
            checkIfNoStudentInformationPresent(receivedComplaint);
        }
    }

    private void checkIfNoStudentInformationPresent(ComplaintDTO receivedComplaint) {
        assertThat(receivedComplaint.participant()).as("Student should not be contained").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void getComplaintsByExerciseIdTutorIsNotTutorForCourse() throws Exception {
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaintRepo.save(complaint);
        User tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        userUtilService.unenrollUserFromCourse(tutor, course);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        params.add("exerciseId", complaint.getResult().getSubmission().getParticipation().getExercise().getId().toString());
        request.getList("/api/assessment/complaints", HttpStatus.FORBIDDEN, Complaint.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void getComplaintsByExerciseId_tutor_sensitiveDataHidden() throws Exception {
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaintRepo.save(complaint);
        var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        var exercise = complaint.getResult().getSubmission().getParticipation().getExercise();
        params.add("exerciseId", exercise.getId().toString());

        var complaints = request.getList("/api/assessment/complaints/for-tutor", HttpStatus.OK, ComplaintDTO.class, params);
        complaints.forEach(complaint -> checkComplaintContainsNoSensitiveData(complaint, true));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void getNumberOfAllowedComplaintsInCourseComplaintsDisabled() throws Exception {
        // complaints enabled will return zero
        course.setMaxComplaintTimeDays(-1);
        courseRepository.save(course);
        var params = new LinkedMultiValueMap<String, String>();
        params.add("courseId", modelingExercise.getCourseViaExerciseGroupOrCourseMember().getId().toString());
        request.get("/api/assessment/complaints", HttpStatus.BAD_REQUEST, Long.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void getNumberOfAllowedComplaintsInCourseTeamMode() throws Exception {
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaintRepo.save(complaint);
        var params = new LinkedMultiValueMap<String, String>();
        params.add("courseId", modelingExercise.getCourseViaExerciseGroupOrCourseMember().getId().toString());
        params.add("teamMode", "true");
        request.get("/api/assessment/complaints", HttpStatus.BAD_REQUEST, Long.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitComplaintForExamExerciseWithinStudentReviewTime() throws Exception {
        final TextExercise examExercise = examUtilService.addEnrolledCourseExamWithReviewDatesExerciseGroupWithOneTextExercise(TEST_PREFIX);
        final long examId = examExercise.getExerciseGroup().getExam().getId();
        final TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("This is my submission", Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(examExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        final var latestResult = textSubmission.getLatestResult();
        assertThat(latestResult).isNotNull();
        final var examExerciseComplaint = new ComplaintRequestDTO(latestResult.getId(), "This is not fair", ComplaintType.COMPLAINT, Optional.of(examId));

        final String url = "/api/assessment/complaints";
        request.post(url, examExerciseComplaint, HttpStatus.CREATED);

        Optional<Complaint> storedComplaint = complaintRepo.findByResultId(textSubmission.getLatestResult().getId());
        assertThat(storedComplaint).as("complaint is saved").isPresent();
        assertThat(storedComplaint.orElseThrow().getComplaintText()).as("complaint text got correctly saved").isEqualTo(examExerciseComplaint.complaintText());
        assertThat(storedComplaint.get().isAccepted()).as("accepted flag of complaint is not set").isNull();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(textSubmission.getLatestResult().getId()).orElseThrow();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is true").isTrue();
        // set a date to UTC for comparison
        storedResult.setCompletionDate(ZonedDateTime.ofInstant(storedResult.getCompletionDate().toInstant(), ZoneId.of("UTC")));
        // TODO add assertion
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitComplaintForCourseExerciseUsingTheExamExerciseCall_badRequest() throws Exception {
        // "Mock Exam" which id is used to call the wrong REST-Call
        final Exam exam = ExamFactory.generateExam(course);
        examTestRepository.save(exam);
        // The complaint is about a course exercise, not an exam exercise
        var complaintRequest = new ComplaintRequestDTO(complaint.getResult().getId(), complaint.getComplaintText(), complaint.getComplaintType(), Optional.of(exam.getId()));

        request.post("/api/assessment/complaints", complaintRequest, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitComplaintForExamExerciseUsingTheCourseExerciseCall_badRequest() throws Exception {
        // Set up Exam, Exercise, Participation and Complaint
        final TextExercise examExercise = examUtilService.addEnrolledCourseExamExerciseGroupWithOneTextExercise(TEST_PREFIX);
        final TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("This is my submission", Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(examExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        var latestResult = textSubmission.getLatestResult();
        assertThat(latestResult).isNotNull();
        var examExerciseComplaint = new ComplaintRequestDTO(latestResult.getId(), "This is not fair", ComplaintType.COMPLAINT, Optional.empty());
        // The complaint is about an exam exercise, but the REST-Call for course exercises is used
        request.post("/api/assessment/complaints", examExerciseComplaint, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitComplaintForExamExerciseWithMismatchedExamId_badRequest() throws Exception {
        final TextExercise examExercise = examUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        final long otherExamId = examUtilService.addCourseExamWithReviewDatesExerciseGroupWithOneTextExercise().getExerciseGroup().getExam().getId();
        final TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("This is my submission", Language.ENGLISH, true);
        final TextSubmission savedSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(examExercise, textSubmission, TEST_PREFIX + "student1",
                TEST_PREFIX + "tutor1");
        final var examExerciseComplaint = new ComplaintRequestDTO(savedSubmission.getLatestResult().getId(), "This is not fair", ComplaintType.COMPLAINT, Optional.of(otherExamId));

        request.post("/api/assessment/complaints", examExerciseComplaint, HttpStatus.BAD_REQUEST);

        assertThat(complaintRepo.findByResultId(savedSubmission.getLatestResult().getId())).as("no complaint is stored outside the review period of the exam").isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitComplaintForExamExerciseOutsideOfStudentReviewTime_badRequest() throws Exception {
        final TextExercise examExercise = examUtilService.addEnrolledCourseExamExerciseGroupWithOneTextExercise(TEST_PREFIX);
        final long examId = examExercise.getExerciseGroup().getExam().getId();
        final TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("This is my submission", Language.ENGLISH, true);
        final TextSubmission savedSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(examExercise, textSubmission, TEST_PREFIX + "student1",
                TEST_PREFIX + "tutor1");
        final var examExerciseComplaint = new ComplaintRequestDTO(savedSubmission.getLatestResult().getId(), "This is not fair", ComplaintType.COMPLAINT, Optional.of(examId));

        final String url = "/api/assessment/complaints";
        request.post(url, examExerciseComplaint, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetComplaintsByCourseIdAndExamIdTutorIsNotTutorForCourse() throws Exception {
        final TextExercise examExercise = examUtilService.addEnrolledCourseExamWithReviewDatesExerciseGroupWithOneTextExercise(TEST_PREFIX);
        final long examId = examExercise.getExerciseGroup().getExam().getId();
        final long courseId = examExercise.getExerciseGroup().getExam().getCourse().getId();
        var params = new LinkedMultiValueMap<String, String>();
        params.add("examId", String.valueOf(examId));
        params.add("courseId", String.valueOf(courseId));

        request.getList("/api/assessment/complaints", HttpStatus.FORBIDDEN, ComplaintDTO.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetComplaintsByCourseIdAndExamId() throws Exception {
        final TextExercise examExercise = examUtilService.addEnrolledCourseExamWithReviewDatesExerciseGroupWithOneTextExercise(TEST_PREFIX);
        final long examId = examExercise.getExerciseGroup().getExam().getId();
        final long courseId = examExercise.getExerciseGroup().getExam().getCourse().getId();
        final TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("This is my submission", Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(examExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        final var latestResult = textSubmission.getLatestResult();
        assertThat(latestResult).isNotNull();
        final var examExerciseComplaint = new ComplaintRequestDTO(latestResult.getId(), "This is not fair", ComplaintType.COMPLAINT, Optional.of(examId));

        final String url = "/api/assessment/complaints";
        request.post(url, examExerciseComplaint, HttpStatus.CREATED);
        var params = new LinkedMultiValueMap<String, String>();
        params.add("examId", String.valueOf(examId));
        params.add("courseId", String.valueOf(courseId));

        Optional<Complaint> storedComplaint = complaintRepo.findByResultId(textSubmission.getLatestResult().getId());
        request.get("/api/assessment/complaints", HttpStatus.FORBIDDEN, List.class, params);
        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        request.get("/api/assessment/complaints", HttpStatus.FORBIDDEN, List.class, params);
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        var fetchedComplaints = request.getList("/api/assessment/complaints", HttpStatus.OK, ComplaintDTO.class, params);
        assertThat(fetchedComplaints.getFirst().id()).isEqualTo(storedComplaint.orElseThrow().getId().intValue());
        assertThat(fetchedComplaints.getFirst().complaintText()).isEqualTo(storedComplaint.get().getComplaintText());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitComplaintForExerciseComplaintExceededTextLimit() throws Exception {
        course = courseUtilService.updateCourseComplaintTextLimit(course, 25);
        // 26 characters
        complaint.setComplaintText("abcdefghijklmnopqrstuvwxyz");
        var complaintRequest = new ComplaintRequestDTO(complaint.getResult().getId(), "abcdefghijklmnopqrstuvwxyz", complaint.getComplaintType(), Optional.empty());
        request.post("/api/assessment/complaints", complaintRequest, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitComplaintForExerciseComplaintNotExceededTextLimit() throws Exception {
        exerciseUtilService.updateExerciseDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(2));
        exerciseUtilService.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(1));
        course = courseUtilService.updateCourseComplaintTextLimit(course, 27);
        // 26 characters
        complaint.setComplaintText("abcdefghijklmnopqrstuvwxyz");
        var complaintRequest = new ComplaintRequestDTO(complaint.getResult().getId(), "abcdefghijklmnopqrstuvwxyz", complaint.getComplaintType(), Optional.empty());
        request.post("/api/assessment/complaints", complaintRequest, HttpStatus.CREATED);
        Optional<Complaint> storedComplaint = complaintRepo.findByResultId(modelingAssessment.getId());
        assertThat(storedComplaint).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitComplaintForExam_courseComplaintsEnabled_exceededCourseLimit_success() throws Exception {
        TextExercise examExercise = examUtilService.addEnrolledCourseExamExerciseGroupWithOneTextExercise(TEST_PREFIX);
        Course examCourse = examExercise.getCourseViaExerciseGroupOrCourseMember();
        examCourse = courseUtilService.updateCourseComplaintTextLimit(examCourse, 25);
        // enable course complaints
        examCourse.setMaxComplaintTimeDays(3);
        courseRepository.save(examCourse);
        // 26 characters, exceeds course limit but lower than 2000 --> allowed for exam exercise
        String complaintText = "abcdefghijklmnopqrstuvwxyz";
        var examSubmission = createComplaintForExamExercise(examExercise, complaintText, HttpStatus.CREATED);
        Optional<Complaint> storedComplaint = complaintRepo.findByResultSubmissionId(examSubmission.getId());
        assertThat(storedComplaint).isPresent();
        assertThat(storedComplaint.get().getComplaintText()).isEqualTo(complaintText);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitComplaintForExam_courseComplaintsDisabled_notExceededTextLimit() throws Exception {
        TextExercise examExercise = examUtilService.addEnrolledCourseExamExerciseGroupWithOneTextExercise(TEST_PREFIX);
        Course examCourse = examExercise.getCourseViaExerciseGroupOrCourseMember();
        // disable course complaints
        examCourse.setMaxComplaintTimeDays(0);
        courseRepository.save(examCourse);
        // less than 2000 characters
        var examSubmission = createComplaintForExamExercise(examExercise, "abcdefghijklmnopqrstuvwxyz", HttpStatus.CREATED);
        Optional<Complaint> storedComplaint = complaintRepo.findByResultSubmissionId(examSubmission.getId());
        assertThat(storedComplaint).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitComplaintForExam_courseComplaintsDisabled_exceededTextLimit() throws Exception {
        TextExercise examExercise = examUtilService.addEnrolledCourseExamExerciseGroupWithOneTextExercise(TEST_PREFIX);
        Course examCourse = examExercise.getCourseViaExerciseGroupOrCourseMember();
        // disable course complaints
        examCourse.setMaxComplaintTimeDays(0);
        courseRepository.save(examCourse);
        // 2004 characters (4 over the limit of 2000)
        createComplaintForExamExercise(examExercise, "abcd".repeat(501), HttpStatus.BAD_REQUEST);
    }

    private Submission createComplaintForExamExercise(TextExercise examExercise, String complaintText, HttpStatus expectedStatus) throws Exception {
        examExercise.getExam().setExamStudentReviewStart(ZonedDateTime.now().minusHours(1));
        examExercise.getExam().setExamStudentReviewEnd(ZonedDateTime.now().plusHours(1));
        examTestRepository.save(examExercise.getExam());
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("This is my submission", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(examExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        var examId = examExercise.getExam().getId();
        final var latestResult = textSubmission.getLatestResult();
        assertThat(latestResult).isNotNull();
        final var examExerciseComplaint = new ComplaintRequestDTO(latestResult.getId(), complaintText, ComplaintType.COMPLAINT, Optional.of(examId));

        String url = "/api/assessment/complaints";
        request.post(url, examExerciseComplaint, expectedStatus);
        return textSubmission;
    }

    /**
     * Builds the request body of the modeling assessment-after-complaint endpoint the way the client sends it: feedbacks as
     * {@link FeedbackDTO}s and the complaint response as a {@link ComplaintResponseRequestDTO}.
     *
     * @param feedbacks         the updated feedback entities
     * @param complaintResponse the complaint response carrying the accept/reject decision
     * @return the request body
     */
    private static ModelingAssessmentUpdateDTO modelingAssessmentUpdate(List<Feedback> feedbacks, ComplaintResponse complaintResponse) {
        Complaint complaint = complaintResponse.getComplaint();
        return new ModelingAssessmentUpdateDTO(feedbacks.stream().map(FeedbackDTO::of).toList(), new ComplaintResponseRequestDTO(complaintResponse.getId(),
                complaintResponse.getResponseText(), new ComplaintResponseRequestDTO.ComplaintRequestDTO(complaint.getId(), complaint.isAccepted())), null);
    }
}
