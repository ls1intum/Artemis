package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.util.TextExerciseUtilService;

public class TextAssessmentIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    RequestUtilService request;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    ComplaintRepository complaintRepo;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    private TextClusterRepository textClusterRepository;

    @Autowired
    private TextBlockRepository textBlockRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private TextSubmissionRepository textSubmissionRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    private TextExercise textExercise;

    private Course course;

    @BeforeEach
    public void initTestCase() throws Exception {
        database.addUsers(1, 2, 1);
        course = database.addCourseWithOneTextExercise();
        textExercise = (TextExercise) exerciseRepo.findAll().get(0);
        textExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exerciseRepo.save(textExercise);
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
        int submissionCount = 5;
        int submissionSize = 4;
        int[] clusterSizes = new int[] { 4, 5, 10, 1 };
        ArrayList<TextBlock> textBlocks = textExerciseUtilService.generateTextBlocks(submissionCount * submissionSize);
        TextExercise textExercise = textExerciseUtilService.createSampleTextExerciseWithSubmissions(course, textBlocks, submissionCount, submissionSize);
        textBlocks.forEach(TextBlock::computeId);
        List<TextCluster> clusters = textExerciseUtilService.addTextBlocksToCluster(textBlocks, clusterSizes, textExercise);
        textClusterRepository.saveAll(clusters);
        textBlockRepository.saveAll(textBlocks);

        StudentParticipation studentParticipation = (StudentParticipation) textSubmissionRepository.findAll().get(0).getParticipation();

        // connect it with the user
        User user = database.getUserByLogin("tutor1");
        studentParticipation.setInitializationDate(ZonedDateTime.now());
        studentParticipation.setStudent(user);
        studentParticipationRepository.save(studentParticipation);

        Participation participationWithoutAssessment = request.get("/api/exercise/" + textExercise.getId() + "/participation-without-assessment", HttpStatus.OK,
                Participation.class);

        Result result = request.get("/api/text-assessments/result/" + participationWithoutAssessment.getResults().iterator().next().getId() + "/with-textblocks", HttpStatus.OK,
                Result.class);

        assertThat(result).as("saved result found").isNotNull();
        assertThat(((StudentParticipation) result.getParticipation()).getStudent()).as("student of participation is hidden").isNull();
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
