package de.tum.in.www1.artemis;

import java.util.List;
import org.assertj.core.api.Fail;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.util.*;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
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

    private ModelingExercise exercise;
    ModelingSubmission submittedSubmission;
    ModelingSubmission unsubmittedSubmission;
    String emptyModel;
    String validModel;


    @Before
    public void initTestCase() throws Exception {
        database.resetFileStorage();
        database.resetDatabase();
        database.addUsers(2, 1);
        database.addCourseWithModelingExercise();
        exercise = (ModelingExercise) exerciseRepo.findAll().get(0);
        emptyModel = database.loadFileFromResources("test-data/model-submission/empty-model.json");
        validModel = database.loadFileFromResources("test-data/model-submission/model.54727.json");
        submittedSubmission = generateSubmittedSubmission();
        unsubmittedSubmission = generateUnsubmittedSubmission();
    }


    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void modelingSubmissionOfStudent() throws Exception {
        database.addParticipationForExercise(exercise, "student1");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(emptyModel, false);
        ModelingSubmission returnedSubmission =
            performInitialModelSubmission(exercise.getId(), submission);
        database.checkSubmissionCorrectlyStored(returnedSubmission.getId(), emptyModel);
        checkDetailsHidden(returnedSubmission);

        submission = ModelFactory.generateModelingSubmission(validModel, true);
        returnedSubmission =
            performUpdateOnModelSubmission(exercise.getId(), submission);
        database.checkSubmissionCorrectlyStored(returnedSubmission.getId(), validModel);
        checkDetailsHidden(returnedSubmission);
    }


    @Test
    @WithMockUser(value = "student2", roles = "USER")
    public void updateModelSubmissionAfterSubmit() throws Exception {
        database.addParticipationForExercise(exercise, "student2");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(emptyModel, false);
        ModelingSubmission returnedSubmission =
            performInitialModelSubmission(exercise.getId(), submission);
        database.checkSubmissionCorrectlyStored(returnedSubmission.getId(), emptyModel);
        submission = ModelFactory.generateModelingSubmission(validModel, false);
        try {
            returnedSubmission = performUpdateOnModelSubmission(exercise.getId(), submission);
            database.checkSubmissionCorrectlyStored(returnedSubmission.getId(), validModel);
            checkDetailsHidden(returnedSubmission);
            Fail.fail("update on submitted ModelingSubmission worked");
        } catch (Exception e) {
        }
    }


    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void injectResultOnSubmissionUpdate() throws Exception {
        User user = userRepo.findOneByLogin("student1").get();
        database.addParticipationForExercise(exercise, "student1");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, false);
        Result result = new Result();
        result.setScore(100L);
        result.setRated(true);
        result.setAssessor(user);
        submission.setResult(result);
        ModelingSubmission storedSubmission = request.postWithResponseBody(
            "/api/exercises/" + exercise.getId() + "/modeling-submissions",
            submission,
            ModelingSubmission.class);
        storedSubmission = modelingSubmissionRepo.findById(storedSubmission.getId()).get();
        assertThat(storedSubmission.getResult()).as("submission still unrated").isNull();
    }


    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getAllSubmissionsOfExercise() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmission(exercise, submittedSubmission, "student1");
        ModelingSubmission submission2 = database.addModelingSubmission(exercise, unsubmittedSubmission, "student2");
        List<ModelingSubmission> submissions = request.getList("/api/exercises/" + exercise.getId() + "/modeling-submissions", HttpStatus.OK, ModelingSubmission.class);
        assertThat(submissions).as("contains both submissions").containsExactlyInAnyOrder(new ModelingSubmission[]{submission1, submission2});
    }


    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void getAllSubmissionsOfExerciseAsStudent() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmission(exercise, submittedSubmission, "student1");
        ModelingSubmission submission2 = database.addModelingSubmission(exercise, unsubmittedSubmission, "student2");
        request.getList("/api/exercises/" + exercise.getId() + "/modeling-submissions", HttpStatus.FORBIDDEN, ModelingSubmission.class);
        request.getList("/api/exercises/" + exercise.getId() + "/modeling-submissions?submittedOnly=true", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }


    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getAllSubmittedSubmissionsOfExercise() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmission(exercise, unsubmittedSubmission, "student1");
        ModelingSubmission submission2 = database.addModelingSubmission(exercise, submittedSubmission, "student1");
        ModelingSubmission submission3 = database.addModelingSubmission(exercise, generateSubmittedSubmission(), "student2");
        List<ModelingSubmission> submissions = request.getList("/api/exercises/" + exercise.getId() + "/modeling-submissions?submittedOnly=true", HttpStatus.OK, ModelingSubmission.class);
        assertThat(submissions).as("contains only submitted submission").containsExactlyInAnyOrder(new ModelingSubmission[]{submission1, submission3});
    }


    @Test
    @WithMockUser(value = "tutor1")
    public void getModelSubmission() throws Exception {
        User user = userRepo.findOneByLogin("tutor1").get();
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        submission = database.addModelingSubmission(exercise, submission, "student1");
        ModelingSubmission storedSubmission = request.get("/api/modeling-submissions/" + submission.getId(), HttpStatus.OK, ModelingSubmission.class);
        assertThat(storedSubmission.getResult()).as("result has been set").isNotNull();
        assertThat(storedSubmission.getResult().getAssessor()).as("assessor is tutor1").isEqualTo(user);
        checkDetailsHidden(storedSubmission);
    }

    @Test
    @WithMockUser(value = "student1")
    public void getModelSubmissionAsStudent() throws Exception {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        submission = database.addModelingSubmission(exercise, submission, "student1");
        request.get("/api/modeling-submissions/" + submission.getId(), HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }


    private void checkDetailsHidden(ModelingSubmission submission) {
        assertThat(submission.getParticipation().getSubmissions()).isNullOrEmpty();
        assertThat(submission.getParticipation().getResults()).isNullOrEmpty();
        assertThat(((ModelingExercise) submission.getParticipation().getExercise()).getSampleSolutionModel()).isNullOrEmpty();
        assertThat(((ModelingExercise) submission.getParticipation().getExercise()).getSampleSolutionExplanation()).isNullOrEmpty();
    }


    private ModelingSubmission performInitialModelSubmission(
        Long exerciseId, ModelingSubmission submission) throws Exception {
        return request.postWithResponseBody(
            "/api/exercises/" + exerciseId + "/modeling-submissions",
            submission,
            ModelingSubmission.class,
            HttpStatus.OK);
    }


    private ModelingSubmission performUpdateOnModelSubmission(
        Long exerciseId, ModelingSubmission submission) throws Exception {
        return request.putWithResponseBody(
            "/api/exercises/" + exerciseId + "/modeling-submissions",
            submission,
            ModelingSubmission.class,
            HttpStatus.OK);
    }


    private ModelingSubmission generateSubmittedSubmission() {
        return ModelFactory.generateModelingSubmission(emptyModel, true);
    }


    private ModelingSubmission generateUnsubmittedSubmission() {
        return ModelFactory.generateModelingSubmission(emptyModel, true);
    }
}
