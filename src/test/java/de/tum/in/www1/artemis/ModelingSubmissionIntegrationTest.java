package de.tum.in.www1.artemis;

import java.io.IOException;
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
    private Exercise exercise;


    @Before
    public void initTestCase() throws IOException {
        database.resetFileStorage();
        database.resetDatabase();
        database.addUsers(2, 0);
        database.addCourseWithModelingExercise();
        course = courseRepo.findAll().get(0);
        exercise = exerciseRepo.findAll().get(0);
    }


    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void modelingSubmissionOfStudent() throws Exception {
        User user = userRepo.findOneByLogin("student1").get();
        database.addParticipationForExercise(exercise, "student1");
        String model = database.loadFileFromResources("test-data/model-submission/empty-model.json");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(false, model);
        ModelingSubmission returnedSubmission =
            performInitialModelSubmission(course.getId(), exercise.getId(), submission);
        database.checkSubmissionCorrectlyStored(
            user.getId(), exercise.getId(), returnedSubmission.getId(), model);

        model = database.loadFileFromResources("test-data/model-submission/model.54727.json");
        submission = ModelFactory.generateModelingSubmission(false, model);
        returnedSubmission =
            performUpdateOnModelSubmission(course.getId(), exercise.getId(), submission);
        database.checkSubmissionCorrectlyStored(
            user.getId(), exercise.getId(), returnedSubmission.getId(), model);
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


    @Test
    @WithMockUser(value = "student2", roles = "USER")
    public void updateModelSubmissionAfterSubmit() throws Exception {
        User user = userRepo.findOneByLogin("student2").get();
        database.addParticipationForExercise(exercise, "student2");
        String model = database.loadFileFromResources("test-data/model-submission/model.54727.json");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(true, model);
        ModelingSubmission returnedSubmission =
            performInitialModelSubmission(course.getId(), exercise.getId(), submission);
        database.checkSubmissionCorrectlyStored(
            user.getId(), exercise.getId(), returnedSubmission.getId(), model);

        model = database.loadFileFromResources("test-data/model-submission/empty-model.json");
        submission = ModelFactory.generateModelingSubmission(true, model);
        try {
            returnedSubmission =
                performUpdateOnModelSubmission(course.getId(), exercise.getId(), submission);
            database.checkSubmissionCorrectlyStored(
                user.getId(), exercise.getId(), returnedSubmission.getId(), model);
            Fail.fail("update on submitted ModelingSubmission worked");
        } catch (Exception e) {
        }
    }
}
