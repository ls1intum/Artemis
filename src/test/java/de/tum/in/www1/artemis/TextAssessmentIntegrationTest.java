package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class TextAssessmentIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    RequestUtilService request;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    ComplaintRepository complaintRepo;

    @Autowired
    ResultRepository resultRepo;

    @Autowired
    ObjectMapper mapper;

    private TextExercise textExercise;

    @BeforeEach
    public void initTestCase() throws Exception {
        database.addUsers(1, 2, 1);
        database.addCourseWithOneTextExercise();
        textExercise = (TextExercise) exerciseRepo.findAll().get(0);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getParticipationForTextExerciseWithoutAssessment_studentHidden() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("example text", Language.ENGLISH, true);
        textSubmission = database.addTextSubmission(textExercise, textSubmission, "student1");

        StudentParticipation participationWithoutAssessment = request.get("/api/exercise/" + textExercise.getId() + "/participation-without-assessment", HttpStatus.OK,
                StudentParticipation.class);

        assertThat(participationWithoutAssessment).as("participation without assessment was found").isNotNull();
        assertThat(participationWithoutAssessment.getSubmissions().iterator().next().getId()).as("participation with correct text submission was found")
                .isEqualTo(textSubmission.getId());
        assertThat(participationWithoutAssessment.getStudent()).as("student of participation is hidden").isNull();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void retrieveParticipationForSubmission_studentHidden() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, false);
        textSubmission = database.addTextSubmission(textExercise, textSubmission, "student1");

        StudentParticipation participationWithoutAssessment = request.get("/api/text-assessments/submission/" + textSubmission.getId(), HttpStatus.OK, StudentParticipation.class);

        assertThat(participationWithoutAssessment).as("participation with submission was found").isNotNull();
        assertThat(participationWithoutAssessment.getSubmissions().iterator().next().getId()).as("participation with correct text submission was found")
                .isEqualTo(textSubmission.getId());
        assertThat(participationWithoutAssessment.getStudent()).as("student of participation is hidden").isNull();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void retrieveParticipationForLockedSubmission() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, false);
        textSubmission = database.addTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor2");
        Result result = textSubmission.getResult();
        result.setCompletionDate(null); // assessment is still in progress for this test
        resultRepo.save(result);
        StudentParticipation participation = request.get("/api/text-assessments/submission/" + textSubmission.getId(), HttpStatus.BAD_REQUEST, StudentParticipation.class);
        assertThat(participation).as("participation is locked and should not be returned").isNull();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void retrieveParticipationForNonExistingSubmission() throws Exception {
        StudentParticipation participation = request.get("/api/text-assessments/submission/345395769256365", HttpStatus.BAD_REQUEST, StudentParticipation.class);
        assertThat(participation).as("participation should not be found").isNull();
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void updateTextAssessmentAfterComplaint_studentHidden() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = database.addTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor1");
        Result textAssessment = textSubmission.getResult();
        Complaint complaint = new Complaint().result(textAssessment).complaintText("This is not fair");

        complaintRepo.save(complaint);
        complaint.getResult().setParticipation(null); // Break infinite reference chain

        ComplaintResponse complaintResponse = new ComplaintResponse().complaint(complaint.accepted(false)).responseText("rejected");
        AssessmentUpdate assessmentUpdate = new AssessmentUpdate().feedbacks(new ArrayList<>()).complaintResponse(complaintResponse);

        Result updatedResult = request.putWithResponseBody("/api/text-assessments/text-submissions/" + textSubmission.getId() + "/assessment-after-complaint", assessmentUpdate,
                Result.class, HttpStatus.OK);

        assertThat(updatedResult).as("updated result found").isNotNull();
        assertThat(((StudentParticipation) updatedResult.getParticipation()).getStudent()).as("student of participation is hidden").isNull();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void saveTextAssessment_studentHidden() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        database.addTextSubmission(textExercise, textSubmission, "student1");

        Participation participationWithoutAssessment = request.get("/api/exercise/" + textExercise.getId() + "/participation-without-assessment", HttpStatus.OK,
                Participation.class);

        Result result = request.putWithResponseBody(
                "/api/text-assessments/exercise/" + textExercise.getId() + "/result/" + participationWithoutAssessment.getResults().iterator().next().getId(),
                new ArrayList<String>(), Result.class, HttpStatus.OK);

        assertThat(result).as("saved result found").isNotNull();
        assertThat(((StudentParticipation) result.getParticipation()).getStudent()).as("student of participation is hidden").isNull();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void submitTextAssessment_studentHidden() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        database.addTextSubmission(textExercise, textSubmission, "student1");

        Participation participationWithoutAssessment = request.get("/api/exercise/" + textExercise.getId() + "/participation-without-assessment", HttpStatus.OK,
                Participation.class);

        Result result = request.putWithResponseBody(
                "/api/text-assessments/exercise/" + textExercise.getId() + "/result/" + participationWithoutAssessment.getResults().iterator().next().getId() + "/submit",
                new ArrayList<String>(), Result.class, HttpStatus.OK);

        assertThat(result).as("saved result found").isNotNull();
        assertThat(((StudentParticipation) result.getParticipation()).getStudent()).as("student of participation is hidden").isNull();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getResultWithPredefinedTextblocks_studentHidden() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        database.addTextSubmission(textExercise, textSubmission, "student1");

        Participation participationWithoutAssessment = request.get("/api/exercise/" + textExercise.getId() + "/participation-without-assessment", HttpStatus.OK,
                Participation.class);

        Result result = request.get("/api/text-assessments/result/" + participationWithoutAssessment.getResults().iterator().next().getId() + "/with-textblocks", HttpStatus.OK,
                Result.class);

        assertThat(result).as("saved result found").isNotNull();
        assertThat(((StudentParticipation) result.getParticipation()).getStudent()).as("student of participation is hidden").isNull();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getResultWithPredefinedTextblocksForNonTextExercise() throws Exception {
        FileUploadExercise fileUploadExercise = ModelFactory.generateFileUploadExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1),
                ZonedDateTime.now().plusDays(2), "png,pdf", textExercise.getCourse());
        exerciseRepo.save(fileUploadExercise);

        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        database.addFileUploadSubmissionWithResultAndAssessorFeedback(fileUploadExercise, fileUploadSubmission, "student1", "tutor1", new ArrayList<Feedback>());

        request.get("/api/exercise/" + fileUploadExercise.getId() + "/participation-without-assessment", HttpStatus.BAD_REQUEST, Participation.class);

        Result result = request.get("/api/text-assessments/result/" + fileUploadSubmission.getParticipation().getResults().iterator().next().getId() + "/with-textblocks",
                HttpStatus.BAD_REQUEST, Result.class);

        assertThat(result).as("no result should be returned when exercise is not a text exercise").isNull();
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void getDataForTextEditor_assessorHidden() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = database.addTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor1");

        Participation participation = request.get("/api/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, Participation.class);

        assertThat(participation).as("participation found").isNotNull();
        assertThat(participation.getResults().iterator().next()).as("result found").isNotNull();
        assertThat(participation.getResults().iterator().next().getAssessor()).as("assessor of participation is hidden").isNull();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getDataForTextEditor_studentHidden() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = database.addTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor1");

        StudentParticipation participation = request.get("/api/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, StudentParticipation.class);

        assertThat(participation).as("participation found").isNotNull();
        assertThat(participation.getResults().iterator().next()).as("result found").isNotNull();
        assertThat(participation.getStudent()).as("student of participation is hidden").isNull();
    }

    private void getExampleResultForTutor(HttpStatus expectedStatus, boolean isExample) throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission.setExampleSubmission(isExample);
        textSubmission = database.addTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "instructor1");

        Result result = request.get("/api/text-assessments/exercise/" + textExercise.getId() + "/submission/" + textSubmission.getId() + "/example-result", expectedStatus,
                Result.class);

        if (expectedStatus == HttpStatus.OK) {
            assertThat(result).as("result found").isNotNull();
            assertThat(result.getSubmission().getId()).as("result for correct submission").isEqualTo(textSubmission.getId());
        }
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void getExampleResultForTutorAsStudent() throws Exception {
        getExampleResultForTutor(HttpStatus.FORBIDDEN, true);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getExampleResultForTutorAsTutor() throws Exception {
        getExampleResultForTutor(HttpStatus.OK, true);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getExampleResultForNonExampleSubmissionAsTutor() throws Exception {
        getExampleResultForTutor(HttpStatus.FORBIDDEN, false);
    }

    private void cancelAssessment(HttpStatus expectedStatus) throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = database.addTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor1");
        database.addFeedbacksToResult(textSubmission.getResult());
        request.put("/api/text-assessments/exercise/" + textExercise.getId() + "/submission/" + textSubmission.getId() + "/cancel-assessment", null, expectedStatus);
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
