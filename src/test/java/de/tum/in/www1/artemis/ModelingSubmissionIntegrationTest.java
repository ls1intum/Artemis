package de.tum.in.www1.artemis;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ModelingSubmission;
import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
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
import org.springframework.util.ResourceUtils;

import java.net.URI;
import java.nio.file.Files;
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

  private ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);
  private ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(1);
  private ZonedDateTime futureFututreTimestamp = ZonedDateTime.now().plusDays(2);

  @Before
  public void initTestCase() {
    database.reset();
    database.addUsers();
    database.addCourseWithModelingExercise();
  }

  @Test
  @WithMockUser(value = "student1", roles = "USER")
  public void modelingSubmissionOfStudent() throws Exception {
    Course course = courseRepo.findAll().get(0);
    Exercise exercise = exerciseRepo.findAll().get(0);
    database.addParticipationForExercise(exercise, "student1");
    String model = loadModelFromResource("test-data/model-submission/empty-model.json");
    ModelingSubmission submission = new ModelingSubmission(false, model);
    URI submissionLocation =
        request.post(
            "/api/courses/"
                + course.getId()
                + "/exercises/"
                + exercise.getId()
                + "/modeling-submissions",
            submission,
            HttpStatus.OK);
    Participation storedParticipation =
        request.get(submissionLocation.toString(), HttpStatus.OK, Participation.class);
    assertThat(storedParticipation.findLatestModelingSubmission().getModel())
        .as("model correctly stored")
        .isEqualTo(model);

    model = loadModelFromResource("test-data/model-submission/model.54727.json");
  }

  public String loadModelFromResource(String path) throws Exception {
    java.io.File file = ResourceUtils.getFile("classpath:" + path);
    StringBuilder builder = new StringBuilder();
    Files.lines(file.toPath()).forEach(builder::append);
    assertThat(builder.toString()).as("model has been correctly read from file").isNotEqualTo("");
    return builder.toString();
  }
}
