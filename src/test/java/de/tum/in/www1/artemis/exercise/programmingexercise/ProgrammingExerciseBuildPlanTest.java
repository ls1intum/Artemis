package de.tum.in.www1.artemis.exercise.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationGitlabCIGitlabSamlTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;

class ProgrammingExerciseBuildPlanTest extends AbstractSpringIntegrationGitlabCIGitlabSamlTest {

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    private static final String BUILD_PLAN = """
            image: ubuntu:20.04
            stages:
                - test
            test-job:
                stage: test
                script:
                    - echo "Test"
                        """;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    private Long programmingExerciseId;

    @BeforeEach
    void init() {
        // no users needed for this test
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExerciseId = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class).getId();
    }

    @Test
    void testGetBuildPlanSuccess() throws Exception {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);
        programmingExerciseUtilService.addBuildPlanAndSecretToProgrammingExercise(exercise, BUILD_PLAN);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("secret", exercise.getBuildPlanAccessSecret());

        String actualBuildPlan = request.get("/api/public/programming-exercises/" + exercise.getId() + "/build-plan", HttpStatus.OK, String.class, params);

        assertThat(actualBuildPlan).isEqualTo(BUILD_PLAN);
    }

    @Test
    void testGetBuildPlanInvalidSecret() throws Exception {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);
        programmingExerciseUtilService.addBuildPlanAndSecretToProgrammingExercise(exercise, BUILD_PLAN);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("secret", "invalid-secret");

        request.get("/api/public/programming-exercises/" + exercise.getId() + "/build-plan", HttpStatus.FORBIDDEN, String.class, params);
    }

    @Test
    void testGetBuildPlanInvalidExerciseId() throws Exception {
        request.get("/api/public/programming-exercises/" + -1 + "/build-plan", HttpStatus.BAD_REQUEST, String.class);
    }
}
