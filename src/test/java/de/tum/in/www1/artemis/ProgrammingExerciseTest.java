package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.ProblemStatementUpdate;

class ProgrammingExerciseTest extends AbstractSpringIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

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

    void updateProgrammingExercise(ProgrammingExercise programmingExercise, String newProblem, String newTitle) throws Exception {
        programmingExercise.setProblemStatement(newProblem);
        programmingExercise.setTitle(newTitle);
        when(continuousIntegrationService.buildPlanIdIsValid(programmingExercise.getTemplateBuildPlanId())).thenReturn(true);
        when(versionControlService.repositoryUrlIsValid(programmingExercise.getTemplateRepositoryUrlAsUrl())).thenReturn(true);
        when(continuousIntegrationService.buildPlanIdIsValid(programmingExercise.getSolutionBuildPlanId())).thenReturn(true);
        when(versionControlService.repositoryUrlIsValid(programmingExercise.getSolutionRepositoryUrlAsUrl())).thenReturn(true);

        ProgrammingExercise updatedProgrammingExercise = request.putWithResponseBody("/api/programming-exercises", programmingExercise, ProgrammingExercise.class, HttpStatus.OK);

        // The result from the put response should be updated with the new data.
        assertThat(updatedProgrammingExercise.getProblemStatement()).isEqualTo(newProblem);
        assertThat(updatedProgrammingExercise.getTitle()).isEqualTo(newTitle);

        SecurityUtils.setAuthorizationObject();
        // There should still be only 1 programming exercise.
        assertThat(programmingExerciseRepository.count()).isEqualTo(1);
        // The programming exercise in the db should also be updated.
        ProgrammingExercise fromDb = programmingExerciseRepository.findById(programmingExercise.getId()).get();
        assertThat(fromDb.getProblemStatement()).isEqualTo(newProblem);
        assertThat(fromDb.getTitle()).isEqualTo(newTitle);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExerciseOnce() throws Exception {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findById(programmingExerciseId).get();
        updateProgrammingExercise(programmingExercise, "new problem 1", "new title 1");
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExerciseTwice() throws Exception {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findById(programmingExerciseId).get();
        updateProgrammingExercise(programmingExercise, "new problem 1", "new title 1");
        updateProgrammingExercise(programmingExercise, "new problem 2", "new title 2");
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void updateProblemStatement() throws Exception {
        String newProblem = "a new problem statement";
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findById(programmingExerciseId).get();
        ProblemStatementUpdate problemStatementUpdate = new ProblemStatementUpdate();
        problemStatementUpdate.setExerciseId(programmingExerciseId);
        problemStatementUpdate.setProblemStatement(newProblem);
        ProgrammingExercise updatedProgrammingExercise = request.patchWithResponseBody("/api/programming-exercises-problem", problemStatementUpdate, ProgrammingExercise.class,
                HttpStatus.OK);

        assertThat(updatedProgrammingExercise.getProblemStatement()).isEqualTo(newProblem);

        SecurityUtils.setAuthorizationObject();
        ProgrammingExercise fromDb = programmingExerciseRepository.findById(programmingExerciseId).get();
        assertThat(fromDb.getProblemStatement()).isEqualTo(newProblem);
    }

}
