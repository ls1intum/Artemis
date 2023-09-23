package de.tum.in.www1.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.modelingexercise.ModelingExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.team.TeamUtilService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.FileUtils;

class AssessmentTeamComplaintIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "assmentteamcomplaint"; // only lower case is supported

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private ResultRepository resultRepo;

    @Autowired
    private ComplaintRepository complaintRepo;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ComplaintResponseRepository complaintResponseRepo;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ComplaintUtilService complaintUtilService;

    private ModelingExercise modelingExercise;

    private ModelingSubmission modelingSubmission;

    private Result modelingAssessment;

    private Complaint complaint;

    private Complaint moreFeedbackRequest;

    private Course course;

    private Team team;

    private final String resourceUrl = "/api/complaints";

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 2, 0, 1);
        // Initialize with 3 max team complaints and 7 days max complaint due date
        course = modelingExerciseUtilService.addCourseWithOneModelingExercise();
        modelingExercise = (ModelingExercise) course.getExercises().iterator().next();
        modelingExercise.setMode(ExerciseMode.TEAM);
        modelingExercise = exerciseRepo.save(modelingExercise);
        team = teamUtilService.addTeamForExercise(modelingExercise, userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"), TEST_PREFIX);
        saveModelingSubmissionAndAssessment();
        complaint = new Complaint().result(modelingAssessment).complaintText("This is not fair").complaintType(ComplaintType.COMPLAINT);
        moreFeedbackRequest = new Complaint().result(modelingAssessment).complaintText("Please explain").complaintType(ComplaintType.MORE_FEEDBACK);
    }

    @Test
    @WithMockUser(username = "team1" + TEST_PREFIX + "1")
    void submitComplaintAboutModellingAssessment_complaintLimitNotReached() throws Exception {
        exerciseUtilService.updateExerciseDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(2));
        exerciseUtilService.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(1));

        // 2 complaints are allowed, the course is created with 3 max team complaints
        complaintUtilService.addTeamComplaints(team, modelingAssessment.getParticipation(), 2, ComplaintType.COMPLAINT);

        request.post(resourceUrl, complaint, HttpStatus.CREATED);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is saved").isPresent();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).orElseThrow();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is true").isTrue();
    }

    @Test
    @WithMockUser(username = "team1" + TEST_PREFIX + "1")
    void submitComplaintAboutModelingAssessment_complaintLimitReached() throws Exception {
        complaintUtilService.addTeamComplaints(team, modelingAssessment.getParticipation(), 3, ComplaintType.COMPLAINT);

        request.post(resourceUrl, complaint, HttpStatus.BAD_REQUEST);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is not saved").isNotPresent();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).orElseThrow();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is false").isFalse();
    }

    @Test
    @WithMockUser(username = "team1" + TEST_PREFIX + "1")
    void requestMoreFeedbackAboutModelingAssessment_noLimit() throws Exception {
        exerciseUtilService.updateExerciseDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(2));
        exerciseUtilService.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(1));
        complaintUtilService.addTeamComplaints(team, modelingAssessment.getParticipation(), 5, ComplaintType.MORE_FEEDBACK);

        request.post(resourceUrl, complaint, HttpStatus.CREATED);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is saved").isPresent();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).orElseThrow();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is true").isTrue();

        // Only one complaint is possible for exercise regardless of its type
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

        request.post(resourceUrl, complaint, HttpStatus.CREATED);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is saved").isPresent();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).orElseThrow();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is true").isTrue();
    }

    @Test
    @WithMockUser(username = "team1" + TEST_PREFIX + "1")
    void submitComplaintAboutModelingAssessment_assessmentTooOld() throws Exception {
        // 3 weeks is already past the due date
        exerciseUtilService.updateExerciseDueDate(modelingExercise.getId(), ZonedDateTime.now().minusWeeks(3));
        exerciseUtilService.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusWeeks(3));
        exerciseUtilService.updateResultCompletionDate(modelingAssessment.getId(), ZonedDateTime.now().minusWeeks(3));

        request.post(resourceUrl, complaint, HttpStatus.BAD_REQUEST);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is not saved").isNotPresent();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).orElseThrow();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is false").isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void submitComplaintResponse_rejectComplaint() throws Exception {
        complaint = complaintRepo.saveAndFlush(complaint);

        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor1", complaint);
        complaintResponse.getComplaint().setAccepted(false);
        complaintResponse.setResponseText("rejected");

        request.put("/api/complaint-responses/complaint/" + complaint.getId() + "/resolve", complaintResponse, HttpStatus.OK);
        assertThat(complaintResponse.getComplaint().getParticipant()).isNull();
        Complaint storedComplaint = complaintRepo.findByResultId(modelingAssessment.getId()).orElseThrow();
        assertThat(storedComplaint.isAccepted()).as("complaint is not accepted").isFalse();
        Result storedResult = resultRepo.findWithEagerSubmissionAndFeedbackAndAssessorByIdElseThrow(modelingAssessment.getId());
        participationUtilService.checkFeedbackCorrectlyStored(modelingAssessment.getFeedbacks(), storedResult.getFeedbacks(), FeedbackType.MANUAL);
        assertThat(storedResult.getSubmission()).isEqualTo(modelingAssessment.getSubmission());
        assertThat(storedResult.getAssessor()).isEqualTo(modelingAssessment.getAssessor());
        assertThat(storedResult.getParticipation()).isEqualTo(modelingAssessment.getParticipation());
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
        request.put("/api/complaint-responses/complaint/" + complaint.getId() + "/resolve", complaintResponse, HttpStatus.FORBIDDEN);
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

        AssessmentUpdate assessmentUpdate = new AssessmentUpdate().feedbacks(feedbacks).complaintResponse(complaintResponse);
        Result receivedResult = request.putWithResponseBody("/api/modeling-submissions/" + modelingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate,
                Result.class, HttpStatus.OK);

        assertThat(((StudentParticipation) receivedResult.getParticipation()).getStudent()).as("student is hidden in response").isEmpty();
        Complaint storedComplaint = complaintRepo.findByResultId(modelingAssessment.getId()).orElseThrow();
        assertThat(storedComplaint.isAccepted()).as("complaint is accepted").isTrue();
        Result resultOfComplaint = storedComplaint.getResult();
        // set dates to UTC and round to milliseconds for comparison
        resultOfComplaint.setCompletionDate(ZonedDateTime.ofInstant(resultOfComplaint.getCompletionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        modelingAssessment.setCompletionDate(ZonedDateTime.ofInstant(modelingAssessment.getCompletionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        assertThat(resultOfComplaint.getAssessor()).isEqualTo(modelingAssessment.getAssessor());
        assertThat(resultOfComplaint).isEqualTo(modelingAssessment);
        Submission submission = submissionRepository.findOneWithEagerResultAndFeedback(modelingAssessment.getSubmission().getId());
        assertThat(submission.getLatestResult()).isNotNull();
        assertThat(submission.getFirstResult()).isNotNull();
        participationUtilService.checkFeedbackCorrectlyStored(feedbacks, submission.getLatestResult().getFeedbacks(), FeedbackType.MANUAL);
        assertThat(submission.getFirstResult().getAssessor()).as("assessor is still the original one").isEqualTo(modelingAssessment.getAssessor());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void submitComplaintResponse_updateAssessment_asOtherTutor_forbidden() throws Exception {
        complaint = complaintRepo.save(complaint);

        List<Feedback> feedback = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(true);
        complaintResponse.setResponseText("accepted");

        AssessmentUpdate assessmentUpdate = new AssessmentUpdate().feedbacks(feedback).complaintResponse(complaintResponse);
        request.putWithResponseBody("/api/modeling-submissions/" + modelingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate, Result.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getComplaintByResultId_studentAndNotPartOfTeam_forbidden() throws Exception {
        complaint.setParticipant(team);
        complaintRepo.save(complaint);

        request.get("/api/complaints/submissions/" + modelingSubmission.getId(), HttpStatus.FORBIDDEN, Complaint.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getComplaintResponseByComplaintId_studentNotPartOfTeam_forbidden() throws Exception {
        complaint.setParticipant(team);
        complaintRepo.save(complaint);

        ComplaintResponse complaintResponse = new ComplaintResponse().complaint(complaint.accepted(false)).responseText("rejected")
                .reviewer(userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"));
        complaintResponseRepo.save(complaintResponse);

        request.get("/api/complaint-responses/complaint/" + complaint.getId(), HttpStatus.FORBIDDEN, ComplaintResponse.class);
    }

    private void saveModelingSubmissionAndAssessment() throws Exception {
        modelingSubmission = ParticipationFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json"), true);
        modelingSubmission = modelingExerciseUtilService.addModelingTeamSubmission(modelingExercise, modelingSubmission, team);
        modelingAssessment = modelingExerciseUtilService.addModelingAssessmentForSubmission(modelingExercise, modelingSubmission,
                "test-data/model-assessment/assessment.54727.v2.json", TEST_PREFIX + "tutor1", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getNumberOfAllowedTeamComplaintsInCourse() throws Exception {
        complaint.setParticipant(team);
        complaintRepo.save(complaint);
        Long nrOfAllowedComplaints = request.get("/api/courses/" + modelingExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/allowed-complaints?isTeamMode=true",
                HttpStatus.OK, Long.class);
        assertThat(nrOfAllowedComplaints.intValue()).isEqualTo(course.getMaxTeamComplaints());
    }

}
