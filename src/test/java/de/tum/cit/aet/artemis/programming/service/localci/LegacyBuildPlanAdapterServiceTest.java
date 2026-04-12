package de.tum.cit.aet.artemis.programming.service.localci;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;

@ExtendWith(MockitoExtension.class)
class LegacyBuildPlanAdapterServiceTest {

    @Mock
    BuildPhasesTemplateService buildPhasesTemplateService;

    private LegacyBuildPlanAdapterService legacyBuildPlanAdapterService;

    @BeforeEach
    void setUp() {
        legacyBuildPlanAdapterService = new LegacyBuildPlanAdapterService(new ObjectMapper(), buildPhasesTemplateService);
    }

    @Test
    void extractLegacyDockerImage_shouldExtractDockerImage() {
        String legacyConfiguration = "{\"metadata\":{\"docker\":{\"image\":\"my/legacy-image:1.0\"}}}";

        final ProgrammingExercise programmingExercise = new ProgrammingExercise();
        programmingExercise.setBuildConfig(new ProgrammingExerciseBuildConfig());
        programmingExercise.getBuildConfig().setBuildPlanConfiguration(legacyConfiguration);

        String dockerImage = legacyBuildPlanAdapterService.extractLegacyDockerImage(programmingExercise);

        assertThat(dockerImage).isEqualTo("my/legacy-image:1.0");
    }

    @Test
    void extractLegacyDockerImage_shouldReturnEmptyForInvalidJson() {
        final ProgrammingExercise programmingExercise = new ProgrammingExercise();
        programmingExercise.setBuildConfig(new ProgrammingExerciseBuildConfig());
        programmingExercise.getBuildConfig().setBuildPlanConfiguration("non legacy");

        assertThat(legacyBuildPlanAdapterService.extractLegacyDockerImage(programmingExercise)).isNull();
    }

    @Test
    void createBuildPhasesFromLegacyBuildScript_shouldWrapScriptAndAssignResultPaths() {
        final ProgrammingExercise programmingExercise = new ProgrammingExercise();
        programmingExercise.setBuildConfig(new ProgrammingExerciseBuildConfig());
        programmingExercise.getBuildConfig().setBuildScript("echo hi");
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        programmingExercise.setProjectType(ProjectType.PLAIN_GRADLE);
        programmingExercise.setStaticCodeAnalysisEnabled(false);
        programmingExercise.getBuildConfig().setSequentialTestRuns(false);

        when(buildPhasesTemplateService.getDefaultBuildPlanPhasesFor(programmingExercise))
                .thenReturn(List.of(new BuildPhaseDTO("default", "dummy", BuildPhaseCondition.ALWAYS, false, List.of("build/test-results/test/*.xml"))));

        var phases = legacyBuildPlanAdapterService.createBuildPhasesFromLegacyBuildScript(programmingExercise);

        assertThat(phases).hasSize(1);
        assertThat(phases.getFirst().name()).isEqualTo("script");
        assertThat(phases.getFirst().resultPaths().size()).isEqualTo(1);
        assertThat(phases.getFirst().resultPaths()).containsExactly("build/test-results/test/*.xml");
        assertThat(phases.getFirst().script()).contains("cd /var/tmp/testing-dir\n");
        assertThat(phases.getFirst().script()).contains("cat << '__LEGACY_INNER_SCRIPT_END__' > \"${tmp_file}\"\n");
        assertThat(phases.getFirst().script()).contains("echo hi\n");
    }
}
