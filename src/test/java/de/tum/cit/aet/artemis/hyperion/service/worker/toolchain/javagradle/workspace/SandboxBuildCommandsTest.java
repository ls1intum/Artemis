package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;

import de.tum.cit.aet.artemis.hyperion.protocol.GenerationBuildTool;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.GenerationInput;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.GenerationResources;

class SandboxBuildCommandsTest {

    private final GenerationInput input = new GenerationInput(1, "de.example", null, false, Set.of(), Set.of(), GenerationBuildTool.GRADLE);

    /**
     * The agent's shell runs under a per-file size limit that the image's unpacked Gradle distribution exceeds, so copying the whole Gradle home made every agent-run verify.sh
     * fail with "File size limit exceeded". The distributions are linked in place; only their lock and marker files are copied.
     */
    @Test
    void verifyScriptLinksTheUnpackedWrapperDistributionInsteadOfCopyingIt() {
        String script = new SandboxBuildCommands(new GenerationResources()).verifyScriptContent(input);

        assertThat(script).doesNotContain("cp -a /root/.gradle/. ").contains("[ \"$entry\" = /root/.gradle/wrapper ] && continue",
                "for entry in /root/.gradle/wrapper/dists/*/*/*; do", "if [ -d \"$entry\" ]; then ln -s \"$entry\" \"$target\"", "else cp -a \"$entry\" \"$target\"");
    }

    /** Executes the generated Gradle-home preparation against a fake image home, so a broken loop, quoting, or link target fails here instead of in the sandbox. */
    @Test
    void theGeneratedGradleHomePreparationLinksDistributionsAndCopiesEverythingElse(@TempDir Path directory) throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/sh")), "a POSIX shell is required");
        String script = new SandboxBuildCommands(new GenerationResources()).verifyScriptContent(input);
        String preparation = script.substring(script.indexOf("GRADLE_WRAPPERS=$(find"), script.indexOf("if [ \"$LANE\" = \"behavior-isolated\" ]"));
        Path imageHome = directory.resolve("image-gradle");
        Path distribution = Files.createDirectories(imageHome.resolve("wrapper/dists/gradle-9-bin/hash/gradle-9"));
        FileUtils.writeStringToFile(distribution.resolve("lib.jar").toFile(), "jar", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(imageHome.resolve("wrapper/dists/gradle-9-bin/hash/gradle-9-bin.zip.lck").toFile(), "lock", StandardCharsets.UTF_8);
        Files.createDirectories(imageHome.resolve("caches/modules-2"));
        FileUtils.writeStringToFile(imageHome.resolve("caches/modules-2/entry").toFile(), "cached", StandardCharsets.UTF_8);
        Path buildDirectory = Files.createDirectories(directory.resolve("build"));
        FileUtils.writeStringToFile(buildDirectory.resolve("gradlew").toFile(), "#!/bin/sh\n", StandardCharsets.UTF_8);
        Path privateHome = directory.resolve("private-gradle");
        String command = "BUILD_DIR='" + buildDirectory + "'\n"
                + preparation.replace("/root/.gradle", imageHome.toString()).replace("/tmp/hyperion-gradle-home", privateHome.toString()) + "\nexit 0\n";

        Process process = new ProcessBuilder("/bin/sh", "-c", command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(process.waitFor()).as(output).isZero();
        Path linked = privateHome.resolve("wrapper/dists/gradle-9-bin/hash/gradle-9");
        assertThat(Files.isSymbolicLink(linked)).isTrue();
        assertThat(Files.readSymbolicLink(linked)).isEqualTo(distribution);
        Path lock = privateHome.resolve("wrapper/dists/gradle-9-bin/hash/gradle-9-bin.zip.lck");
        assertThat(Files.isRegularFile(lock, java.nio.file.LinkOption.NOFOLLOW_LINKS)).isTrue();
        assertThat(Files.readString(privateHome.resolve("caches/modules-2/entry"))).isEqualTo("cached");
        assertThat(Files.isExecutable(buildDirectory.resolve("gradlew"))).isTrue();
    }

    @Test
    void disposableGradleCachesNeverUsePersistentDaemons() {
        var commands = new SandboxBuildCommands(new GenerationResources());

        assertThat(commands.verifyScriptContent(input)).contains("-Dorg.gradle.daemon=false");
        assertThat(commands.readinessVerifyScriptContent(input)).contains("-Dorg.gradle.daemon=false");
    }

    @Test
    void packagedRecipeProvidesTheActualLocalCiPhasesAndReportPaths() {
        var commands = new SandboxBuildCommands(new GenerationResources());
        var context = commands.describeBuildContext(input);

        assertThat(context.phaseScripts()).containsExactly("chmod +x ./gradlew\n./gradlew clean compileJava compileTestJava", "./gradlew test");
        assertThat(context.reportGlobs()).containsExactly("**/test-results/test/*.xml");
        assertThat(context.testCheckoutDir()).isEmpty();
        assertThat(commands.verifyScriptContent(input)).doesNotContain("@@", "mvn ").contains("/opt/aiworker", "__$canonical", "collect_one \"$seq\" \"$report\" \"junit.xml\"");
        assertThat(commands.readinessVerifyScriptContent(input)).doesNotContain("@@").contains("/opt/aiworker-readiness-fixture", "readiness fixture");
    }

    @ParameterizedTest
    @ValueSource(strings = { "[]", "- script: './gradlew test'", "- resultPaths: ['**/test-results/test/*.xml']" })
    void incompleteCanonicalRecipesFailClosed(String yaml) {
        var resources = mock(GenerationResources.class);
        when(resources.getResource(any())).thenReturn(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> new SandboxBuildCommands(resources)).isInstanceOf(RuntimeException.class);
    }

    private final GenerationInput mavenInput = new GenerationInput(1, "de.example", null, false, Set.of(), Set.of(), GenerationBuildTool.MAVEN);

    @Test
    void mavenExercisesRunTheCanonicalMavenPhasesOfflineAgainstTheImageRepository() {
        var commands = new SandboxBuildCommands(new GenerationResources());
        var context = commands.describeBuildContext(mavenInput);

        assertThat(context.phaseScripts()).containsExactly("mvn -B clean compile", "mvn -B test");
        assertThat(context.reportGlobs()).containsExactly("**/target/surefire-reports/*.xml");
        String script = commands.verifyScriptContent(mavenInput);
        assertThat(script).doesNotContain("@@", "./gradlew", "org.gradle.daemon")
                .contains("export MAVEN_ARGS=\"-o -Dmaven.repo.local=/tmp/hyperion-maven-repository -Dmaven.repo.local.tail=/root/.m2/repository\"")
                .contains("rm -rf \"/tmp/hyperion-maven-repository\"", "-path '*/target/surefire-reports/*.xml'");
        assertThat(commands.readinessVerifyScriptContent(mavenInput)).doesNotContain("@@").contains("MAVEN_ARGS", "/opt/aiworker-readiness-fixture");
    }

    @Test
    void mavenSourceIsolatedLaneCompilesThroughTheAresAgentCopyThenRunsOnlySurefire() {
        String script = new SandboxBuildCommands(new GenerationResources()).verifyScriptContent(mavenInput);
        String isolated = script.substring(script.indexOf("if [ \"$LANE\" = \"behavior-isolated\" ]; then\n    run_phase"), script.indexOf("else\n    run_phase"));

        assertThat(isolated).contains("run_phase 'mvn -B clean compile'", "run_phase 'mvn -B process-test-classes'", "run_phase 'mvn -B surefire:test'")
                .doesNotContain("run_phase 'mvn -B test'");
        assertThat(isolated.indexOf("process-test-classes")).isLessThan(isolated.indexOf("-name '*.java' -delete"));
        assertThat(isolated.indexOf("-name '*.java' -delete")).isLessThan(isolated.indexOf("surefire:test"));
    }
}
