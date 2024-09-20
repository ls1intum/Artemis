package de.tum.cit.aet.artemis.programming.service.connectors.jenkins.build_plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPlan;
import de.tum.cit.aet.artemis.programming.repository.BuildPlanRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.service.jenkins.build_plan.JenkinsPipelineScriptCreator;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsGitlabTest;

class JenkinsPipelineScriptCreatorTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    private BuildPlanRepository buildPlanRepository;

    @Autowired
    private JenkinsPipelineScriptCreator jenkinsPipelineScriptCreator;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    @Autowired
    private CourseUtilService courseUtilService;

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
