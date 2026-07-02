package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.hibernate7.Hibernate7Module;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;

class LegacyBuildPlanConverterServiceTest {

    private final LegacyBuildPlanConverterService legacyBuildPlanConverterService = new LegacyBuildPlanConverterService();

    @Test
    void deserializeBuildConfig_shouldKeepLegacyBuildScriptWithHibernateModule() throws Exception {
        final ObjectMapper objectMapper = new ObjectMapper().registerModule(new Hibernate7Module());

        final ProgrammingExerciseBuildConfig buildConfig = objectMapper.readValue("""
                {
                    "buildPlanConfiguration": "{}",
                    "buildScript": "echo hi"
                }
                """, ProgrammingExerciseBuildConfig.class);

        assertThat(buildConfig.getBuildScript()).isEqualTo("echo hi");
    }

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

        var phase = buildPlanPhases.orElseThrow().phases().getFirst();
        assertThat(phase.name()).isEqualTo("script");
        assertThat(phase.condition()).isEqualTo(BuildPhaseCondition.ALWAYS);
        assertThat(phase.forceRun()).isFalse();
        assertThat(phase.resultPaths()).containsExactly("build/test-results/test/*.xml", "coverage.xml");
        assertThat(phase.script()).isEqualTo("""
                # feel free to remove the code surrounding your script and split your script into multiple phases
                cd /var/tmp/testing-dir
                local tmp_file=$(mktemp)
                cat << '  __LEGACY_INNER_SCRIPT_END__' > "${tmp_file}"  # two leading spaces are intentional as the final script will be indented be for a phase
                echo hi
                __LEGACY_INNER_SCRIPT_END__
                chmod +x "${tmp_file}"
                "${tmp_file}" "$@"
                """);
    }

    @Test
    void convertLegacyBuildPlanConfiguration_shouldConvertBuildScriptWithInvalidJson() {
        final ProgrammingExercise programmingExercise = createExercise("non legacy", "echo hi");

        var buildPlanPhases = legacyBuildPlanConverterService.convertLegacyBuildPlanConfiguration(programmingExercise);

        assertThat(buildPlanPhases).isPresent();
        assertThat(buildPlanPhases.orElseThrow().dockerImage()).isNull();
        assertThat(buildPlanPhases.orElseThrow().phases()).hasSize(1);

        var phase = buildPlanPhases.orElseThrow().phases().getFirst();
        assertThat(phase.resultPaths()).isEmpty();
        assertThat(phase.script()).contains("echo hi");
    }

    @Test
    void convertLegacyBuildPlanConfiguration_shouldReturnEmptyForInvalidJsonWithoutBuildScript() {
        final ProgrammingExercise programmingExercise = createExercise("non legacy", null);

        assertThat(legacyBuildPlanConverterService.convertLegacyBuildPlanConfiguration(programmingExercise)).isEmpty();
    }

    @Test
    void convertLegacyBuildPlanConfiguration_shouldIgnoreNonTextualResultPath() {
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

        var buildPlanPhases = legacyBuildPlanConverterService.convertLegacyBuildPlanConfiguration(programmingExercise);

        assertThat(buildPlanPhases).isPresent();
        assertThat(buildPlanPhases.orElseThrow().phases().getFirst().resultPaths()).isEmpty();
    }

    @Test
    void convertLegacyBuildPlanConfiguration_shouldConvertLegacyActionsWhenBuildScriptIsMissing() {
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
                            "script": "./gradlew compileJava",
                            "workdir": "assignment",
                            "results": [
                                {
                                    "path": " build/test-results/compile/*.xml "
                                }
                            ]
                        },
                        {
                            "name": "test",
                            "script": "./gradlew test",
                            "results": [
                                {
                                    "path": "build/test-results/test/*.xml"
                                }
                            ]
                        },
                        {
                            "name": "platform-only",
                            "platform": "jenkins"
                        }
                    ]
                }
                """, null);

        var buildPlanPhases = legacyBuildPlanConverterService.convertLegacyBuildPlanConfiguration(programmingExercise);

        assertThat(buildPlanPhases).isPresent();
        assertThat(buildPlanPhases.orElseThrow().dockerImage()).isEqualTo("my/legacy-image:1.0");
        assertThat(buildPlanPhases.orElseThrow().phases()).hasSize(1);

        var phase = buildPlanPhases.orElseThrow().phases().getFirst();
        assertThat(phase.name()).isEqualTo("script");
        assertThat(phase.condition()).isEqualTo(BuildPhaseCondition.ALWAYS);
        assertThat(phase.forceRun()).isFalse();
        assertThat(phase.resultPaths()).containsExactly("build/test-results/compile/*.xml", "build/test-results/test/*.xml");
        assertThat(phase.script()).isEqualTo("""
                # feel free to remove the code surrounding your script and split your script into multiple phases
                cd /var/tmp/testing-dir
                local tmp_file=$(mktemp)
                cat << '  __LEGACY_INNER_SCRIPT_END__' > "${tmp_file}"  # two leading spaces are intentional as the final script will be indented be for a phase
                #!/bin/bash
                cd /var/tmp/testing-dir
                cd /var/tmp/testing-dir/assignment
                ./gradlew compileJava
                cd /var/tmp/testing-dir
                ./gradlew test

                __LEGACY_INNER_SCRIPT_END__
                chmod +x "${tmp_file}"
                "${tmp_file}" "$@"
                """);
    }

    private static ProgrammingExercise createExercise(String buildPlanConfiguration, String buildScript) {
        final ProgrammingExercise programmingExercise = new ProgrammingExercise();
        programmingExercise.setBuildConfig(new ProgrammingExerciseBuildConfig());
        programmingExercise.getBuildConfig().setBuildPlanConfiguration(buildPlanConfiguration);
        programmingExercise.getBuildConfig().setBuildScript(buildScript);
        return programmingExercise;
    }
}
