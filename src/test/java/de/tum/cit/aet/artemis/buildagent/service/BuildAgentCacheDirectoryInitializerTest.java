package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;

/**
 * Unit tests for {@link BuildAgentCacheDirectoryInitializer}. We deliberately do not assert anything about whether
 * {@code setfacl} actually ran — that depends on whether the {@code acl} package is installed on the test host, and
 * we want this test class to be portable across the developer fleet. We do assert the directory-creation contract
 * (idempotent, creates if missing, leaves existing dir alone) and that the initializer is a no-op when no cache is
 * configured.
 */
class BuildAgentCacheDirectoryInitializerTest {

    @TempDir
    Path scratch;

    @Test
    void createsMissingCacheDirectory() throws IOException {
        Path target = scratch.resolve("m2-fresh");
        assertThat(Files.exists(target)).isFalse();
        BuildAgentConfiguration cfg = mock(BuildAgentConfiguration.class);
        when(cfg.mavenCacheHostPath()).thenReturn(target);
        when(cfg.gradleCacheHostPath()).thenReturn(null);

        BuildAgentCacheDirectoryInitializer initializer = new BuildAgentCacheDirectoryInitializer(cfg);

        initializer.initializeCacheDirectories();

        assertThat(Files.isDirectory(target)).isTrue();
    }

    @Test
    void doesNotFailWhenCacheDirectoryAlreadyExists() throws IOException {
        Path target = scratch.resolve("m2-existing");
        Files.createDirectories(target);
        Files.writeString(target.resolve("existing-file.txt"), "hello");
        BuildAgentConfiguration cfg = mock(BuildAgentConfiguration.class);
        when(cfg.mavenCacheHostPath()).thenReturn(target);
        when(cfg.gradleCacheHostPath()).thenReturn(null);

        BuildAgentCacheDirectoryInitializer initializer = new BuildAgentCacheDirectoryInitializer(cfg);

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

        BuildAgentCacheDirectoryInitializer initializer = new BuildAgentCacheDirectoryInitializer(cfg);

        // Must not throw and must not require any filesystem state.
        initializer.initializeCacheDirectories();
    }

    @Test
    void handlesBothMavenAndGradle() throws IOException {
        Path mavenPath = scratch.resolve("m2");
        Path gradlePath = scratch.resolve("gradle");
        BuildAgentConfiguration cfg = mock(BuildAgentConfiguration.class);
        when(cfg.mavenCacheHostPath()).thenReturn(mavenPath);
        when(cfg.gradleCacheHostPath()).thenReturn(gradlePath);

        BuildAgentCacheDirectoryInitializer initializer = new BuildAgentCacheDirectoryInitializer(cfg);

        initializer.initializeCacheDirectories();

        assertThat(Files.isDirectory(mavenPath)).isTrue();
        assertThat(Files.isDirectory(gradlePath)).isTrue();
    }
}
