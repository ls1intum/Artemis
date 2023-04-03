package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.BuildPlan;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.connectors.jenkins.build_plan.JenkinsPipelineScriptCreator;

public class BuildPlanIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    private static final String TEST_PREFIX = "buildplanintegration";

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private JenkinsPipelineScriptCreator jenkinsPipelineScriptCreator;

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

        jenkinsPipelineScriptCreator.createBuildPlanForExercise(programmingExercise);
        programmingExercise.generateAndSetBuildPlanAccessSecret();
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testNoReadAccessForStudent() throws Exception {
        request.get("/api/programming-exercises/" + programmingExercise.getId() + "/build-plan/for-editor", HttpStatus.FORBIDDEN, BuildPlan.class);
        request.get("/api/programming-exercises/" + programmingExercise.getId() + "/build-plan?secret=" + programmingExercise.getBuildPlanAccessSecret(), HttpStatus.FORBIDDEN,
                String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testNoWriteAccessForStudent() throws Exception {
        BuildPlan someOtherBuildPlan = new BuildPlan();
        request.put("/api/programming-exercises/" + programmingExercise.getId() + "/build-plan", someOtherBuildPlan, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testNoReadAccessForTutor() throws Exception {
        request.get("/api/programming-exercises/" + programmingExercise.getId() + "/build-plan/for-editor", HttpStatus.FORBIDDEN, BuildPlan.class);
        request.get("/api/programming-exercises/" + programmingExercise.getId() + "/build-plan?secret=" + programmingExercise.getBuildPlanAccessSecret(), HttpStatus.FORBIDDEN,
                String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testNoWriteAccessForTutor() throws Exception {
        BuildPlan someOtherBuildPlan = new BuildPlan();
        request.put("/api/programming-exercises/" + programmingExercise.getId() + "/build-plan", someOtherBuildPlan, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testReadAccessForEditor() throws Exception {
        BuildPlan buildPlanForEditor = request.get("/api/programming-exercises/" + programmingExercise.getId() + "/build-plan/for-editor", HttpStatus.OK, BuildPlan.class);
        String buildPlan = request.get("/api/programming-exercises/" + programmingExercise.getId() + "/build-plan?secret=" + programmingExercise.getBuildPlanAccessSecret(),
                HttpStatus.OK, String.class);

        assertThat(buildPlan).isEqualTo(buildPlanForEditor.getBuildPlan());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testWriteAccessForEditor() throws Exception {
        BuildPlan someOtherBuildPlan = new BuildPlan();
        someOtherBuildPlan.setBuildPlan("Content");
        // request.("/api/programming-exercises/" + programmingExercise.getId() + "/build-plan", someOtherBuildPlan, HttpStatus.OK);
        System.out.println(programmingExercise.getBuildPlan().getBuildPlan());
    }
}
