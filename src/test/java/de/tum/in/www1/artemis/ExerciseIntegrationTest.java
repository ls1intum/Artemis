package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ResultService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.dto.StatsForInstructorDashboardDTO;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")
public class ExerciseIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    ResultService resultService;

    @Autowired
    RequestUtilService request;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    ExerciseRepository exerciseRepository;

    @Autowired
    CourseRepository courseRepository;

    @BeforeEach
    public void init() {
        database.addUsers(10, 1, 1);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void getStatsForTutorExerciseDashboardTest() throws Exception {
        database.addCourseWithOneTextExercise();
        long courseID = courseRepository.findAllActive().get(0).getId();
        Exercise exercise = exerciseRepository.findByCourseId(courseID).get(0);
        List<Submission> submissions = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            TextSubmission textSubmission = new TextSubmission();
            textSubmission.text("Text");
            textSubmission.submitted(true);
            textSubmission.submissionDate(ZonedDateTime.now());
            submissions.add(database.addSubmission(exercise, textSubmission, "student" + i));
            if (i % 3 == 0) {
                database.addResultToSubmission(textSubmission, AssessmentType.MANUAL, database.getUserByLogin("instructor1"));
            }
            else if (i % 4 == 0) {
                database.addResultToSubmission(textSubmission, AssessmentType.SEMI_AUTOMATIC, database.getUserByLogin("instructor1"));
            }
        }
        StatsForInstructorDashboardDTO statsForInstructorDashboardDTO = request.get("/api/exercises/" + exercise.getId() + "/stats-for-tutor-dashboard", HttpStatus.OK,
                StatsForInstructorDashboardDTO.class);
        assertThat(statsForInstructorDashboardDTO.getNumberOfSubmissions()).isEqualTo(6);
        assertThat(statsForInstructorDashboardDTO.getNumberOfAssessments()).isEqualTo(3);
        assertThat(statsForInstructorDashboardDTO.getNumberOfAutomaticAssistedAssessments()).isEqualTo(1);
    }
}
