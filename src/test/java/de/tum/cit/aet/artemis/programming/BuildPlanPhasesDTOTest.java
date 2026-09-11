package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;

import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;

class BuildPlanPhasesDTOTest {

    @Test
    void constraints_shouldBoundTokensDepthAndDocumentSize() {
        var constraints = BuildPlanPhasesDTO.BUILD_PLAN_CONFIGURATION_CONSTRAINTS;
        assertThat(constraints.getMaxTokenCount()).isEqualTo(10_000);
        assertThat(constraints.getMaxNestingDepth()).isEqualTo(32);
        assertThat(constraints.getMaxDocumentLength()).isEqualTo(1024L * 1024);
    }

    @Test
    void fromBuildPlanConfiguration_shouldParseRegularConfiguration() throws Exception {
        String configuration = """
                {
                    "phases": [
                        {
                            "name": "test",
                            "script": "./gradlew test",
                            "resultPaths": ["build/test-results/test/*.xml"]
                        }
                    ],
                    "dockerImage": "my/image:1.0"
                }
                """;

        BuildPlanPhasesDTO result = BuildPlanPhasesDTO.fromBuildPlanConfiguration(configuration);

        assertThat(result.dockerImage()).isEqualTo("my/image:1.0");
        assertThat(result.phases()).hasSize(1);
        assertThat(result.phases().getFirst().name()).isEqualTo("test");
        assertThat(result.phases().getFirst().resultPaths()).containsExactly("build/test-results/test/*.xml");
    }

    @Test
    void fromBuildPlanConfiguration_shouldReturnEmptyDtoForBlankInput() throws Exception {
        assertThat(BuildPlanPhasesDTO.fromBuildPlanConfiguration("  ").phases()).isNull();
    }

    @Test
    void roundTrip_shouldPreserveConfiguration() throws Exception {
        BuildPlanPhasesDTO original = new BuildPlanPhasesDTO(
                List.of(new BuildPhaseDTO("test", "./gradlew test", BuildPhaseCondition.ALWAYS, false, List.of("build/test-results/test/*.xml"))), "my/image:1.0");

        BuildPlanPhasesDTO restored = BuildPlanPhasesDTO.fromBuildPlanConfiguration(original.toBuildPlanConfiguration());

        assertThat(restored.dockerImage()).isEqualTo("my/image:1.0");
        assertThat(restored.phases()).hasSize(1);
        assertThat(restored.phases().getFirst().script()).isEqualTo("./gradlew test");
        assertThat(restored.phases().getFirst().resultPaths()).containsExactly("build/test-results/test/*.xml");
    }

    @Test
    void fromBuildPlanConfiguration_shouldRejectExcessivelyManyTokens() {
        String widePhases = buildWidePhasesConfiguration(6_000);

        // readValue wraps the parser-level StreamConstraintsException in a JsonMappingException; both are JacksonException,
        // which is what the callers catch, so the oversized payload is rejected during parsing instead of building a huge tree.
        assertThatThrownBy(() -> BuildPlanPhasesDTO.fromBuildPlanConfiguration(widePhases)).isInstanceOf(JacksonException.class).hasMessageContaining("Token count");
    }

    private static String buildWidePhasesConfiguration(int phaseCount) {
        StringBuilder builder = new StringBuilder("{\"phases\":[");
        for (int i = 0; i < phaseCount; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append("{}");
        }
        builder.append("]}");
        return builder.toString();
    }
}
