package de.tum.cit.aet.artemis.hyperionworker.generation.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;

import de.tum.cit.aet.artemis.hyperionworker.generation.GenerationInput;
import de.tum.cit.aet.artemis.hyperionworker.generation.GenerationResources;

class SandboxBuildCommandServiceTest {

    private final GenerationInput input = new GenerationInput(1, "de.example", null, false, Set.of());

    @Test
    void disposableGradleCachesNeverUsePersistentDaemons() {
        var commands = new SandboxBuildCommandService(new GenerationResources());

        assertThat(commands.verifyScriptContent(input)).contains("-Dorg.gradle.daemon=false");
        assertThat(commands.readinessVerifyScriptContent(input)).contains("-Dorg.gradle.daemon=false");
    }

    @Test
    void packagedRecipeProvidesTheActualLocalCiPhasesAndReportPaths() {
        var commands = new SandboxBuildCommandService(new GenerationResources());
        var context = commands.describeBuildContext(input);

        assertThat(context.phaseScripts()).containsExactly("chmod +x ./gradlew\n./gradlew clean compileJava compileTestJava", "./gradlew test");
        assertThat(context.reportGlobs()).containsExactly("**/test-results/test/*.xml");
        assertThat(context.testCheckoutDir()).isEmpty();
        assertThat(commands.verifyScriptContent(input)).doesNotContain("@@", "mvn ").contains("/opt/hyperion", "__$canonical", "collect_one \"$seq\" \"$report\" \"junit.xml\"");
        assertThat(commands.readinessVerifyScriptContent(input)).doesNotContain("@@").contains("/opt/hyperion-readiness-fixture", "readiness fixture");
    }

    @ParameterizedTest
    @ValueSource(strings = { "[]", "- script: './gradlew test'", "- resultPaths: ['**/test-results/test/*.xml']" })
    void incompleteCanonicalRecipesFailClosed(String yaml) {
        var resources = mock(GenerationResources.class);
        when(resources.getResource(any())).thenReturn(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> new SandboxBuildCommandService(resources)).isInstanceOf(RuntimeException.class);
    }
}
