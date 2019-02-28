package de.tum.in.www1.artemis;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.ModelingSubmissionService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.time.ZonedDateTime;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
public class ModellingAssessmentIntegrationTest {
  @Autowired CourseRepository courseRepo;
  @Autowired ExerciseRepository exerciseRepo;
  @Autowired UserRepository userRepo;
  @Autowired RequestUtilService request;
  @Autowired DatabaseUtilService database;
  @Autowired ModelingSubmissionService modelSubmissionService;
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
    database.addUsers(2, 1);

    course = courseRepo.findAll().get(0);
    exercise = exerciseRepo.findAll().get(0);
  }



}
