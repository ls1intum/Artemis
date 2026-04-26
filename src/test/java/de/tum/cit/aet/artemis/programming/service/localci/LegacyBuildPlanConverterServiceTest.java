package de.tum.cit.aet.artemis.programming.service.localci;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;

class LegacyBuildPlanConverterServiceTest {

    private final LegacyBuildPlanConverterService legacyBuildPlanConverterService = new LegacyBuildPlanConverterService();

    @Test
    void adaptLegacyBuildPlanConfiguration_shouldConvertLegacyConfiguration() {
        final ProgrammingExercise programmingExercise = createExercise("""
                {
                    "metadata": {
                        "docker": {
                            "image": " my/legacy-image:1.0 "
                        }
                    },
                    "actions": [
                        {
                            "name": "compile",
                            "results": [
                                {
                                    "path": " build/test-results/test/*.xml "
                                }
                            ]
                        },
                        {
                            "name": "test",
                            "results": [
                                {
                                    "path": "coverage.xml"
                                }
                            ]
                        }
                    ]
                }
                """, "echo hi");

        var legacyData = legacyBuildPlanConverterService.convertLegacyBuildPlanConfiguration(programmingExercise);

        assertThat(legacyData).isPresent();
        assertThat(legacyData.orElseThrow().dockerImage()).isEqualTo("my/legacy-image:1.0");
        assertThat(legacyData.orElseThrow().buildScript()).contains("echo hi");
        assertThat(legacyData.orElseThrow().resultPaths().size()).isEqualTo(2);
        assertThat(legacyData.orElseThrow().resultPaths().getFirst()).isEqualTo("build/test-results/test/*.xml");
        assertThat(legacyData.orElseThrow().resultPaths().getLast()).isEqualTo("coverage.xml");
    }

    @Test
    void convertLegacyBuildPlanConfiguration_shouldReturnEmptyForInvalidJson() {
        final ProgrammingExercise programmingExercise = createExercise("non legacy", "echo hi");

        assertThat(legacyBuildPlanConverterService.convertLegacyBuildPlanConfiguration(programmingExercise)).isEmpty();
    }

    @Test
    void convertLegacyBuildPlanConfiguration_shouldReturnEmptyForNonTextualResultPath() {
        final ProgrammingExercise programmingExercise = createExercise("""
                {
                    "actions": [
                        {
                            "results": [
                                {
                                    "path": 7
                                }
                            ]
                        }
                    ]
                }
                """, "echo hi");

        assertThat(legacyBuildPlanConverterService.convertLegacyBuildPlanConfiguration(programmingExercise)).isEmpty();
    }

    private static ProgrammingExercise createExercise(String buildPlanConfiguration, String buildScript) {
        final ProgrammingExercise programmingExercise = new ProgrammingExercise();
        programmingExercise.setBuildConfig(new ProgrammingExerciseBuildConfig());
        programmingExercise.getBuildConfig().setBuildPlanConfiguration(buildPlanConfiguration);
        programmingExercise.getBuildConfig().setBuildScript(buildScript);
        return programmingExercise;
    }
}
