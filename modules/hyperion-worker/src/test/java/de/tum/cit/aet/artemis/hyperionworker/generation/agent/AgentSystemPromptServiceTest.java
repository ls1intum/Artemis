package de.tum.cit.aet.artemis.hyperionworker.generation.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.tum.cit.aet.artemis.hyperion.protocol.ExerciseBrief.Mode;
import de.tum.cit.aet.artemis.hyperionworker.generation.GenerationInput;
import de.tum.cit.aet.artemis.hyperionworker.generation.GenerationResources;
import de.tum.cit.aet.artemis.hyperionworker.generation.workspace.SandboxBuildCommandService;

class AgentSystemPromptServiceTest {

    private final AgentSystemPromptService prompts = new AgentSystemPromptService(new SandboxBuildCommandService(new GenerationResources()));

    private static GenerationInput input(boolean hasDueDate) {
        return new GenerationInput(1, "de.example", null, hasDueDate, Set.of());
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void singleLoopUsesGradleLayoutAndStableGradedTestNames(Mode mode) {
        String prompt = prompts.build(input(false), mode);

        assertThat(prompt)
                .contains("tests/test/<package path>", "tests/build.gradle", "DisplayNameGenerator.Simple.class", "@WhitelistPath(\"build\")",
                        "@BlacklistPath(\"build/classes/java/test\")", "SECURITY BOUNDARY", "./gradlew", "Design the graded entry point BEFORE choosing demonstration output")
                .doesNotContain("Maven", "mvn ");
        if (mode == Mode.ADAPT) {
            assertThat(prompt).contains("Edit only exercise-specific test sources required by the feedback; preserve all others.");
        }
        else {
            assertThat(prompt).contains("Replace only exercise-specific test source files.");
        }
    }

    @ParameterizedTest
    @EnumSource(GenerationStage.class)
    void stagedPromptsExposeOnlyTheRelevantBuildGuidance(GenerationStage stage) {
        String prompt = prompts.buildStage(input(false), stage);

        assertThat(prompt).contains("SECURITY BOUNDARY", "Design the graded entry point BEFORE choosing demonstration output").doesNotContain("Maven", "mvn ");
        if (stage == GenerationStage.TESTS) {
            assertThat(prompt).contains("DisplayNameGenerator.Simple.class", "tests/build.gradle");
        }
        else {
            assertThat(prompt).doesNotContain("DisplayNameGenerator.Simple.class");
        }
    }

    @Test
    void hiddenTestGuidanceTracksTheImmutableDueDateCapability() {
        assertThat(prompts.buildStage(input(false), GenerationStage.TESTS)).contains("Every Testing Strategy hidden-variant cell must be `no`");
        assertThat(prompts.buildStage(input(true), GenerationStage.TESTS)).contains("configured due date", "additional `AFTER_DUE_DATE` witness");
    }
}
