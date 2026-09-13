package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import tools.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.localci.dto.BuildPlanDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPlan;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanRequestDTO;

class BuildPlanIntegrationTest extends AbstractProgrammingIntegrationJenkinsLocalVCTest {

    private static final String TEST_PREFIX = "buildplanintegration";

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        var course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);

        programmingExercise = new ProgrammingExercise();
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        var buildConfig = new ProgrammingExerciseBuildConfig();
        programmingExercise.setProjectType(ProjectType.MAVEN_MAVEN);
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        buildConfig.setSequentialTestRuns(false);
        var savedBuildConfig = programmingExerciseBuildConfigRepository.save(buildConfig);

        programmingExercise.setBuildConfig(savedBuildConfig);
        programmingExercise.setReleaseDate(null);
        course.addExercises(programmingExercise);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        programmingExerciseUtilService.addBuildPlanAndSecretToProgrammingExercise(programmingExercise, "dummy-build-plan");
    }

    private void testReadAccessForbidden() throws Exception {
        request.get("/api/localci/programming-exercises/" + programmingExercise.getId() + "/build-plan/for-editor", HttpStatus.FORBIDDEN, BuildPlanDTO.class);
    }

    private void testWriteAccessForbidden() throws Exception {
        BuildPlanRequestDTO someOtherBuildPlan = new BuildPlanRequestDTO(null);
        request.put("/api/localci/programming-exercises/" + programmingExercise.getId() + "/build-plan", someOtherBuildPlan, HttpStatus.FORBIDDEN);
    }

    private void testReadAccess() throws Exception {
        programmingExercise.getBuildConfig().generateAndSetBuildPlanAccessSecret();
        programmingExercise.setBuildConfig(programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig()));

        request.get("/api/localci/programming-exercises/" + programmingExercise.getId() + "/build-plan/for-editor", HttpStatus.OK, BuildPlanDTO.class);
    }

    private void testWriteAccess() throws Exception {
        BuildPlanRequestDTO someOtherBuildPlan = new BuildPlanRequestDTO("Content");

        final BuildPlanDTO newBuildPlan = request.putWithResponseBody("/api/localci/programming-exercises/" + programmingExercise.getId() + "/build-plan", someOtherBuildPlan,
                BuildPlanDTO.class, HttpStatus.OK);
        final BuildPlan buildPlan = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercisesElseThrow(programmingExercise.getId());

        assertThat(newBuildPlan.buildPlan()).isEqualTo(someOtherBuildPlan.buildPlan());
        assertThat(buildPlan.getId()).isEqualTo(newBuildPlan.id());
        assertThat(buildPlan.getBuildPlan()).isEqualTo("Content");
    }

    @Test
    void testPublicReadAccessWithSecret() throws Exception {
        final String buildPlan = request.get("/api/localci/public/programming-exercises/" + programmingExercise.getId() + "/build-plan?secret="
                + programmingExercise.getBuildConfig().getBuildPlanAccessSecret(), HttpStatus.OK, String.class);
        assertThat(buildPlan).isNotEmpty();
    }

    @Test
    void testPublicReadAccessForbiddenWithoutSecret() throws Exception {
        final String response = request.get("/api/localci/public/programming-exercises/" + programmingExercise.getId() + "/build-plan?secret=", HttpStatus.FORBIDDEN, String.class);
        assertThat(response).isNull();
    }

    @Test
    void testPublicReadAccessForbiddenWithWrongSecret() throws Exception {
        final String response = request.get("/api/localci/public/programming-exercises/" + programmingExercise.getId() + "/build-plan?secret=randomWrongSecret",
                HttpStatus.FORBIDDEN, String.class);
        assertThat(response).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testReadAccessForbiddenForStudent() throws Exception {
        testReadAccessForbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testWriteAccessForbiddenForStudent() throws Exception {
        testWriteAccessForbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testReadAccessForbiddenForTutor() throws Exception {
        testReadAccessForbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testWriteAccessForbiddenForTutor() throws Exception {
        testWriteAccessForbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testReadAccessForEditor() throws Exception {
        testReadAccess();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testWriteAccessForEditor() throws Exception {
        testWriteAccess();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReadAccessForInstructor() throws Exception {
        testReadAccess();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testWriteAccessForInstructor() throws Exception {
        testWriteAccess();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetRequestResponse() throws Exception {
        BuildPlanDTO buildPlan = request.get("/api/localci/programming-exercises/" + programmingExercise.getId() + "/build-plan/for-editor", HttpStatus.OK, BuildPlanDTO.class);
        assertThat(buildPlan.id()).isNotNull();
        assertThat(buildPlan.buildPlan()).isEqualTo("dummy-build-plan");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testEditorEchoesLoadedBuildPlanBack() throws Exception {
        // The build plan editor loads the DTO and sends the same object back on save
        String url = "/api/localci/programming-exercises/" + programmingExercise.getId() + "/build-plan";
        String loaded = request.get(url + "/for-editor", HttpStatus.OK, String.class);
        assertThat(request.getObjectMapper().readTree(loaded).propertyNames()).containsExactlyInAnyOrder("id", "buildPlan");

        var echoed = (ObjectNode) request.getObjectMapper().readTree(loaded);
        echoed.put("buildPlan", "echoed content");
        BuildPlanDTO saved = request.putWithResponseBody(url, echoed, BuildPlanDTO.class, HttpStatus.OK);

        assertThat(saved.buildPlan()).isEqualTo("echoed content");
        assertThat(buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercisesElseThrow(programmingExercise.getId()).getBuildPlan()).isEqualTo("echoed content");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testBuildTrigger() throws Exception {
        BuildPlanRequestDTO someOtherBuildPlan = new BuildPlanRequestDTO("Content");

        request.put("/api/localci/programming-exercises/" + programmingExercise.getId() + "/build-plan", someOtherBuildPlan, HttpStatus.OK);
        verify(programmingTriggerService).triggerTemplateAndSolutionBuild(programmingExercise.getId());
    }
}
