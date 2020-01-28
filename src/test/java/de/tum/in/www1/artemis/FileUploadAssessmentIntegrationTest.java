package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.FileUploadSubmissionService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class FileUploadAssessmentIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    ModelAssessmentConflictRepository conflictRepo;

    @Autowired
    UserRepository userRepo;

    @Autowired
    RequestUtilService request;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    FileUploadSubmissionService fileUploadSubmissionService;

    @Autowired
    FileUploadSubmissionRepository fileUploadSubmissionRepository;

    @Autowired
    ResultRepository resultRepo;

    @Autowired
    ParticipationService participationService;

    @Autowired
    ComplaintRepository complaintRepo;

    private FileUploadExercise fileUploadExercise;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(1, 2, 1);
        database.addCourseWithTwoFileUploadExercise();
        fileUploadExercise = (FileUploadExercise) exerciseRepo.findAll().get(0);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void updateFileUploadAssessmentAfterComplaint_studentHidden() throws Exception {
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = database.addFileUploadSubmissionWithResultAndAssessor(fileUploadExercise, fileUploadSubmission, "student1", "tutor1");
        Result fileUploadAssessment = fileUploadSubmission.getResult();
        Complaint complaint = new Complaint().result(fileUploadAssessment).complaintText("This is not fair");

        complaintRepo.save(complaint);
        complaint.getResult().setParticipation(null); // Break infinite reference chain

        ComplaintResponse complaintResponse = new ComplaintResponse().complaint(complaint.accepted(false)).responseText("rejected");
        List<Feedback> feedbacks = ModelFactory.generateFeedback();
        AssessmentUpdate assessmentUpdate = new AssessmentUpdate().feedbacks(feedbacks).complaintResponse(complaintResponse);

        Result updatedResult = request.putWithResponseBody("/api/file-upload-submissions/" + fileUploadSubmission.getId() + "/assessment-after-complaint", assessmentUpdate,
                Result.class, HttpStatus.OK);

        assertThat(updatedResult).as("updated result found").isNotNull();
        assertThat(((StudentParticipation) updatedResult.getParticipation()).getStudent()).as("student of participation is hidden").isNull();
        assertThat(updatedResult.getFeedbacks().size()).isEqualTo(2);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void saveFileUploadAssessment_studentHidden() throws Exception {
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = database.addFileUploadSubmission(fileUploadExercise, fileUploadSubmission, "student1");

        Result result = request.putWithResponseBody("/api/file-upload-submissions/" + fileUploadSubmission.getId() + "/feedback", new ArrayList<String>(), Result.class,
                HttpStatus.OK);

        assertThat(result).as("saved result found").isNotNull();
        assertThat(result.isRated()).isNull();
        assertThat(((StudentParticipation) result.getParticipation()).getStudent()).as("student of participation is hidden").isNull();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void submitFileUploadAssessment_studentHidden() throws Exception {
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = database.addFileUploadSubmission(fileUploadExercise, fileUploadSubmission, "student1");

        var params = new LinkedMultiValueMap<String, String>();
        params.add("submit", "true");
        List<Feedback> feedbacks = ModelFactory.generateFeedback();

        Result result = request.putWithResponseBodyAndParams("/api/file-upload-submissions/" + fileUploadSubmission.getId() + "/feedback", feedbacks, Result.class, HttpStatus.OK,
                params);

        assertThat(result).as("submitted result found").isNotNull();
        assertThat(result.isRated()).isTrue();

        assertThat(((StudentParticipation) result.getParticipation()).getStudent()).as("student of participation is hidden").isNull();
        assertThat(result.getFeedbacks().size()).isEqualTo(2);
        assertThat(result.getFeedbacks().get(0).getCredits()).isEqualTo(feedbacks.get(0).getCredits());
        assertThat(result.getFeedbacks().get(1).getCredits()).isEqualTo(feedbacks.get(1).getCredits());
    }

    private void cancelAssessment(HttpStatus expectedStatus) throws Exception {
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = database.addFileUploadSubmissionWithResultAndAssessor(fileUploadExercise, fileUploadSubmission, "student1", "tutor1");
        database.addFeedbacksToResult(fileUploadSubmission.getResult());
        request.put("/api/file-upload-submissions/" + fileUploadSubmission.getId() + "/cancel-assessment", null, expectedStatus);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void cancelOwnAssessmentAsStudent() throws Exception {
        cancelAssessment(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void cancelOwnAssessmentAsTutor() throws Exception {
        cancelAssessment(HttpStatus.OK);
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void cancelAssessmentOfOtherTutorAsTutor() throws Exception {
        cancelAssessment(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void cancelAssessmentOfOtherTutorAsInstructor() throws Exception {
        cancelAssessment(HttpStatus.OK);
    }
}
