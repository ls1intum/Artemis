package de.tum.in.www1.artemis.service.connectors.jenkins.build_plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.BuildPlan;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.repository.BuildPlanRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;

class JenkinsPipelineScriptCreatorTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    private BuildPlanRepository buildPlanRepository;

    @Autowired
    private JenkinsPipelineScriptCreator jenkinsPipelineScriptCreator;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private CourseUtilService courseUtilService;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void init() {
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
    }

    @Test
    void testBuildPlanCreation() {
        jenkinsPipelineScriptCreator.createBuildPlanForExercise(programmingExercise);
        Optional<BuildPlan> optionalBuildPlan = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercises(programmingExercise.getId());
        assertThat(optionalBuildPlan).isPresent();
    }

    @Test
    void testReplacements() {
        jenkinsPipelineScriptCreator.createBuildPlanForExercise(programmingExercise);
        BuildPlan buildPlan = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercises(programmingExercise.getId()).orElseThrow();
        assertThat(buildPlan.getBuildPlan()).doesNotContain("#isStaticCodeAnalysisEnabled", "#testWiseCoverage", "#dockerImage", "#dockerArgs")
                // testwise coverage is disabled in the dummy exercise
                .contains("isTestwiseCoverageEnabled = false && isSolutionBuild");
    }

    @Test
    void testBuildPlanRecreation() {
        jenkinsPipelineScriptCreator.createBuildPlanForExercise(programmingExercise);
        BuildPlan oldBuildPlan = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercises(programmingExercise.getId()).orElseThrow();
        assertThat(oldBuildPlan).isNotNull();
        assertThat(oldBuildPlan.getBuildPlan()).contains("isStaticCodeAnalysisEnabled = true");

        // change exercise attributes
        programmingExercise.setStaticCodeAnalysisEnabled(false);
        jenkinsPipelineScriptCreator.createBuildPlanForExercise(programmingExercise);

        BuildPlan newBuildPlan = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercises(programmingExercise.getId()).orElseThrow();
        assertThat(newBuildPlan.getBuildPlan()).contains("isStaticCodeAnalysisEnabled = false");
    }
}
