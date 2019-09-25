package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")
class ProgrammingExerciseIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    ProgrammingExercise programmingExercise;

    @BeforeEach
    void initTestCase() {
        database.addUsers(3, 2, 2);
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = programmingExerciseRepository.findAllWithEagerParticipations().get(0);
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void checkIfProgrammingExerciseIsReleased_IsReleasedAndHasResults() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(5L));
        programmingExerciseRepository.save(programmingExercise);
        StudentParticipation participation = database.addParticipationForExercise(programmingExercise, "student1");
        database.addResultToParticipation(participation);

        Boolean result = request.get("/api/programming-exercises/" + programmingExercise.getId() + "/is-released-and-has-results", HttpStatus.OK, Boolean.class);
        assertThat(result).isTrue();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void checkIfProgrammingExerciseIsReleased_IsNotReleasedAndHasResults() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().plusHours(5L));
        programmingExerciseRepository.save(programmingExercise);
        StudentParticipation participation = database.addParticipationForExercise(programmingExercise, "student1");
        database.addResultToParticipation(participation);

        Boolean result = request.get("/api/programming-exercises/" + programmingExercise.getId() + "/is-released-and-has-results", HttpStatus.OK, Boolean.class);
        assertThat(result).isFalse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void checkIfProgrammingExerciseIsReleased_IsReleasedAndHasNoResults() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(5L));
        programmingExerciseRepository.save(programmingExercise);

        Boolean result = request.get("/api/programming-exercises/" + programmingExercise.getId() + "/is-released-and-has-results", HttpStatus.OK, Boolean.class);
        assertThat(result).isFalse();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void checkIfProgrammingExerciseIsReleased_forbidden() throws Exception {
        request.get("/api/programming-exercises/" + programmingExercise.getId() + "/is-released-and-has-results", HttpStatus.FORBIDDEN, Boolean.class);
    }
}
