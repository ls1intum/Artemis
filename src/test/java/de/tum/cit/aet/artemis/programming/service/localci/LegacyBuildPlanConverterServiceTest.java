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

        var buildPlanPhases = legacyBuildPlanConverterService.convertLegacyBuildPlanConfiguration(programmingExercise);

        assertThat(buildPlanPhases).isPresent();
        assertThat(buildPlanPhases.orElseThrow().dockerImage()).isEqualTo("my/legacy-image:1.0");
        assertThat(buildPlanPhases.orElseThrow().phases()).hasSize(1);
        assertThat(buildPlanPhases.orElseThrow().phases().getFirst().name()).isEqualTo("script");
        assertThat(buildPlanPhases.orElseThrow().phases().getFirst().resultPaths()).containsExactly("build/test-results/test/*.xml", "coverage.xml");
        assertThat(buildPlanPhases.orElseThrow().phases().getFirst().script()).contains("cd /var/tmp/testing-dir\n");
        assertThat(buildPlanPhases.orElseThrow().phases().getFirst().script()).contains("cat << '  __LEGACY_INNER_SCRIPT_END__' > \"${tmp_file}\"\n");
        assertThat(buildPlanPhases.orElseThrow().phases().getFirst().script()).contains("echo hi\n");
    }

    @Test
    void convertLegacyBuildPlanConfiguration_shouldReturnEmptyForInvalidJson() {
        final ProgrammingExercise programmingExercise = createExercise("non legacy", "echo hi");

        assertThat(legacyBuildPlanConverterService.convertLegacyBuildPlanConfiguration(programmingExercise)).isEmpty();
    }

    @Test
    void convertLegacyBuildPlanConfiguration_shouldAcceptBlankDockerImage() {
        final ProgrammingExercise programmingExercise = createExercise("""
                {
                    "metadata": {
                        "docker": {
                            "image": "   "
                        }
                    },
                    "actions": []
                }
                """, "echo hi");

        var buildPlanPhases = legacyBuildPlanConverterService.convertLegacyBuildPlanConfiguration(programmingExercise);

        assertThat(buildPlanPhases).isPresent();
        assertThat(buildPlanPhases.orElseThrow().dockerImage()).isEmpty();
    }

    @Test
    void convertLegacyBuildPlanConfiguration_shouldAcceptBlankResultPath() {
        final ProgrammingExercise programmingExercise = createExercise("""
                {
                    "actions": [
                        {
                            "name": "test",
                            "results": [
                                {
                                    "path": "   "
                                }
                            ]
                        }
                    ]
                }
                """, "echo hi");

        var buildPlanPhases = legacyBuildPlanConverterService.convertLegacyBuildPlanConfiguration(programmingExercise);

        assertThat(buildPlanPhases).isPresent();
        assertThat(buildPlanPhases.orElseThrow().phases().getFirst().resultPaths()).containsExactly("");
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
