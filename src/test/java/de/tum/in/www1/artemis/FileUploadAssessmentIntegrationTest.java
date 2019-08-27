package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.FileUploadSubmissionService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")
public class FileUploadAssessmentIntegrationTest {

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

    private FileUploadSubmission fileUploadSubmission;

    private Result fileUploadAssessment;

    @BeforeEach
    public void initTestCase() throws Exception {
        database.addUsers(6, 2, 0);
        database.addCourseWithOneFileUploadExercise();
        fileUploadExercise = (FileUploadExercise) exerciseRepo.findAll().get(0);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void updateFileUploadAssessmentAfterComplaint_studentHidden() throws Exception {
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission("Some text", true);
        fileUploadSubmission = database.addFileUploadSubmissionWithResultAndAssessor(fileUploadExercise, fileUploadSubmission, "student1", "tutor1");
        Result fileUploadAssessment = fileUploadSubmission.getResult();
        Complaint complaint = new Complaint().result(fileUploadAssessment).complaintText("This is not fair");

        complaintRepo.save(complaint);
        complaint.getResult().setParticipation(null); // Break infinite reference chain

        ComplaintResponse complaintResponse = new ComplaintResponse().complaint(complaint.accepted(false)).responseText("rejected");
        AssessmentUpdate assessmentUpdate = new AssessmentUpdate().feedbacks(new ArrayList<>()).complaintResponse(complaintResponse);

        Result updatedResult = request.putWithResponseBody("/api/file-upload-submissions/" + fileUploadSubmission.getId() + "/assessment-after-complaint", assessmentUpdate,
                Result.class, HttpStatus.OK);

        assertThat(updatedResult).as("updated result found").isNotNull();
        assertThat(((StudentParticipation) updatedResult.getParticipation()).getStudent()).as("student of participation is hidden").isNull();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void saveFileUploadAssessment_studentHidden() throws Exception {
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission("example fileUpload", true);
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
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission("example fileUpload", true);
        fileUploadSubmission = database.addFileUploadSubmission(fileUploadExercise, fileUploadSubmission, "student1");

        var params = new LinkedMultiValueMap<String, String>();
        params.add("submit", "true");

        Result result = request.putWithResponseBodyAndParams("/api/file-upload-submissions/" + fileUploadSubmission.getId() + "/feedback", new ArrayList<String>(), Result.class,
                HttpStatus.OK, params);

        assertThat(result).as("submitted result found").isNotNull();
        assertThat(result.isRated()).isTrue();

        assertThat(((StudentParticipation) result.getParticipation()).getStudent()).as("student of participation is hidden").isNull();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void cancelAssessment() throws Exception {
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission("Some text", true);
        fileUploadSubmission = database.addFileUploadSubmissionWithResultAndAssessor(fileUploadExercise, fileUploadSubmission, "student1", "tutor1");
        request.put("/api/file-upload-submissions/" + fileUploadSubmission.getId() + "/cancel-assessment", null, HttpStatus.OK);
    }
}
