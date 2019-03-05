package de.tum.in.www1.artemis;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ModelingSubmission;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import org.assertj.core.api.Fail;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

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
        database.addParticipationForExercise(exercise, "student1");
        String model = database.loadFileFromResources("test-data/model-submission/empty-model.json");
        ModelingSubmission submission = new ModelingSubmission(false, model);
        ModelingSubmission returnedSubmission =
            performInitialModelSubmission(course.getId(), exercise.getId(), submission);
        database.checkSubmissionCorrectlyStored(returnedSubmission.getId(), model);

        model = database.loadFileFromResources("test-data/model-submission/model.54727.json");
        submission = new ModelingSubmission(false, model);
        returnedSubmission =
            performUpdateOnModelSubmission(course.getId(), exercise.getId(), submission);
        database.checkSubmissionCorrectlyStored(returnedSubmission.getId(), model);
    }

    @Test
    @WithMockUser(value = "student2", roles = "USER")
    public void updateModelSubmissionAfterSubmit() throws Exception {
        database.addParticipationForExercise(exercise, "student2");
        String model = database.loadFileFromResources("test-data/model-submission/model.54727.json");
        ModelingSubmission submission = new ModelingSubmission(true, model);
        ModelingSubmission returnedSubmission =
            performInitialModelSubmission(course.getId(), exercise.getId(), submission);
        database.checkSubmissionCorrectlyStored(returnedSubmission.getId(), model);

        model = database.loadFileFromResources("test-data/model-submission/empty-model.json");
        submission = new ModelingSubmission(true, model);
        try {
            returnedSubmission =
                performUpdateOnModelSubmission(course.getId(), exercise.getId(), submission);
            database.checkSubmissionCorrectlyStored(returnedSubmission.getId(), model);
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
}
