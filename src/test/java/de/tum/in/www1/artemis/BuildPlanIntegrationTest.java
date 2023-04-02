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
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testNoAccessForStudent() throws Exception {
        jenkinsPipelineScriptCreator.createBuildPlanForExercise(programmingExercise);
        request.get("/api/programming-exercises/" + programmingExercise.getId() + "/build-plan/for-editor", HttpStatus.FORBIDDEN, BuildPlan.class);
        BuildPlan someOtherBuildPlan = new BuildPlan();
        request.put("/api/programming-exercises/" + programmingExercise.getId() + "/build-plan", someOtherBuildPlan, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "TA")
    void testNoAccessForTutor() throws Exception {
        jenkinsPipelineScriptCreator.createBuildPlanForExercise(programmingExercise);
        request.get("/api/programming-exercises/" + programmingExercise.getId() + "/build-plan/for-editor", HttpStatus.FORBIDDEN, BuildPlan.class);
        BuildPlan someOtherBuildPlan = new BuildPlan();
        request.put("/api/programming-exercises/" + programmingExercise.getId() + "/build-plan", someOtherBuildPlan, HttpStatus.FORBIDDEN);
    }
}
