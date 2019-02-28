package de.tum.in.www1.artemis;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ModelingSubmission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.ModelingSubmissionService;
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
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
public class ModelingSubmissionIntegrationTest {
  @Autowired CourseRepository courseRepo;
  @Autowired ExerciseRepository exerciseRepo;
  @Autowired UserRepository userRepo;
  @Autowired RequestUtilService request;
  @Autowired DatabaseUtilService database;
  @Autowired ModelingSubmissionService modelSublissionService;
  @Autowired ParticipationService participationService;

  private ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);
  private ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(1);
  private ZonedDateTime futureFututreTimestamp = ZonedDateTime.now().plusDays(2);
  private Course course;
  private Exercise exercise;

  @Before
  public void initTestCase() throws IOException {
    database.resetFileStorage();
    database.resetDatabase();
    database.addUsers(2,0);
    database.addCourseWithModelingExercise();
    course = courseRepo.findAll().get(0);
    exercise = exerciseRepo.findAll().get(0);
  }

  @Test
  @WithMockUser(value = "student1", roles = "USER")
  public void modelingSubmissionOfStudent() throws Exception {
    User user = userRepo.findOneByLogin("student1").get();
    database.addParticipationForExercise(exercise, "student1");
    String model = database.loadFileFromResource("test-data/model-submission/empty-model.json");
    ModelingSubmission submission = new ModelingSubmission(false, model);
    ModelingSubmission returnedSubmission =
        performInitialModelSubmission(course.getId(), exercise.getId(), submission);
    checkSubmissionCorrectlyStored(
        user.getId(), exercise.getId(), returnedSubmission.getId(), model);

    model = database.loadFileFromResource("test-data/model-submission/model.54727.json");
    submission = new ModelingSubmission(false, model);
    returnedSubmission =
        performUpdateOnModelSubmission(course.getId(), exercise.getId(), submission);
    checkSubmissionCorrectlyStored(
        user.getId(), exercise.getId(), returnedSubmission.getId(), model);
  }

  @Test
  @WithMockUser(value = "student2", roles = "USER")
  public void updateModelSubmissionAfterSubmit() throws Exception {
    User user = userRepo.findOneByLogin("student2").get();
    database.addParticipationForExercise(exercise, "student2");
    String model = database.loadFileFromResource("test-data/model-submission/model.54727.json");
    ModelingSubmission submission = new ModelingSubmission(true, model);
    ModelingSubmission returnedSubmission =
        performInitialModelSubmission(course.getId(), exercise.getId(), submission);
    checkSubmissionCorrectlyStored(
        user.getId(), exercise.getId(), returnedSubmission.getId(), model);

    model = database.loadFileFromResource("test-data/model-submission/empty-model.json");
    submission = new ModelingSubmission(true, model);
    try {
      returnedSubmission =
          performUpdateOnModelSubmission(course.getId(), exercise.getId(), submission);
      checkSubmissionCorrectlyStored(
          user.getId(), exercise.getId(), returnedSubmission.getId(), model);
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

  private void checkSubmissionCorrectlyStored(
      Long studentId, Long exerciseId, Long submissionId, String sentModel) throws Exception {
    JsonObject storedModel = modelSublissionService.getModel(exerciseId, studentId, submissionId);
    JsonParser parser = new JsonParser();
    JsonObject sentModelObject = parser.parse(sentModel).getAsJsonObject();
    assertThat(storedModel).as("model correctly stored").isEqualTo(sentModelObject);
  }
}
