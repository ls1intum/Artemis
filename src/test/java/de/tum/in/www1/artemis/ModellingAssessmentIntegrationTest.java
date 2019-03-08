package de.tum.in.www1.artemis;

import java.util.List;
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
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.util.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
public class ModellingAssessmentIntegrationTest {
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

    private ModelingExercise exercise;

    @Before
    public void initTestCase() throws Exception {
        database.resetFileStorage();
        database.resetDatabase();
        database.addUsers(2, 1);
        database.addCourseWithModelingExercise();
        exercise = (ModelingExercise) exerciseRepo.findAll().get(0);
    }


    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void manualAssessmentSubmission() throws Exception {
        ModelingSubmission submission = database.addModelingSubmissionFromResources(exercise, "test-data/model-submission/model.54727.json", "student1");
        Result result = submission.getResult();
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        result.setFeedbacks(feedbacks);
        request.put(
            "/api/submissions/"
                + submission.getId()+"/modeling-assessment",
            result,
            HttpStatus.OK);
    }
}
