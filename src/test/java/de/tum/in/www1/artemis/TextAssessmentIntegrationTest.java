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
import org.springframework.util.LinkedMultiValueMap;

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

public class TextAssessmentIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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
    ResultRepository resultRepo;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    private TextExercise textExercise;

    private Course course;

    @BeforeEach
    public void initTestCase() throws Exception {
        database.addUsers(2, 2, 1);
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
    public void retrieveParticipationForSubmission_studentHidden() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, false);
        textSubmission = database.addTextSubmission(textExercise, textSubmission, "student1");

        StudentParticipation participationWithoutAssessment = request.get("/api/text-assessments/submission/" + textSubmission.getId(), HttpStatus.OK, StudentParticipation.class);

        assertThat(participationWithoutAssessment).as("participation with submission was found").isNotNull();
        assertThat(participationWithoutAssessment.getSubmissions().iterator().next().getId()).as("participation with correct text submission was found")
                .isEqualTo(textSubmission.getId());
        assertThat(participationWithoutAssessment.getStudent()).as("student of participation is hidden").isEmpty();
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
        assertThat(((StudentParticipation) updatedResult.getParticipation()).getStudent()).as("student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void saveTextAssessment_studentHidden() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        database.addTextSubmission(textExercise, textSubmission, "student1");
        exerciseDueDatePassed();

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");

        TextSubmission submissionWithoutAssessment = request.get("/api/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class, params);

        Result result = request.putWithResponseBody("/api/text-assessments/exercise/" + textExercise.getId() + "/result/" + submissionWithoutAssessment.getResult().getId(),
                new ArrayList<String>(), Result.class, HttpStatus.OK);

        assertThat(result).as("saved result found").isNotNull();
        assertThat(((StudentParticipation) result.getParticipation()).getStudent()).as("student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void submitTextAssessment_studentHidden() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        database.addTextSubmission(textExercise, textSubmission, "student1");
        exerciseDueDatePassed();

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");

        TextSubmission submissionWithoutAssessment = request.get("/api/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class, params);
        Result result = request.putWithResponseBody(
                "/api/text-assessments/exercise/" + textExercise.getId() + "/result/" + submissionWithoutAssessment.getResult().getId() + "/submit", new ArrayList<String>(),
                Result.class, HttpStatus.OK);

        assertThat(result).as("saved result found").isNotNull();
        assertThat(((StudentParticipation) result.getParticipation()).getStudent()).as("student of participation is hidden").isEmpty();
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
        studentParticipation.setParticipant(user);
        studentParticipationRepository.save(studentParticipation);

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");

        TextSubmission submissionWithoutAssessment = request.get("/api/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class, params);

        Result result = request.get("/api/text-assessments/result/" + submissionWithoutAssessment.getResult().getId() + "/with-textblocks", HttpStatus.OK, Result.class);

        assertThat(result).as("saved result found").isNotNull();
        assertThat(((StudentParticipation) result.getParticipation()).getStudent()).as("student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getResultWithPredefinedTextblocksForNonTextExercise() throws Exception {
        FileUploadExercise fileUploadExercise = ModelFactory.generateFileUploadExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1),
                ZonedDateTime.now().plusDays(2), "png,pdf", textExercise.getCourse());
        exerciseRepo.save(fileUploadExercise);

        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        database.addFileUploadSubmissionWithResultAndAssessorFeedback(fileUploadExercise, fileUploadSubmission, "student1", "tutor1", new ArrayList<Feedback>());

        request.get("/api/exercises/" + fileUploadExercise.getId() + "/text-submission-without-assessment", HttpStatus.BAD_REQUEST, Participation.class);

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
    @WithMockUser(value = "student2", roles = "USER")
    public void getDataForTextEditor_asOtherStudent() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = database.addTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor1");
        request.get("/api/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.FORBIDDEN, Participation.class);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getDataForTextEditor_studentHidden() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = database.addTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor1");

        StudentParticipation participation = request.get("/api/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, StudentParticipation.class);

        assertThat(participation).as("participation found").isNotNull();
        assertThat(participation.getResults().iterator().next()).as("result found").isNotNull();
        assertThat(participation.getStudent()).as("student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getDataForTextEditor_submissionWithoutResult() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = database.addTextSubmission(textExercise, textSubmission, "student1");
        request.get("/api/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, StudentParticipation.class);
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
        getExampleResultForTutor(HttpStatus.NOT_FOUND, false);
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

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void cancelAssessment_wrongSubmissionId() throws Exception {
        request.put("/api/text-assessments/exercise/" + textExercise.getId() + "/submission/100/cancel-assessment", null, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void testOverrideAssessment_saveOtherTutorForbidden() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "false", true);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testOverrideAssessment_saveInstructorPossible() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "false", true);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testOverrideAssessment_saveSameTutorPossible() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "false", true);
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void testOverrideAssessment_submitOtherTutorForbidden() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "true", true);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testOverrideAssessment_submitInstructorPossible() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "true", true);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testOverrideAssessment_submitSameTutorPossible() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "true", true);
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void testOverrideAssessment_saveOtherTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "false", true);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testOverrideAssessment_saveInstructorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "false", true);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testOverrideAssessment_saveSameTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "false", true);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testOverrideAssessment_saveSameTutorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        // should be possible because the original result was not yet submitted
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "false", false);
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void testOverrideAssessment_submitOtherTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "true", true);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testOverrideAssessment_submitInstructorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "true", true);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testOverrideAssessment_submitSameTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "true", true);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testOverrideAssessment_submitSameTutorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        // should be possible because the original result was not yet submitted
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "true", false);
    }

    private void exerciseDueDatePassed() {
        database.updateExerciseDueDate(textExercise.getId(), ZonedDateTime.now().minusHours(2));
    }

    private void assessmentDueDatePassed() {
        database.updateAssessmentDueDate(textExercise.getId(), ZonedDateTime.now().minusSeconds(10));
    }

    private void overrideAssessment(String student, String originalAssessor, HttpStatus httpStatus, String submit, boolean originalAssessmentSubmitted) throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Test123", Language.ENGLISH, true);
        textSubmission = database.addTextSubmissionWithResultAndAssessor(textExercise, textSubmission, student, originalAssessor);
        textSubmission.getResult().setCompletionDate(originalAssessmentSubmitted ? ZonedDateTime.now() : null);
        resultRepo.save(textSubmission.getResult());
        var params = new LinkedMultiValueMap<String, String>();
        params.add("submit", submit);
        List<Feedback> feedbacks = ModelFactory.generateFeedback();
        var path = "/api/text-assessments/exercise/" + textExercise.getId() + "/result/" + textSubmission.getResult().getId();
        if (submit.equals("true")) {
            path = path + "/submit";
        }
        request.putWithResponseBodyAndParams(path, feedbacks, Result.class, httpStatus, params);
    }
}
