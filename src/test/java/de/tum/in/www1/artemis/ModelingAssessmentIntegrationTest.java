package de.tum.in.www1.artemis;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ModelingExercise;
import de.tum.in.www1.artemis.domain.ModelingSubmission;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.ModelingSubmissionService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.compass.assessment.ModelElementAssessment;
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

import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
public class ModelingAssessmentIntegrationTest {
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
    ModelingSubmissionService modelSubmissionService;
    @Autowired
    ParticipationService participationService;

    private Course course;
    private ModelingExercise exercise;

    private String model1;
    private List<ModelElementAssessment> assessment1;
    private ModelingSubmission submission1;

    @Before
    public void initTestCase() throws Exception {
        database.resetFileStorage();
        database.resetDatabase();
        database.addUsers(2, 1);
        database.addCourseWithModelingExercise();
        course = courseRepo.findAll().get(0);
        exercise = (ModelingExercise) exerciseRepo.findAll().get(0);
        model1 = database.loadFileFromResources("test-data/model-submission/model.54727.json");
        assessment1 =
            database.loadAssessmentFomRessources("test-data/model-assessment/assessment.54727.json");
        loadModelingSubmissions();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void manualAssessmentSubmission() throws Exception {
        request.put(
            "/api/modeling-assessments/exercise/"
                + exercise.getId()
                + "/result/"
                + submission1.getResult().getId()
                + "/submit",
            assessment1,
            HttpStatus.OK);
    }

    private void loadModelingSubmissions() throws Exception {
        submission1 = database.addModelingSubmissionForAssessment(exercise, model1, "student1");
        database.checkSubmissionCorrectlyStored(submission1.getId(), model1);
    }
}
