package de.tum.in.www1.artemis;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")
public class ProgrammingExerciseParticipationIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    ParticipationRepository participationRepository;

    ProgrammingExercise programmingExercise;

    Participation programmingExerciseParticipation;

    @Before
    public void initTestCase() {
        database.resetDatabase();
        database.addUsers(2, 2, 2);
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = programmingExerciseRepository.findAll().get(0);
        programmingExerciseParticipation = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        database.addResultToParticipation(programmingExerciseParticipation);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getParticipationWithLatestResultAsAStudent() throws Exception {
        StudentParticipation participation = (StudentParticipation) participationRepository.findAll().get(0);
        request.get("/api/programming-exercises-participation/" + participation.getId() + "/student-participation-with-latest-result-and-feedbacks", HttpStatus.OK,
                ProgrammingExerciseStudentParticipation.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getParticipationWithLatestResultAsAnInstructor() throws Exception {
        StudentParticipation participation = (StudentParticipation) participationRepository.findAll().get(0);
        request.get("/api/programming-exercises-participation/" + participation.getId() + "/student-participation-with-latest-result-and-feedbacks", HttpStatus.OK,
                ProgrammingExerciseStudentParticipation.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getLatestResultWithFeedbacksAsStudent() throws Exception {
        StudentParticipation participation = (StudentParticipation) participationRepository.findAll().get(0);
        request.get("/api/programming-exercises-participation/" + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.OK, Result.class);
    }
}
