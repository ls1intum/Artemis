package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;

/**
 * Unit tests for {@link BuildAgentCacheDirectoryService}. We deliberately do not assert anything about whether
 * {@code setfacl} actually ran — that depends on whether the {@code acl} package is installed on the test host, and
 * we want this test class to be portable across the developer fleet. We do assert the directory-creation contract
 * (idempotent, creates if missing, leaves existing dir alone) and that the initializer is a no-op when no cache is
 * configured.
 */
class BuildAgentCacheDirectoryServiceTest {

    @TempDir
    Path scratch;

    @Test
    void createsMissingCacheDirectory() throws IOException {
        Path target = scratch.resolve("m2-fresh");
        assertThat(Files.exists(target)).isFalse();
        BuildAgentConfiguration cfg = mock(BuildAgentConfiguration.class);
        when(cfg.mavenCacheHostPath()).thenReturn(target);
        when(cfg.gradleCacheHostPath()).thenReturn(null);

        BuildAgentCacheDirectoryService initializer = new BuildAgentCacheDirectoryService(cfg);

        initializer.initializeCacheDirectories();

        assertThat(Files.isDirectory(target)).isTrue();
    }

    @Test
    void doesNotFailWhenCacheDirectoryAlreadyExists() throws IOException {
        Path target = scratch.resolve("m2-existing");
        Files.createDirectories(target);
        // Architecture rule forbids Files.write* — use FileUtils.writeByteArrayToFile instead.
        FileUtils.writeByteArrayToFile(target.resolve("existing-file.txt").toFile(), "hello".getBytes(StandardCharsets.UTF_8));
        BuildAgentConfiguration cfg = mock(BuildAgentConfiguration.class);
        when(cfg.mavenCacheHostPath()).thenReturn(target);
        when(cfg.gradleCacheHostPath()).thenReturn(null);

        BuildAgentCacheDirectoryService initializer = new BuildAgentCacheDirectoryService(cfg);

        initializer.initializeCacheDirectories();

        // Idempotent: still there, untouched contents.
        assertThat(Files.isDirectory(target)).isTrue();
        assertThat(Files.readString(target.resolve("existing-file.txt"))).isEqualTo("hello");
    }

    @Test
    void noOpWhenNeitherCacheIsConfigured() {
        BuildAgentConfiguration cfg = mock(BuildAgentConfiguration.class);
        when(cfg.mavenCacheHostPath()).thenReturn(null);
        when(cfg.gradleCacheHostPath()).thenReturn(null);

        BuildAgentCacheDirectoryService initializer = new BuildAgentCacheDirectoryService(cfg);

        // Must not throw and must not require any filesystem state.
        initializer.initializeCacheDirectories();
    }

    @Test
    void buildSetfaclCommand_appendsPathAsLastArgumentAndPreservesAllAclSpecs() {
        // Regression: an earlier off-by-one sized the argv as args.length + 1 and OVERWROTE the final argument
        // (the trailing ACL spec) with the path. The command shipped to setfacl was missing its last "-m u:...:rwx"
        // and every run on staging produced "Invalid argument near character 1". Pin the full argv shape here.
        Path cacheDir = Path.of("/var/cache/artemis-buildagent/m2");

        String[] cmd = BuildAgentCacheDirectoryService.buildSetfaclCommand(cacheDir, "-R", "-m", "u:artemis:rwx", "-d", "-m", "u:artemis:rwx");

        assertThat(cmd).containsExactly("setfacl", "-R", "-m", "u:artemis:rwx", "-d", "-m", "u:artemis:rwx", "/var/cache/artemis-buildagent/m2");
    }

    @Test
    void buildSetfaclCommand_acceptsAnEmptyArgsArrayWithoutOverwritingTheBinaryName() {
        Path cacheDir = Path.of("/tmp/cache");

        String[] cmd = BuildAgentCacheDirectoryService.buildSetfaclCommand(cacheDir);

        assertThat(cmd).containsExactly("setfacl", "/tmp/cache");
    }

    @Test
    void handlesBothMavenAndGradle() throws IOException {
        Path mavenPath = scratch.resolve("m2");
        Path gradlePath = scratch.resolve("gradle");
        BuildAgentConfiguration cfg = mock(BuildAgentConfiguration.class);
        when(cfg.mavenCacheHostPath()).thenReturn(mavenPath);
        when(cfg.gradleCacheHostPath()).thenReturn(gradlePath);

        BuildAgentCacheDirectoryService initializer = new BuildAgentCacheDirectoryService(cfg);

        initializer.initializeCacheDirectories();

        assertThat(Files.isDirectory(mavenPath)).isTrue();
        assertThat(Files.isDirectory(gradlePath)).isTrue();
    }
}
