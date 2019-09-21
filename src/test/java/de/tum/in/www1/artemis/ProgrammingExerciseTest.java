package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.connectors.BambooService;
import de.tum.in.www1.artemis.service.connectors.BitbucketService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis, bamboo")
class ProgrammingExerciseTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @MockBean
    BambooService continuousIntegrationService;

    @MockBean
    BitbucketService versionControlService;

    Long programmingExerciseId;

    @BeforeEach
    void init() {
        database.addUsers(2, 2, 2);
        database.addCourseWithOneProgrammingExercise();

        programmingExerciseId = programmingExerciseRepository.findAll().get(0).getId();
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    ProgrammingExercise executeUpdateRequest(ProgrammingExercise programmingExercise) throws Exception {
        return request.putWithResponseBody("/api/programming-exercises", programmingExercise, ProgrammingExercise.class, HttpStatus.OK);
    }

    @ParameterizedTest
    // Number of requests executed in the test.
    @ValueSource(ints = { 1, 2, 3, 5 })
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExercise(int numberOfUpdates) throws Exception {
        SecurityUtils.setAuthorizationObject();

        String newProblem = "new problem";
        String newTitle = "new Title";
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationById(programmingExerciseId).get();
        programmingExercise.setProblemStatement(newProblem);
        programmingExercise.setTitle(newTitle);
        when(continuousIntegrationService.buildPlanIdIsValid(programmingExercise.getTemplateBuildPlanId())).thenReturn(true);
        when(versionControlService.repositoryUrlIsValid(programmingExercise.getTemplateRepositoryUrlAsUrl())).thenReturn(true);
        when(continuousIntegrationService.buildPlanIdIsValid(programmingExercise.getSolutionBuildPlanId())).thenReturn(true);
        when(versionControlService.repositoryUrlIsValid(programmingExercise.getSolutionRepositoryUrlAsUrl())).thenReturn(true);

        ProgrammingExercise updatedProgrammingExercise;
        // We execute varying numbers of updates because we noticed different behaviors on successive requests.
        for (int i : Collections.nCopies(numberOfUpdates, 0)) {
            updatedProgrammingExercise = executeUpdateRequest(programmingExercise);

            // The result from the put response should be updated with the new data.
            assertThat(updatedProgrammingExercise.getProblemStatement()).isEqualTo(newProblem);
            assertThat(updatedProgrammingExercise.getTitle()).isEqualTo(newTitle);

            // There should still be only 1 programming exercise.
            assertThat(programmingExerciseRepository.count()).isEqualTo(1);
            // The programming exercise in the db should also be updated.
            ProgrammingExercise fromDb = programmingExerciseRepository.findById(programmingExercise.getId()).get();
            assertThat(fromDb.getProblemStatement()).isEqualTo(newProblem);
            assertThat(fromDb.getTitle()).isEqualTo(newTitle);
        }

    }
}
