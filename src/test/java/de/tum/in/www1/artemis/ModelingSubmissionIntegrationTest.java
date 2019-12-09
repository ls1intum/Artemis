package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

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
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.util.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")
public class ModelingSubmissionIntegrationTest {

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    UserRepository userRepo;

    @Autowired
    RequestUtilService request;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    ParticipationService participationService;

    @Autowired
    ResultRepository resultRepo;

    @Autowired
    ModelingSubmissionRepository modelingSubmissionRepo;

    private ModelingExercise classExercise;

    private ModelingExercise activityExercise;

    private ModelingExercise objectExercise;

    private ModelingExercise useCaseExercise;

    private ModelingExercise afterDueDateExercise;

    private ModelingSubmission submittedSubmission;

    private ModelingSubmission unsubmittedSubmission;

    private StudentParticipation afterDueDateParticipation;

    private String emptyModel;

    private String validModel;

    @BeforeEach
    public void initTestCase() throws Exception {
        database.addUsers(3, 1, 1);
        database.addCourseWithDifferentModelingExercises();
        classExercise = (ModelingExercise) exerciseRepo.findAll().get(0);
        activityExercise = (ModelingExercise) exerciseRepo.findAll().get(1);
        objectExercise = (ModelingExercise) exerciseRepo.findAll().get(2);
        useCaseExercise = (ModelingExercise) exerciseRepo.findAll().get(3);
        afterDueDateExercise = (ModelingExercise) exerciseRepo.findAll().get(4);
        afterDueDateParticipation = database.addParticipationForExercise(afterDueDateExercise, "student3");
        database.addParticipationForExercise(classExercise, "student3");

        emptyModel = database.loadFileFromResources("test-data/model-submission/empty-class-diagram.json");
        validModel = database.loadFileFromResources("test-data/model-submission/model.54727.json");
        submittedSubmission = generateSubmittedSubmission();
        unsubmittedSubmission = generateUnsubmittedSubmission();
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "student1")
    public void saveAndSubmitModelingSubmission_classDiagram() throws Exception {
        database.addParticipationForExercise(classExercise, "student1");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(emptyModel, false);
        ModelingSubmission returnedSubmission = performInitialModelSubmission(classExercise.getId(), submission);
        database.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), emptyModel);
        checkDetailsHidden(returnedSubmission, true);

        submission = ModelFactory.generateModelingSubmission(validModel, true);
        returnedSubmission = performUpdateOnModelSubmission(classExercise.getId(), submission);
        database.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), validModel);
        checkDetailsHidden(returnedSubmission, true);
    }

    @Test
    @WithMockUser(value = "student1")
    public void saveAndSubmitModelingSubmission_activityDiagram() throws Exception {
        database.addParticipationForExercise(activityExercise, "student1");
        String emptyActivityModel = database.loadFileFromResources("test-data/model-submission/empty-activity-diagram.json");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(emptyActivityModel, false);
        ModelingSubmission returnedSubmission = performInitialModelSubmission(activityExercise.getId(), submission);
        database.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), emptyActivityModel);
        checkDetailsHidden(returnedSubmission, true);

        String validActivityModel = database.loadFileFromResources("test-data/model-submission/example-activity-diagram.json");
        submission = ModelFactory.generateModelingSubmission(validActivityModel, true);
        returnedSubmission = performUpdateOnModelSubmission(activityExercise.getId(), submission);
        database.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), validActivityModel);
        checkDetailsHidden(returnedSubmission, true);
    }

    @Test
    @WithMockUser(value = "student1")
    public void saveAndSubmitModelingSubmission_objectDiagram() throws Exception {
        database.addParticipationForExercise(objectExercise, "student1");
        String emptyObjectModel = database.loadFileFromResources("test-data/model-submission/empty-object-diagram.json");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(emptyObjectModel, false);
        ModelingSubmission returnedSubmission = performInitialModelSubmission(objectExercise.getId(), submission);
        database.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), emptyObjectModel);
        checkDetailsHidden(returnedSubmission, true);

        String validObjectModel = database.loadFileFromResources("test-data/model-submission/object-model.json");
        submission = ModelFactory.generateModelingSubmission(validObjectModel, true);
        returnedSubmission = performUpdateOnModelSubmission(objectExercise.getId(), submission);
        database.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), validObjectModel);
        checkDetailsHidden(returnedSubmission, true);
    }

    @Test
    @WithMockUser(value = "student1")
    public void saveAndSubmitModelingSubmission_useCaseDiagram() throws Exception {
        database.addParticipationForExercise(useCaseExercise, "student1");
        String emptyUseCaseModel = database.loadFileFromResources("test-data/model-submission/empty-use-case-diagram.json");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(emptyUseCaseModel, false);
        ModelingSubmission returnedSubmission = performInitialModelSubmission(useCaseExercise.getId(), submission);
        database.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), emptyUseCaseModel);
        checkDetailsHidden(returnedSubmission, true);

        String validUseCaseModel = database.loadFileFromResources("test-data/model-submission/use-case-model.json");
        submission = ModelFactory.generateModelingSubmission(validUseCaseModel, true);
        returnedSubmission = performUpdateOnModelSubmission(useCaseExercise.getId(), submission);
        database.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), validUseCaseModel);
        checkDetailsHidden(returnedSubmission, true);
    }

    @Test
    @WithMockUser(value = "student2")
    public void updateModelSubmissionAfterSubmit() throws Exception {
        database.addParticipationForExercise(classExercise, "student2");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(emptyModel, true);
        ModelingSubmission returnedSubmission = performInitialModelSubmission(classExercise.getId(), submission);
        database.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), emptyModel);

        submission = ModelFactory.generateModelingSubmission(validModel, false);
        request.putWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission, ModelingSubmission.class, HttpStatus.OK);

        database.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), emptyModel);
    }

    @Test
    @WithMockUser(value = "student1")
    public void injectResultOnSubmissionUpdate() throws Exception {
        User user = database.getUserByLogin("student1");
        database.addParticipationForExercise(classExercise, "student1");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, false);
        Result result = new Result();
        result.setScore(100L);
        result.setRated(true);
        result.setAssessor(user);
        submission.setResult(result);
        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission,
                ModelingSubmission.class);

        storedSubmission = modelingSubmissionRepo.findById(storedSubmission.getId()).get();
        assertThat(storedSubmission.getResult()).as("submission still unrated").isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getAllSubmissionsOfExercise() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmission(classExercise, submittedSubmission, "student1");
        ModelingSubmission submission2 = database.addModelingSubmission(classExercise, unsubmittedSubmission, "student2");

        List<ModelingSubmission> submissions = request.getList("/api/exercises/" + classExercise.getId() + "/modeling-submissions", HttpStatus.OK, ModelingSubmission.class);

        assertThat(submissions).as("contains both submissions").containsExactlyInAnyOrder(submission1, submission2);
    }

    @Test
    @WithMockUser(value = "student1")
    public void getAllSubmissionsOfExerciseAsStudent() throws Exception {
        database.addModelingSubmission(classExercise, submittedSubmission, "student1");
        database.addModelingSubmission(classExercise, unsubmittedSubmission, "student2");

        request.getList("/api/exercises/" + classExercise.getId() + "/modeling-submissions", HttpStatus.FORBIDDEN, ModelingSubmission.class);
        request.getList("/api/exercises/" + classExercise.getId() + "/modeling-submissions?submittedOnly=true", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getAllSubmittedSubmissionsOfExercise() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmission(classExercise, submittedSubmission, "student1");
        ModelingSubmission submission2 = database.addModelingSubmission(classExercise, unsubmittedSubmission, "student2");
        ModelingSubmission submission3 = database.addModelingSubmission(classExercise, generateSubmittedSubmission(), "student3");

        List<ModelingSubmission> submissions = request.getList("/api/exercises/" + classExercise.getId() + "/modeling-submissions?submittedOnly=true", HttpStatus.OK,
                ModelingSubmission.class);

        assertThat(submissions).as("contains only submitted submission").containsExactlyInAnyOrder(submission1, submission3);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getModelSubmission() throws Exception {
        User user = database.getUserByLogin("tutor1");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        submission = database.addModelingSubmission(classExercise, submission, "student1");

        ModelingSubmission storedSubmission = request.get("/api/modeling-submissions/" + submission.getId(), HttpStatus.OK, ModelingSubmission.class);

        assertThat(storedSubmission.getResult()).as("result has been set").isNotNull();
        assertThat(storedSubmission.getResult().getAssessor()).as("assessor is tutor1").isEqualTo(user);
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(value = "student1")
    public void getModelSubmissionAsStudent() throws Exception {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        submission = database.addModelingSubmission(classExercise, submission, "student1");

        request.get("/api/modeling-submissions/" + submission.getId(), HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getModelSubmission_lockLimitReached_success() throws Exception {
        User user = database.getUserByLogin("tutor1");
        createNineLockedSubmissionsForDifferentExercisesAndUsers("tutor1");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        submission = database.addModelingSubmissionWithResultAndAssessor(useCaseExercise, submission, "student1", "tutor1");

        ModelingSubmission storedSubmission = request.get("/api/modeling-submissions/" + submission.getId(), HttpStatus.OK, ModelingSubmission.class);

        assertThat(storedSubmission.getResult()).as("result has been set").isNotNull();
        assertThat(storedSubmission.getResult().getAssessor()).as("assessor is tutor1").isEqualTo(user);
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getModelSubmission_lockLimitReached_badRequest() throws Exception {
        createTenLockedSubmissionsForDifferentExercisesAndUsers("tutor1");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmission(useCaseExercise, submission, "student2");

        request.get("/api/modeling-submissions/" + submission.getId(), HttpStatus.BAD_REQUEST, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getModelSubmissionWithoutAssessment() throws Exception {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        submission = database.addModelingSubmission(classExercise, submission, "student1");
        database.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        ModelingSubmission storedSubmission = request.get("/api/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.OK,
                ModelingSubmission.class);

        // set dates to UTC and round to milliseconds for comparison
        submission.setSubmissionDate(ZonedDateTime.ofInstant(submission.getSubmissionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        storedSubmission.setSubmissionDate(ZonedDateTime.ofInstant(storedSubmission.getSubmissionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        assertThat(storedSubmission).as("submission was found").isEqualToIgnoringGivenFields(submission, "result");
        assertThat(storedSubmission.getResult()).as("result is not set").isNull();
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getModelSubmissionWithoutAssessment_lockSubmission() throws Exception {
        User user = database.getUserByLogin("tutor1");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        submission = database.addModelingSubmission(classExercise, submission, "student1");
        database.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        ModelingSubmission storedSubmission = request.get("/api/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment?lock=true", HttpStatus.OK,
                ModelingSubmission.class);

        // set dates to UTC and round to milliseconds for comparison
        submission.setSubmissionDate(ZonedDateTime.ofInstant(submission.getSubmissionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        storedSubmission.setSubmissionDate(ZonedDateTime.ofInstant(storedSubmission.getSubmissionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        assertThat(storedSubmission).as("submission was found").isEqualToIgnoringGivenFields(submission, "result");
        assertThat(storedSubmission.getResult()).as("result is set").isNotNull();
        assertThat(storedSubmission.getResult().getAssessor()).as("assessor is tutor1").isEqualTo(user);
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getModelSubmissionWithoutAssessment_noSubmittedSubmission_notFound() throws Exception {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, false);
        database.addModelingSubmission(classExercise, submission, "student1");
        database.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        request.get("/api/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.NOT_FOUND, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getModelSubmissionWithoutAssessment_noSubmissionWithoutAssessment_notFound() throws Exception {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(classExercise, submission, "student1", "tutor1");
        database.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        request.get("/api/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.NOT_FOUND, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getModelSubmissionWithoutAssessment_dueDateNotOver() throws Exception {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmission(classExercise, submission, "student1");

        request.get("/api/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.NOT_FOUND, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(value = "student1")
    public void getModelSubmissionWithoutAssessment_asStudent_forbidden() throws Exception {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmission(classExercise, submission, "student1");
        database.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        request.get("/api/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getModelSubmissionWithoutAssessment_testLockLimit() throws Exception {
        createNineLockedSubmissionsForDifferentExercisesAndUsers("tutor1");
        ModelingSubmission newSubmission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmission(useCaseExercise, newSubmission, "student1");
        database.updateExerciseDueDate(useCaseExercise.getId(), ZonedDateTime.now().minusHours(1));

        ModelingSubmission storedSubmission = request.get("/api/exercises/" + useCaseExercise.getId() + "/modeling-submission-without-assessment?lock=true", HttpStatus.OK,
                ModelingSubmission.class);
        assertThat(storedSubmission).as("submission was found").isNotNull();
        request.get("/api/exercises/" + useCaseExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.BAD_REQUEST, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getNextOptimalModelSubmission() throws Exception {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        submission = database.addModelingSubmission(classExercise, submission, "student1");

        List<Long> optimalSubmissionIds = request.getList("/api/exercises/" + classExercise.getId() + "/optimal-model-submissions", HttpStatus.OK, Long.class);

        assertThat(optimalSubmissionIds).as("optimal submission was found").containsExactly(submission.getId());
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getNextOptimalModelSubmission_lockLimitReached() throws Exception {
        createTenLockedSubmissionsForDifferentExercisesAndUsers("tutor1");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmission(useCaseExercise, submission, "student2");

        request.getList("/api/exercises/" + classExercise.getId() + "/optimal-model-submissions", HttpStatus.BAD_REQUEST, Long.class);
    }

    @Test
    @WithMockUser(value = "student1")
    public void getModelSubmissionForModelingEditor() throws Exception {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        submission = database.addModelingSubmissionWithFinishedResultAndAssessor(classExercise, submission, "student1", "tutor1");

        ModelingSubmission receivedSubmission = request.get("/api/modeling-editor/" + submission.getParticipation().getId(), HttpStatus.OK, ModelingSubmission.class);

        // set dates to UTC and round to milliseconds for comparison
        submission.setSubmissionDate(ZonedDateTime.ofInstant(submission.getSubmissionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        receivedSubmission.setSubmissionDate(ZonedDateTime.ofInstant(receivedSubmission.getSubmissionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        assertThat(receivedSubmission).as("submission was found").isEqualToIgnoringGivenFields(submission, "result");
        assertThat(receivedSubmission.getResult()).as("result is set").isNotNull();
        assertThat(receivedSubmission.getResult().getAssessor()).as("assessor is hidden").isNull();
    }

    @Test
    @WithMockUser(value = "student3", roles = "USER")
    public void submitExercise_afterDueDate_forbidden() throws Exception {
        afterDueDateParticipation.setInitializationDate(ZonedDateTime.now().minusDays(2));
        participationService.save(afterDueDateParticipation);
        request.put("/api/exercises/" + afterDueDateExercise.getId() + "/modeling-submissions", submittedSubmission, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "student3", roles = "USER")
    public void submitExercise_beforeDueDate_allowed() throws Exception {
        request.put("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submittedSubmission, HttpStatus.OK);
    }

    @Test
    @WithMockUser(value = "student3", roles = "USER")
    public void submitExercise_beforeDueDateSecondSubmission_allowed() throws Exception {
        submittedSubmission.setModel(validModel);
        submittedSubmission = request.putWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submittedSubmission, ModelingSubmission.class,
                HttpStatus.OK);

        final var submissionInDb = modelingSubmissionRepo.findById(submittedSubmission.getId());
        assertThat(submissionInDb.isPresent());
        assertThat(submissionInDb.get().getModel()).isEqualTo(validModel);
    }

    @Test
    @WithMockUser(value = "student3", roles = "USER")
    public void submitExercise_afterDueDateWithParticipationStartAfterDueDate_allowed() throws Exception {
        afterDueDateParticipation.setInitializationDate(ZonedDateTime.now());
        participationService.save(afterDueDateParticipation);

        request.put("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submittedSubmission, HttpStatus.OK);
    }

    private void checkDetailsHidden(ModelingSubmission submission, boolean isStudent) {
        assertThat(submission.getParticipation().getSubmissions()).isNullOrEmpty();
        assertThat(submission.getParticipation().getResults()).isNullOrEmpty();
        assertThat(((ModelingExercise) submission.getParticipation().getExercise()).getSampleSolutionModel()).isNullOrEmpty();
        assertThat(((ModelingExercise) submission.getParticipation().getExercise()).getSampleSolutionExplanation()).isNullOrEmpty();
        if (isStudent) {
            assertThat(submission.getResult()).isNull();
        }
    }

    private ModelingSubmission performInitialModelSubmission(Long exerciseId, ModelingSubmission submission) throws Exception {
        return request.postWithResponseBody("/api/exercises/" + exerciseId + "/modeling-submissions", submission, ModelingSubmission.class, HttpStatus.OK);
    }

    private ModelingSubmission performUpdateOnModelSubmission(Long exerciseId, ModelingSubmission submission) throws Exception {
        return request.putWithResponseBody("/api/exercises/" + exerciseId + "/modeling-submissions", submission, ModelingSubmission.class, HttpStatus.OK);
    }

    private ModelingSubmission generateSubmittedSubmission() {
        return ModelFactory.generateModelingSubmission(emptyModel, true);
    }

    private ModelingSubmission generateUnsubmittedSubmission() {
        return ModelFactory.generateModelingSubmission(emptyModel, false);
    }

    private void createNineLockedSubmissionsForDifferentExercisesAndUsers(String assessor) {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(classExercise, submission, "student1", assessor);
        submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(classExercise, submission, "student2", assessor);
        submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(classExercise, submission, "student3", assessor);
        submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(activityExercise, submission, "student1", assessor);
        submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(activityExercise, submission, "student2", assessor);
        submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(activityExercise, submission, "student3", assessor);
        submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(objectExercise, submission, "student1", assessor);
        submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(objectExercise, submission, "student2", assessor);
        submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(objectExercise, submission, "student3", assessor);
    }

    private void createTenLockedSubmissionsForDifferentExercisesAndUsers(String assessor) {
        createNineLockedSubmissionsForDifferentExercisesAndUsers(assessor);
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(useCaseExercise, submission, "student1", assessor);
    }
}
