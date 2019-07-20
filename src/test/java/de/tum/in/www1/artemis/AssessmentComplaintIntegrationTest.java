package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.AssessmentUpdate;
import de.tum.in.www1.artemis.domain.Complaint;
import de.tum.in.www1.artemis.domain.ComplaintResponse;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")
public class AssessmentComplaintIntegrationTest {

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    RequestUtilService request;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    ResultRepository resultRepo;

    @Autowired
    ComplaintRepository complaintRepo;

    @Autowired
    ObjectMapper mapper;

    private ModelingExercise modelingExercise;

    private ModelingSubmission modelingSubmission;

    private Result modelingAssessment;

    private Complaint complaint;

    @Before
    public void initTestCase() throws Exception {
        database.resetDatabase();
        database.addUsers(1, 2, 0);
        database.addCourseWithOneModelingExercise();
        modelingExercise = (ModelingExercise) exerciseRepo.findAll().get(0);
        saveModelingSubmissionAndAssessment();
        complaint = new Complaint().result(modelingAssessment).complaintText("This is not fair");
    }

    @Test
    @WithMockUser(username = "student1")
    public void submitComplaintAboutModelingAssessment() throws Exception {
        request.post("/api/complaints", complaint, HttpStatus.CREATED);

        Optional<Complaint> storedComplaint = complaintRepo.findByResult_Id(modelingAssessment.getId());
        assertThat(storedComplaint).as("complaint is saved").isPresent();
        assertThat(storedComplaint.get().getComplaintText()).as("complaint text got correctly saved").isEqualTo(complaint.getComplaintText());
        assertThat(storedComplaint.get().isAccepted()).as("accepted flag of complaint is not set").isNull();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).get();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is true").isTrue();
        Result resultBeforeComplaint = mapper.readValue(storedComplaint.get().getResultBeforeComplaint(), Result.class);
        // set date to UTC for comparison as the date saved in resultBeforeComplaint string is in UTC
        storedResult.setCompletionDate(ZonedDateTime.ofInstant(storedResult.getCompletionDate().toInstant(), ZoneId.of("UTC")));
        assertThat(resultBeforeComplaint).as("result before complaint is correctly stored").isEqualToIgnoringGivenFields(storedResult, "participation", "submission");
    }

    @Test
    @WithMockUser(username = "student1")
    public void submitComplaintAboutModelingAssessment_complaintLimitReached() throws Exception {
        database.addComplaints("student1", modelingAssessment.getParticipation(), 3);

        request.post("/api/complaints", complaint, HttpStatus.BAD_REQUEST);

        assertThat(complaintRepo.findByResult_Id(modelingAssessment.getId())).as("complaint is not saved").isNotPresent();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).get();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is false").isFalse();

    }

    @Test
    @WithMockUser(username = "student1")
    public void submitComplaintAboutModelingAssessment_assessmentTooOld() throws Exception {
        database.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(8));
        database.updateResultCompletionDate(modelingAssessment.getId(), ZonedDateTime.now().minusDays(8));

        request.post("/api/complaints", complaint, HttpStatus.BAD_REQUEST);

        assertThat(complaintRepo.findByResult_Id(modelingAssessment.getId())).as("complaint is not saved").isNotPresent();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).get();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is false").isFalse();
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    public void submitComplaintResponse_rejectComplaint() throws Exception {
        complaintRepo.save(complaint);

        ComplaintResponse complaintResponse = new ComplaintResponse().complaint(complaint.accepted(false)).responseText("rejected");
        request.post("/api/complaint-responses", complaintResponse, HttpStatus.CREATED);

        Complaint storedComplaint = complaintRepo.findByResult_Id(modelingAssessment.getId()).get();
        assertThat(storedComplaint.isAccepted()).as("complaint is not accepted").isFalse();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacks(modelingAssessment.getId()).get();
        checkFeedbackCorrectlyStored(modelingAssessment.getFeedbacks(), storedResult.getFeedbacks());
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    public void submitComplaintResponse_updateAssessment() throws Exception {
        complaint = complaintRepo.save(complaint);

        List<Feedback> feedback = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        ComplaintResponse complaintResponse = new ComplaintResponse().complaint(complaint.accepted(true)).responseText("accepted");
        AssessmentUpdate assessmentUpdate = new AssessmentUpdate().feedbacks(feedback).complaintResponse(complaintResponse);
        request.postWithResponseBody("/api/modeling-submissions/" + modelingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate, Result.class, HttpStatus.OK);

        Complaint storedComplaint = complaintRepo.findByResult_Id(modelingAssessment.getId()).get();
        assertThat(storedComplaint.isAccepted()).as("complaint is accepted").isTrue();
        Result resultBeforeComplaint = mapper.readValue(storedComplaint.getResultBeforeComplaint(), Result.class);
        // set completion date to UTC for comparison as the date saved in resultBeforeComplaint string is in UTC
        modelingAssessment.setCompletionDate(ZonedDateTime.ofInstant(modelingAssessment.getCompletionDate().toInstant(), ZoneId.of("UTC")));
        assertThat(resultBeforeComplaint).as("result before complaint is correctly stored").isEqualToIgnoringGivenFields(modelingAssessment, "participation", "submission");
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).get();
        checkFeedbackCorrectlyStored(feedback, storedResult.getFeedbacks());
        assertThat(storedResult.getAssessor()).as("assessor is still the original one").isEqualTo(modelingAssessment.getAssessor());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void submitComplaintResponse_asAssessor_forbidden() throws Exception {
        complaintRepo.save(complaint);

        ComplaintResponse complaintResponse = new ComplaintResponse().complaint(complaint.accepted(false)).responseText("rejected");
        request.post("/api/complaint-responses", complaintResponse, HttpStatus.FORBIDDEN);

        Complaint storedComplaint = complaintRepo.findByResult_Id(modelingAssessment.getId()).get();
        assertThat(storedComplaint.isAccepted()).as("accepted flag of complaint is not set").isNull();
    }

    @Test
    @WithMockUser(username = "student1")
    public void getComplaintByResultId_assessorHiddenForStudent() throws Exception {
        complaintRepo.save(complaint);

        Complaint receivedComplaint = request.get("/api/complaints/result/" + complaint.getResult().getId(), HttpStatus.OK, Complaint.class);

        assertThat(receivedComplaint.getResult().getAssessor()).as("assessor is not set").isNull();
    }

    private void checkFeedbackCorrectlyStored(List<Feedback> sentFeedback, List<Feedback> storedFeedback) {
        assertThat(sentFeedback.size()).as("contains the same amount of feedback").isEqualTo(storedFeedback.size());
        Result storedFeedbackResult = new Result();
        Result sentFeedbackResult = new Result();
        storedFeedbackResult.setFeedbacks(storedFeedback);
        sentFeedbackResult.setFeedbacks(sentFeedback);
        storedFeedbackResult.evaluateFeedback(20);
        sentFeedbackResult.evaluateFeedback(20);
        assertThat(storedFeedbackResult.getScore()).as("stored feedback evaluates to the same score as sent feedback").isEqualTo(sentFeedbackResult.getScore());
        storedFeedback.forEach(feedback -> {
            assertThat(feedback.getType()).as("type has been set to MANUAL").isEqualTo(FeedbackType.MANUAL);
        });
    }

    private void saveModelingSubmissionAndAssessment() throws Exception {
        modelingSubmission = ModelFactory.generateModelingSubmission(database.loadFileFromResources("test-data/model-submission/model.54727.json"), true);
        modelingSubmission = database.addModelingSubmission(modelingExercise, modelingSubmission, "student1");
        modelingAssessment = database.addModelingAssessmentForSubmission(modelingExercise, modelingSubmission, "test-data/model-assessment/assessment.54727.v2.json", "tutor1",
                true);
    }
}
