package de.tum.cit.aet.artemis.programming.service.connectors.jenkins.build_plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationJenkinsGitlabTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPlan;

class JenkinsPipelineScriptCreatorTest extends AbstractProgrammingIntegrationJenkinsGitlabTest {

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void init() {
        var course = courseUtilService.addEmptyCourse();

        programmingExercise = new ProgrammingExercise();
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        programmingExercise.setBuildConfig(new ProgrammingExerciseBuildConfig());
        programmingExercise.setProjectType(ProjectType.MAVEN_MAVEN);
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExercise.getBuildConfig().setSequentialTestRuns(false);
        programmingExercise.getBuildConfig().setTestwiseCoverageEnabled(false);
        programmingExercise.setReleaseDate(null);
        course.addExercises(programmingExercise);

        var savedBuildConfig = programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());
        programmingExercise.setBuildConfig(savedBuildConfig);
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
