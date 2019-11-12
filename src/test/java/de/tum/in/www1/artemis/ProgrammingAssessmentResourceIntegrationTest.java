package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
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

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")
public class ProgrammingAssessmentResourceIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    ComplaintRepository complaintRepo;

    ProgrammingExercise programmingExercise;

    ProgrammingSubmission programmingSubmission;

    @BeforeEach
    void initTestCase() {
        database.addUsers(3, 2, 2);
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = programmingExerciseRepository.findAllWithEagerParticipations().get(0);
        programmingExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusDays(1));
        programmingExerciseRepository.save(programmingExercise);
        programmingSubmission = ModelFactory.generateProgrammingSubmission(true);
        programmingSubmission = database.addProgrammingSubmissionWithResultAndAssessor(programmingExercise, programmingSubmission, "student1", "tutor1");
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void updateTextAssessmentAfterComplaint_studentHidden() throws Exception {
        ProgrammingSubmission programmingSubmission = ModelFactory.generateProgrammingSubmission(true);
        programmingSubmission = database.addProgrammingSubmissionWithResultAndAssessor(programmingExercise, programmingSubmission, "student1", "tutor1");
        Result programmingAssessment = programmingSubmission.getResult();
        Complaint complaint = new Complaint().result(programmingAssessment).complaintText("This is not fair");

        complaintRepo.save(complaint);
        complaint.getResult().setParticipation(null); // Break infinite reference chain

        ComplaintResponse complaintResponse = new ComplaintResponse().complaint(complaint.accepted(false)).responseText("rejected");
        AssessmentUpdate assessmentUpdate = new AssessmentUpdate().feedbacks(new ArrayList<>()).complaintResponse(complaintResponse);

        Result updatedResult = request.putWithResponseBody("/api/programming-submissions/" + programmingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate,
                Result.class, HttpStatus.OK);

        assertThat(updatedResult).as("updated result found").isNotNull();
        assertThat(((StudentParticipation) updatedResult.getParticipation()).getStudent()).as("student of participation is hidden").isNull();
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void updateTextAssessmentAfterComplaint_automaticAssessment_forbidden() throws Exception {
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        programmingExerciseRepository.save(programmingExercise);
        Result programmingAssessment = programmingSubmission.getResult();
        Complaint complaint = new Complaint().result(programmingAssessment).complaintText("This is not fair");

        complaintRepo.save(complaint);
        complaint.getResult().setParticipation(null); // Break infinite reference chain

        ComplaintResponse complaintResponse = new ComplaintResponse().complaint(complaint.accepted(false)).responseText("rejected");
        AssessmentUpdate assessmentUpdate = new AssessmentUpdate().feedbacks(new ArrayList<>()).complaintResponse(complaintResponse);

        request.putWithResponseBody("/api/programming-submissions/" + programmingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate, Result.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void updateTextAssessmentAfterComplaint_dueDateNotPassed_forbidden() throws Exception {
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusDays(1));
        programmingExerciseRepository.save(programmingExercise);
        Result programmingAssessment = programmingSubmission.getResult();
        Complaint complaint = new Complaint().result(programmingAssessment).complaintText("This is not fair");

        complaintRepo.save(complaint);
        complaint.getResult().setParticipation(null); // Break infinite reference chain

        ComplaintResponse complaintResponse = new ComplaintResponse().complaint(complaint.accepted(false)).responseText("rejected");
        AssessmentUpdate assessmentUpdate = new AssessmentUpdate().feedbacks(new ArrayList<>()).complaintResponse(complaintResponse);

        request.putWithResponseBody("/api/programming-submissions/" + programmingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate, Result.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void updateTextAssessmentAfterComplaint_sameAsAssessor_forbidden() throws Exception {
        Result programmingAssessment = programmingSubmission.getResult();
        Complaint complaint = new Complaint().result(programmingAssessment).complaintText("This is not fair");

        complaintRepo.save(complaint);
        complaint.getResult().setParticipation(null); // Break infinite reference chain

        ComplaintResponse complaintResponse = new ComplaintResponse().complaint(complaint.accepted(false)).responseText("rejected");
        AssessmentUpdate assessmentUpdate = new AssessmentUpdate().feedbacks(new ArrayList<>()).complaintResponse(complaintResponse);

        request.putWithResponseBody("/api/programming-submissions/" + programmingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate, Result.class,
                HttpStatus.FORBIDDEN);
    }

}
