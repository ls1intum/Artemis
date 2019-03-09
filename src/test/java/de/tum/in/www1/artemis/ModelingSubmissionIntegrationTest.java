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

    private Course course;
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
        course = courseRepo.findAll().get(0);
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
        ModelingSubmission submission = new ModelingSubmission(false, emptyModel);
        ModelingSubmission returnedSubmission =
            performInitialModelSubmission(course.getId(), exercise.getId(), submission);
        database.checkSubmissionCorrectlyStored(returnedSubmission.getId(), emptyModel);

        submission = new ModelingSubmission(false, validModel);
        returnedSubmission =
            performUpdateOnModelSubmission(course.getId(), exercise.getId(), submission);
        database.checkSubmissionCorrectlyStored(returnedSubmission.getId(), validModel);
    }


    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getAllSubmissionsOfExercise() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmission(exercise, submittedSubmission, "student1");
        ModelingSubmission submission2 = database.addModelingSubmission(exercise, unsubmittedSubmission, "student2");
        List<ModelingSubmission> submissions = request.getList("/api/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/modeling-submissions", HttpStatus.OK, ModelingSubmission.class);
        assertThat(submissions).as("contains both submissions").containsExactlyInAnyOrder(new ModelingSubmission[]{submission1, submission2});
    }


    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getAllSubmittedSubmissionsOfExercise() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmission(exercise, unsubmittedSubmission, "student1");
        ModelingSubmission submission2 = database.addModelingSubmission(exercise, submittedSubmission, "student1");
        ModelingSubmission submission3 = database.addModelingSubmission(exercise, generateSubmittedSubmission(), "student2");
        List<ModelingSubmission> submissions = request.getList("/api/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/modeling-submissions?submittedOnly=true", HttpStatus.OK, ModelingSubmission.class);
        assertThat(submissions).as("contains only submitted submission").containsExactlyInAnyOrder(new ModelingSubmission[]{submission1,submission3});
    }


    @Test
    @WithMockUser(value = "student2", roles = "USER")
    public void updateModelSubmissionAfterSubmit() throws Exception {
        database.addParticipationForExercise(exercise, "student2");
        ModelingSubmission submission = new ModelingSubmission(true, emptyModel);
        ModelingSubmission returnedSubmission =
            performInitialModelSubmission(course.getId(), exercise.getId(), submission);
        database.checkSubmissionCorrectlyStored(returnedSubmission.getId(), emptyModel);

        submission = new ModelingSubmission(true, validModel);
        try {
            returnedSubmission =
                performUpdateOnModelSubmission(course.getId(), exercise.getId(), submission);
            database.checkSubmissionCorrectlyStored(returnedSubmission.getId(), validModel);
            Fail.fail("update on submitted ModelingSubmission worked");
        } catch (Exception e) {
        }
    }


    private ModelingSubmission performInitialModelSubmission(
        Long courseId, Long exerciseId, ModelingSubmission submission) throws Exception {
        return request.postWithResponseBody(
            "/api/courses/" + courseId + "/exercises/" + exerciseId + "/modeling-submissions",
            submission,
            ModelingSubmission.class,
            HttpStatus.OK);
    }


    private ModelingSubmission performUpdateOnModelSubmission(
        Long courseId, Long exerciseId, ModelingSubmission submission) throws Exception {
        return request.putWithResponseBody(
            "/api/courses/" + courseId + "/exercises/" + exerciseId + "/modeling-submissions",
            submission,
            ModelingSubmission.class,
            HttpStatus.OK);
    }

    private ModelingSubmission generateSubmittedSubmission (){
      return  ModelFactory.generateModelingSubmission(emptyModel, true);
    }

    private ModelingSubmission generateUnsubmittedSubmission (){
        return  ModelFactory.generateModelingSubmission(emptyModel, true);
    }
}
