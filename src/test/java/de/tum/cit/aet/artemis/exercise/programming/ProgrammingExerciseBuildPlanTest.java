package de.tum.cit.aet.artemis.exercise.programming;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.AbstractSpringIntegrationGitlabCIGitlabSamlTest;
import de.tum.cit.aet.artemis.exercise.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

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
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithBuildConfigElseThrow(programmingExerciseId);
        programmingExerciseUtilService.addBuildPlanAndSecretToProgrammingExercise(exercise, BUILD_PLAN);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("secret", exercise.getBuildConfig().getBuildPlanAccessSecret());

        String actualBuildPlan = request.get("/api/public/programming-exercises/" + exercise.getId() + "/build-plan", HttpStatus.OK, String.class, params);

        assertThat(actualBuildPlan).isEqualTo(BUILD_PLAN);
    }

    @Test
    void testGetBuildPlanInvalidSecret() throws Exception {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithBuildConfigElseThrow(programmingExerciseId);
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
