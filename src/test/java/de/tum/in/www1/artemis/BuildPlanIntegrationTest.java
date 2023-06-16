package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.BuildPlan;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.repository.BuildPlanRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingTriggerService;
import de.tum.in.www1.artemis.user.UserUtilService;

class BuildPlanIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    private static final String TEST_PREFIX = "buildplanintegration";

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private BuildPlanRepository buildPlanRepository;

    @Autowired
    private ProgrammingTriggerService programmingTriggerService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        var course = courseUtilService.addEmptyCourse();

        programmingExercise = new ProgrammingExercise();
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        programmingExercise.setProjectType(ProjectType.MAVEN_MAVEN);
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExercise.setSequentialTestRuns(false);
        programmingExercise.setTestwiseCoverageEnabled(false);
        programmingExercise.setReleaseDate(null);
        course.addExercises(programmingExercise);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        programmingExerciseUtilService.addBuildPlanAndSecretToProgrammingExercise(programmingExercise, "dummy-build-plan");
    }

    private void testReadAccessForbidden() throws Exception {
        request.get("/api/programming-exercises/" + programmingExercise.getId() + "/build-plan/for-editor", HttpStatus.FORBIDDEN, BuildPlan.class);
    }

    private void testWriteAccessForbidden() throws Exception {
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
    void testPublicReadAccessWithSecret() throws Exception {
        final String buildPlan = request.get(
                "/api/public/programming-exercises/" + programmingExercise.getId() + "/build-plan?secret=" + programmingExercise.getBuildPlanAccessSecret(), HttpStatus.OK,
                String.class);
        assertThat(buildPlan).isNotEmpty();
    }

    @Test
    void testPublicReadAccessForbiddenWithoutSecret() throws Exception {
        final String response = request.get("/api/public/programming-exercises/" + programmingExercise.getId() + "/build-plan?secret=", HttpStatus.FORBIDDEN, String.class);
        assertThat(response).isNull();
    }

    @Test
    void testPublicReadAccessForbiddenWithWrongSecret() throws Exception {
        final String response = request.get("/api/public/programming-exercises/" + programmingExercise.getId() + "/build-plan?secret=randomWrongSecret", HttpStatus.FORBIDDEN,
                String.class);
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
