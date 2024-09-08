package de.tum.in.www1.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.Complaint;
import de.tum.in.www1.artemis.domain.ComplaintResponse;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exercise.modeling.ModelingExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.service.dto.ComplaintAction;
import de.tum.in.www1.artemis.service.dto.ComplaintRequestDTO;
import de.tum.in.www1.artemis.service.dto.ComplaintResponseUpdateDTO;
import de.tum.in.www1.artemis.team.TeamUtilService;
import de.tum.in.www1.artemis.util.TestResourceUtils;
import de.tum.in.www1.artemis.web.rest.dto.AssessmentUpdateDTO;

class AssessmentTeamComplaintIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "assmentteamcomplaint"; // only lower case is supported

    @Autowired
    private ComplaintRepository complaintRepo;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ComplaintUtilService complaintUtilService;

    private ModelingExercise modelingExercise;

    private ModelingSubmission modelingSubmission;

    private Result modelingAssessment;

    private Complaint complaint;

    private ComplaintRequestDTO complaintRequest;

    private Team team;

    private final String resourceUrl = "/api/complaints";

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 2, 0, 1);
        // Initialize with 3 max team complaints and 7 days max complaint due date
        Course course = modelingExerciseUtilService.addCourseWithOneModelingExercise();
        modelingExercise = (ModelingExercise) course.getExercises().iterator().next();
        modelingExercise.setMode(ExerciseMode.TEAM);
        modelingExercise = exerciseRepository.save(modelingExercise);
        team = teamUtilService.addTeamForExercise(modelingExercise, userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"), TEST_PREFIX);
        saveModelingSubmissionAndAssessment();
        complaint = new Complaint().result(modelingAssessment).complaintText("This is not fair").complaintType(ComplaintType.COMPLAINT);
        complaintRequest = new ComplaintRequestDTO(complaint.getResult().getId(), complaint.getComplaintText(), complaint.getComplaintType(), Optional.empty());
    }

    @Test
    @WithMockUser(username = "team1" + TEST_PREFIX + "1")
    void submitComplaintAboutModellingAssessment_complaintLimitNotReached() throws Exception {
        exerciseUtilService.updateExerciseDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(2));
        exerciseUtilService.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(1));

        // 2 complaints are allowed, the course is created with 3 max team complaints
        complaintUtilService.addTeamComplaints(team, modelingAssessment.getSubmission(), 2, ComplaintType.COMPLAINT);

        request.post(resourceUrl, complaintRequest, HttpStatus.CREATED);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is saved").isPresent();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).orElseThrow();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is true").isTrue();
    }

    @Test
    @WithMockUser(username = "team1" + TEST_PREFIX + "1")
    void submitComplaintAboutModelingAssessment_complaintLimitReached() throws Exception {
        complaintUtilService.addTeamComplaints(team, modelingAssessment.getSubmission(), 3, ComplaintType.COMPLAINT);

        request.post(resourceUrl, complaintRequest, HttpStatus.BAD_REQUEST);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is not saved").isNotPresent();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).orElseThrow();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is false").isFalse();
    }

    @Test
    @WithMockUser(username = "team1" + TEST_PREFIX + "1")
    void requestMoreFeedbackAboutModelingAssessment_noLimit() throws Exception {
        exerciseUtilService.updateExerciseDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(2));
        exerciseUtilService.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(1));
        complaintUtilService.addTeamComplaints(team, modelingAssessment.getSubmission(), 5, ComplaintType.MORE_FEEDBACK);

        request.post(resourceUrl, complaintRequest, HttpStatus.CREATED);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is saved").isPresent();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).orElseThrow();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is true").isTrue();

        // Only one complaint is possible for exercise regardless of its type
        var moreFeedbackRequest = new ComplaintRequestDTO(modelingAssessment.getId(), "Please explain", ComplaintType.MORE_FEEDBACK, Optional.empty());
        request.post(resourceUrl, moreFeedbackRequest, HttpStatus.BAD_REQUEST);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId()).orElseThrow().getComplaintType()).as("more feedback request is not saved")
                .isNotEqualTo(ComplaintType.MORE_FEEDBACK);
    }

    @Test
    @WithMockUser(username = "team1" + TEST_PREFIX + "1")
    void submitComplaintAboutModelingAssessment_validDueDate() throws Exception {
        // Mock object initialized with 2 weeks due date. One week after result date is fine.
        exerciseUtilService.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusWeeks(1));
        exerciseUtilService.updateResultCompletionDate(modelingAssessment.getId(), ZonedDateTime.now().minusWeeks(1));

        request.post(resourceUrl, complaintRequest, HttpStatus.CREATED);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is saved").isPresent();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).orElseThrow();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is true").isTrue();
    }

    @Test
    @WithMockUser(username = "team1" + TEST_PREFIX + "1")
    void submitComplaintAboutModelingAssessment_assessmentTooOld() throws Exception {
        // 3 weeks is already past the due date
        exerciseUtilService.updateExerciseDueDate(modelingExercise.getId(), ZonedDateTime.now().minusWeeks(3));
        exerciseUtilService.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusWeeks(3));
        exerciseUtilService.updateResultCompletionDate(modelingAssessment.getId(), ZonedDateTime.now().minusWeeks(3));

        request.post(resourceUrl, complaintRequest, HttpStatus.BAD_REQUEST);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is not saved").isNotPresent();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).orElseThrow();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is false").isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void submitComplaintResponse_rejectComplaint() throws Exception {
        complaint = complaintRepo.saveAndFlush(complaint);

        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor1", complaint);
        ComplaintResponseUpdateDTO complaintResponseUpdate = new ComplaintResponseUpdateDTO("rejected", false, ComplaintAction.RESOLVE_COMPLAINT);

        request.patch("/api/complaints/" + complaint.getId() + "/response", complaintResponseUpdate, HttpStatus.OK);
        assertThat(complaintResponse.getComplaint().getParticipant()).isNull();
        Complaint storedComplaint = complaintRepo.findByResultId(modelingAssessment.getId()).orElseThrow();
        assertThat(storedComplaint.isAccepted()).as("complaint is not accepted").isFalse();
        Result storedResult = resultRepository.findWithBidirectionalSubmissionAndFeedbackAndAssessorAndAssessmentNoteAndTeamStudentsByIdElseThrow(modelingAssessment.getId());
        participationUtilService.checkFeedbackCorrectlyStored(modelingAssessment.getFeedbacks(), storedResult.getFeedbacks(), FeedbackType.MANUAL);
        assertThat(storedResult.getSubmission()).isEqualTo(modelingAssessment.getSubmission());
        assertThat(storedResult.getAssessor()).isEqualTo(modelingAssessment.getAssessor());
        assertThat(storedResult.getSubmission().getParticipation()).isEqualTo(modelingAssessment.getSubmission().getParticipation());
        assertThat(storedResult.getFeedbacks()).containsExactlyInAnyOrderElementsOf(modelingAssessment.getFeedbacks());
        final String[] ignoringFields = { "feedbacks", "submission", "participation", "assessor" };
        assertThat(storedResult).as("only feedbacks are changed in the result").usingRecursiveComparison().ignoringFields(ignoringFields).isEqualTo(modelingAssessment);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void submitComplaintResponse_rejectComplaint_asOtherTutor_forbidden() throws Exception {
        complaint = complaintRepo.save(complaint);
        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(false);
        complaintResponse.setResponseText("rejected");

        ComplaintResponseUpdateDTO complaintResponseUpdate = new ComplaintResponseUpdateDTO("rejected", false, ComplaintAction.RESOLVE_COMPLAINT);

        request.patch("/api/complaints/" + complaint.getId() + "/response", complaintResponseUpdate, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void submitComplaintResponse_updateAssessment() throws Exception {
        complaint = complaintRepo.save(complaint);

        List<Feedback> feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        feedbacks.forEach((feedback -> feedback.setType(FeedbackType.MANUAL)));
        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor1", complaint);
        complaintResponse.getComplaint().setAccepted(true);
        complaintResponse.setResponseText("accepted");

        final var assessmentUpdate = new AssessmentUpdateDTO(feedbacks, complaintResponse, null);
        Result receivedResult = request.putWithResponseBody("/api/modeling-submissions/" + modelingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate,
                Result.class, HttpStatus.OK);

        assertThat(((StudentParticipation) receivedResult.getSubmission().getParticipation()).getStudent()).as("student is hidden in response").isEmpty();
        Complaint storedComplaint = complaintRepo.findByResultId(modelingAssessment.getId()).orElseThrow();
        assertThat(storedComplaint.isAccepted()).as("complaint is accepted").isTrue();
        Result resultOfComplaint = storedComplaint.getResult();
        // set dates to UTC and round to milliseconds for comparison
        resultOfComplaint.setCompletionDate(ZonedDateTime.ofInstant(resultOfComplaint.getCompletionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        modelingAssessment.setCompletionDate(ZonedDateTime.ofInstant(modelingAssessment.getCompletionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        assertThat(resultOfComplaint.getAssessor()).isEqualTo(modelingAssessment.getAssessor());
        assertThat(resultOfComplaint).isEqualTo(modelingAssessment);
        Submission submission = submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(modelingAssessment.getSubmission().getId());
        assertThat(submission.getLastResult()).isNotNull();
        assertThat(submission.getFirstResult()).isNotNull();
        participationUtilService.checkFeedbackCorrectlyStored(feedbacks, submission.getLastResult().getFeedbacks(), FeedbackType.MANUAL);
        assertThat(submission.getFirstResult().getAssessor()).as("assessor is still the original one").isEqualTo(modelingAssessment.getAssessor());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void submitComplaintResponse_updateAssessment_asOtherTutor_forbidden() throws Exception {
        complaint = complaintRepo.save(complaint);

        List<Feedback> feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(true);
        complaintResponse.setResponseText("accepted");

        final var assessmentUpdate = new AssessmentUpdateDTO(feedbacks, complaintResponse, null);
        request.putWithResponseBody("/api/modeling-submissions/" + modelingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate, Result.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getComplaintByResultId_studentAndNotPartOfTeam_forbidden() throws Exception {
        complaint.setParticipant(team);
        complaintRepo.save(complaint);

        request.get("/api/complaints?submissionId=" + modelingSubmission.getId(), HttpStatus.FORBIDDEN, Complaint.class);
    }

    private void saveModelingSubmissionAndAssessment() throws Exception {
        modelingSubmission = ParticipationFactory.generateModelingSubmission(TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727.json"), true);
        modelingSubmission = modelingExerciseUtilService.addModelingTeamSubmission(modelingExercise, modelingSubmission, team);
        modelingAssessment = modelingExerciseUtilService.addModelingAssessmentForSubmission(modelingExercise, modelingSubmission,
                "test-data/model-assessment/assessment.54727.v2.json", TEST_PREFIX + "tutor1", true);
    }
}
