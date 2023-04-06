package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.BuildPlan;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.repository.BuildPlanRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingTriggerService;

class BuildPlanIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    private static final String TEST_PREFIX = "buildplanintegration";

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private BuildPlanRepository buildPlanRepository;

    @Autowired
    private ProgrammingTriggerService programmingTriggerService;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void init() {
        database.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        var course = database.addEmptyCourse();

        programmingExercise = new ProgrammingExercise();
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        programmingExercise.setProjectType(ProjectType.MAVEN_MAVEN);
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExercise.setSequentialTestRuns(false);
        programmingExercise.setTestwiseCoverageEnabled(false);
        programmingExercise.setReleaseDate(null);
        course.addExercises(programmingExercise);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        database.addBuildPlanAndSecretToProgrammingExercise(programmingExercise, "dummy-build-plan");
    }

    private void testNoReadAccess() throws Exception {
        request.get("/api/programming-exercises/" + programmingExercise.getId() + "/build-plan/for-editor", HttpStatus.FORBIDDEN, BuildPlan.class);
    }

    private void testNoWriteAccess() throws Exception {
        BuildPlan someOtherBuildPlan = new BuildPlan();
        request.put("/api/programming-exercises/" + programmingExercise.getId() + "/build-plan", someOtherBuildPlan, HttpStatus.FORBIDDEN);
    }

    private void testReadAccess() throws Exception {
        programmingExercise.generateAndSetBuildPlanAccessSecret();
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        request.get("/api/programming-exercises/" + programmingExercise.getId() + "/build-plan/for-editor", HttpStatus.OK, BuildPlan.class);
    }

    private void testWriteAccess() throws Exception {
        BuildPlan someOtherBuildPlan = new BuildPlan();
        someOtherBuildPlan.setBuildPlan("Content");

        final BuildPlan newBuildPlan = request.putWithResponseBody("/api/programming-exercises/" + programmingExercise.getId() + "/build-plan", someOtherBuildPlan, BuildPlan.class,
                HttpStatus.OK);
        final BuildPlan buildPlan = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercisesElseThrow(programmingExercise.getId());

        assertThat(newBuildPlan.getBuildPlan()).isEqualTo(someOtherBuildPlan.getBuildPlan());
        assertThat(buildPlan.getId()).isEqualTo(newBuildPlan.getId());
    }

    @Test
    void testReadAccessWithSecret() throws Exception {
        final String buildPlan = request.get("/api/programming-exercises/" + programmingExercise.getId() + "/build-plan?secret=" + programmingExercise.getBuildPlanAccessSecret(),
                HttpStatus.OK, String.class);
        assertThat(buildPlan).isNotEmpty();
    }

    @Test
    void testReadAccessForbiddenWithoutSecret() throws Exception {
        final String response = request.get("/api/programming-exercises/" + programmingExercise.getId() + "/build-plan?secret=", HttpStatus.FORBIDDEN, String.class);
        assertThat(response).isNull();
    }

    @Test
    void testReadAccessForbiddenWithWrongSecret() throws Exception {
        final String response = request.get("/api/programming-exercises/" + programmingExercise.getId() + "/build-plan?secret=randomWrongSecret", HttpStatus.FORBIDDEN,
                String.class);
        assertThat(response).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testNoReadAccessForStudent() throws Exception {
        testNoReadAccess();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testNoWriteAccessForStudent() throws Exception {
        testNoWriteAccess();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testNoReadAccessForTutor() throws Exception {
        testNoReadAccess();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testNoWriteAccessForTutor() throws Exception {
        testNoWriteAccess();
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
    void testNoReadAccessWithWrongSecret() throws Exception {
        programmingExercise.generateAndSetBuildPlanAccessSecret();
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        String secret = programmingExercise.getBuildPlanAccessSecret();
        assertThat(secret).isNotNull();
        request.get("/api/programming-exercises/" + programmingExercise.getId() + "/build-plan?secret=" + secret.substring(0, secret.length() - 1), HttpStatus.FORBIDDEN,
                String.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetRequestResponse() throws Exception {
        BuildPlan buildPlan = request.get("/api/programming-exercises/" + programmingExercise.getId() + "/build-plan/for-editor", HttpStatus.OK, BuildPlan.class);
        assertThat(buildPlan.getId()).isNotNull();
        assertThat(buildPlan.getBuildPlan()).isNotNull();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testBuildTrigger() throws Exception {
        BuildPlan someOtherBuildPlan = new BuildPlan();
        someOtherBuildPlan.setBuildPlan("Content");

        request.put("/api/programming-exercises/" + programmingExercise.getId() + "/build-plan", someOtherBuildPlan, HttpStatus.OK);
        verify(programmingTriggerService).triggerTemplateAndSolutionBuild(programmingExercise.getId());
    }
}
