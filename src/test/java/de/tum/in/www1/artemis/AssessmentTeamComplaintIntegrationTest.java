package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.FileUtils;
import de.tum.in.www1.artemis.util.ModelFactory;

public class AssessmentTeamComplaintIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private ResultRepository resultRepo;

    @Autowired
    private ComplaintRepository complaintRepo;

    @Autowired
    SubmissionRepository submissionRepository;

    @Autowired
    private ComplaintResponseRepository complaintResponseRepo;

    @Autowired
    private ObjectMapper mapper;

    private ModelingExercise modelingExercise;

    private ModelingSubmission modelingSubmission;

    private Result modelingAssessment;

    private Complaint complaint;

    private Complaint moreFeedbackRequest;

    private Course course;

    private Team team;

    private final String resourceUrl = "/api/complaints";

    @BeforeEach
    public void initTestCase() throws Exception {
        database.addUsers(1, 2, 0, 1);
        // Initialize with 3 max team complaints and 7 days max complaint deadline
        course = database.addCourseWithOneModelingExercise();
        modelingExercise = (ModelingExercise) course.getExercises().iterator().next();
        modelingExercise = (ModelingExercise) exerciseRepo.save(modelingExercise.mode(ExerciseMode.TEAM));
        team = database.addTeamForExercise(modelingExercise, database.getUserByLogin("tutor1"));
        saveModelingSubmissionAndAssessment();
        complaint = new Complaint().result(modelingAssessment).complaintText("This is not fair").complaintType(ComplaintType.COMPLAINT);
        moreFeedbackRequest = new Complaint().result(modelingAssessment).complaintText("Please explain").complaintType(ComplaintType.MORE_FEEDBACK);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "team1student1")
    public void submitComplaintAboutModellingAssessment_complaintLimitNotReached() throws Exception {
        // 2 complaints are allowed, the course is created with 3 max team complaints
        database.addTeamComplaints(team, modelingAssessment.getParticipation(), 2, ComplaintType.COMPLAINT);

        request.post(resourceUrl, complaint, HttpStatus.CREATED);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is saved").isPresent();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).get();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is true").isTrue();
    }

    @Test
    @WithMockUser(username = "team1student1")
    public void submitComplaintAboutModelingAssessment_complaintLimitReached() throws Exception {
        database.addTeamComplaints(team, modelingAssessment.getParticipation(), 3, ComplaintType.COMPLAINT);

        request.post(resourceUrl, complaint, HttpStatus.BAD_REQUEST);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is not saved").isNotPresent();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).get();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is false").isFalse();
    }

    @Test
    @WithMockUser(username = "team1student1")
    public void requestMoreFeedbackAboutModelingAssessment_noLimit() throws Exception {
        database.addTeamComplaints(team, modelingAssessment.getParticipation(), 5, ComplaintType.MORE_FEEDBACK);

        request.post(resourceUrl, complaint, HttpStatus.CREATED);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is saved").isPresent();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).get();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is true").isTrue();

        // Only one complaint is possible for exercise regardless of its type
        request.post(resourceUrl, moreFeedbackRequest, HttpStatus.BAD_REQUEST);
        assertThat(complaintRepo.findByResultId(modelingAssessment.getId()).get().getComplaintType() == ComplaintType.MORE_FEEDBACK).as("more feedback request is not saved")
                .isFalse();
    }

    @Test
    @WithMockUser(username = "team1student1")
    public void submitComplaintAboutModelingAssessment_validDeadline() throws Exception {
        // Mock object initialized with 2 weeks deadline. One week after result date is fine.
        database.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusWeeks(1));
        database.updateResultCompletionDate(modelingAssessment.getId(), ZonedDateTime.now().minusWeeks(1));

        request.post(resourceUrl, complaint, HttpStatus.CREATED);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is saved").isPresent();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).get();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is true").isTrue();
    }

    @Test
    @WithMockUser(username = "team1student1")
    public void submitComplaintAboutModelingAssessment_assessmentTooOld() throws Exception {
        // 3 weeks is already past the deadline
        database.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusWeeks(3));
        database.updateResultCompletionDate(modelingAssessment.getId(), ZonedDateTime.now().minusWeeks(3));

        request.post(resourceUrl, complaint, HttpStatus.BAD_REQUEST);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is not saved").isNotPresent();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).get();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is false").isFalse();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void submitComplaintResponse_rejectComplaint() throws Exception {
        complaint = complaintRepo.saveAndFlush(complaint);

        ComplaintResponse complaintResponse = database.createInitialEmptyResponse("tutor1", complaint);
        complaintResponse.getComplaint().setAccepted(false);
        complaintResponse.setResponseText("rejected");

        request.put("/api/complaint-responses/complaint/" + complaint.getId() + "/resolve", complaintResponse, HttpStatus.OK);
        assertThat(complaintResponse.getComplaint().getParticipant()).isNull();
        Complaint storedComplaint = complaintRepo.findByResultId(modelingAssessment.getId()).get();
        assertThat(storedComplaint.isAccepted()).as("complaint is not accepted").isFalse();
        Result storedResult = resultRepo.findWithEagerSubmissionAndFeedbackAndAssessorById(modelingAssessment.getId()).get();
        database.checkFeedbackCorrectlyStored(modelingAssessment.getFeedbacks(), storedResult.getFeedbacks(), FeedbackType.MANUAL);
        assertThat(storedResult.getSubmission()).isEqualTo(modelingAssessment.getSubmission());
        assertThat(storedResult.getAssessor()).isEqualTo(modelingAssessment.getAssessor());
        assertThat(storedResult.getParticipation()).isEqualTo(modelingAssessment.getParticipation());
        assertThat(storedResult.getFeedbacks()).containsExactlyInAnyOrderElementsOf(modelingAssessment.getFeedbacks());
        final String[] ignoringFields = { "feedbacks", "submission", "participation", "assessor" };
        assertThat(storedResult).as("only feedbacks are changed in the result").usingRecursiveComparison().ignoringFields(ignoringFields).isEqualTo(modelingAssessment);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    public void submitComplaintResponse_rejectComplaint_asOtherTutor_forbidden() throws Exception {
        complaint = complaintRepo.save(complaint);
        ComplaintResponse complaintResponse = database.createInitialEmptyResponse("tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(false);
        complaintResponse.setResponseText("rejected");
        request.put("/api/complaint-responses/complaint/" + complaint.getId() + "/resolve", complaintResponse, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void submitComplaintResponse_updateAssessment() throws Exception {
        complaint = complaintRepo.save(complaint);

        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        feedbacks.forEach((feedback -> feedback.setType(FeedbackType.MANUAL)));
        ComplaintResponse complaintResponse = database.createInitialEmptyResponse("tutor1", complaint);
        complaintResponse.getComplaint().setAccepted(true);
        complaintResponse.setResponseText("accepted");

        AssessmentUpdate assessmentUpdate = new AssessmentUpdate().feedbacks(feedbacks).complaintResponse(complaintResponse);
        Result receivedResult = request.putWithResponseBody("/api/modeling-submissions/" + modelingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate,
                Result.class, HttpStatus.OK);

        assertThat(((StudentParticipation) receivedResult.getParticipation()).getStudent()).as("student is hidden in response").isEmpty();
        Complaint storedComplaint = complaintRepo.findByResultId(modelingAssessment.getId()).get();
        assertThat(storedComplaint.isAccepted()).as("complaint is accepted").isTrue();
        Result restultOfComplaint = storedComplaint.getResult();
        // set dates to UTC and round to milliseconds for comparison
        restultOfComplaint.setCompletionDate(ZonedDateTime.ofInstant(restultOfComplaint.getCompletionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        modelingAssessment.setCompletionDate(ZonedDateTime.ofInstant(modelingAssessment.getCompletionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        assertThat(restultOfComplaint.getAssessor()).isEqualTo(modelingAssessment.getAssessor());
        assertThat(restultOfComplaint).isEqualTo(modelingAssessment);
        String[] ignoringFields = { "participation", "submission", "feedbacks", "assessor" };
        Submission submission = submissionRepository.findOneWithEagerResultAndFeedback(modelingAssessment.getSubmission().getId());
        assertThat(submission.getLatestResult()).isNotNull();
        assertThat(submission.getFirstResult()).isNotNull();
        database.checkFeedbackCorrectlyStored(feedbacks, submission.getLatestResult().getFeedbacks(), FeedbackType.MANUAL);
        assertThat(submission.getFirstResult().getAssessor()).as("assessor is still the original one").isEqualTo(modelingAssessment.getAssessor());
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    public void submitComplaintResponse_updateAssessment_asOtherTutor_forbidden() throws Exception {
        complaint = complaintRepo.save(complaint);

        List<Feedback> feedback = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        ComplaintResponse complaintResponse = database.createInitialEmptyResponse("tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(true);
        complaintResponse.setResponseText("accepted");

        AssessmentUpdate assessmentUpdate = new AssessmentUpdate().feedbacks(feedback).complaintResponse(complaintResponse);
        request.putWithResponseBody("/api/modeling-submissions/" + modelingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate, Result.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "student1")
    public void getComplaintByResultId_studentAndNotPartOfTeam_forbidden() throws Exception {
        complaint.setParticipant(team);
        complaintRepo.save(complaint);

        request.get("/api/complaints/results/" + complaint.getResult().getId(), HttpStatus.FORBIDDEN, Complaint.class);
    }

    @Test
    @WithMockUser(username = "student1")
    public void getComplaintResponseByComplaintId_studentNotPartOfTeam_forbidden() throws Exception {
        complaint.setParticipant(team);
        complaintRepo.save(complaint);

        ComplaintResponse complaintResponse = new ComplaintResponse().complaint(complaint.accepted(false)).responseText("rejected").reviewer(database.getUserByLogin("tutor1"));
        complaintResponseRepo.save(complaintResponse);

        request.get("/api/complaint-responses/complaint/" + complaint.getId(), HttpStatus.FORBIDDEN, ComplaintResponse.class);
    }

    private void saveModelingSubmissionAndAssessment() throws Exception {
        modelingSubmission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json"), true);
        modelingSubmission = database.addModelingTeamSubmission(modelingExercise, modelingSubmission, team);
        modelingAssessment = database.addModelingAssessmentForSubmission(modelingExercise, modelingSubmission, "test-data/model-assessment/assessment.54727.v2.json", "tutor1",
                true);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getNumberOfAllowedTeamComplaintsInCourse() throws Exception {
        complaint.setParticipant(team);
        complaintRepo.save(complaint);
        Long nrOfAllowedComplaints = request.get("/api/courses/" + modelingExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/allowed-complaints?isTeamMode=true",
                HttpStatus.OK, Long.class);
        assertThat(nrOfAllowedComplaints.intValue()).isEqualTo(course.getMaxTeamComplaints());
    }

}
