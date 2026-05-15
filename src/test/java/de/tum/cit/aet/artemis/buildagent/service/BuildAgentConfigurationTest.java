package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;

class BuildAgentConfigurationTest {

    @Test
    void testBytes() {
        assertThat("512 Bytes").isEqualTo(BuildAgentConfiguration.formatMemory(512));
    }

    @Test
    void testKilobytes() {
        assertThat("1 KB").isEqualTo(BuildAgentConfiguration.formatMemory(1024));
        assertThat("999 KB").isEqualTo(BuildAgentConfiguration.formatMemory(1024 * 999));
    }

    @Test
    void testMegabytes() {
        assertThat("1 MB").isEqualTo(BuildAgentConfiguration.formatMemory(1024 * 1024));
        assertThat("1023 MB").isEqualTo(BuildAgentConfiguration.formatMemory(1024 * 1024 * 1023L));
    }

    @Test
    void testGigabytes() {
        assertThat("1.0 GB").isEqualTo(BuildAgentConfiguration.formatMemory(1024 * 1024 * 1024L));
        assertThat("1.5 GB").isEqualTo(BuildAgentConfiguration.formatMemory(1024 * 1024 * 1024 * 3L / 2));
    }

    @Test
    void testBuildContainerCacheBindsReturnsEmptyWhenUnset() {
        BuildAgentConfiguration config = new BuildAgentConfiguration(null);

        assertThat(config.buildContainerCacheBinds()).isEmpty();
    }

    @Test
    void testBuildContainerCacheBindsReturnsEmptyWhenBlank() {
        BuildAgentConfiguration config = new BuildAgentConfiguration(null);
        ReflectionTestUtils.setField(config, "mavenCacheHostPath", "");
        ReflectionTestUtils.setField(config, "gradleCacheHostPath", "   ");

        assertThat(config.buildContainerCacheBinds()).isEmpty();
    }

    @Test
    void testBuildContainerCacheBindsMapsMavenAndGradle() {
        BuildAgentConfiguration config = new BuildAgentConfiguration(null);
        ReflectionTestUtils.setField(config, "mavenCacheHostPath", "/var/cache/artemis-buildagent/m2");
        ReflectionTestUtils.setField(config, "gradleCacheHostPath", "/var/cache/artemis-buildagent/gradle");

        List<Bind> binds = config.buildContainerCacheBinds();

        assertThat(binds).hasSize(2);
        assertThat(binds.get(0).getPath()).isEqualTo("/var/cache/artemis-buildagent/m2");
        assertThat(binds.get(0).getVolume().getPath()).isEqualTo("/root/.m2");
        assertThat(binds.get(0).getAccessMode()).isEqualTo(AccessMode.rw);
        assertThat(binds.get(1).getPath()).isEqualTo("/var/cache/artemis-buildagent/gradle");
        assertThat(binds.get(1).getVolume().getPath()).isEqualTo("/root/.gradle");
        assertThat(binds.get(1).getAccessMode()).isEqualTo(AccessMode.rw);
    }

    @Test
    void testBuildContainerCacheBindsAllowsMavenOnly() {
        BuildAgentConfiguration config = new BuildAgentConfiguration(null);
        ReflectionTestUtils.setField(config, "mavenCacheHostPath", "/var/cache/artemis-buildagent/m2");

        List<Bind> binds = config.buildContainerCacheBinds();

        assertThat(binds).hasSize(1);
        assertThat(binds.get(0).getVolume().getPath()).isEqualTo("/root/.m2");
    }

    @Test
    void testBuildContainerCacheBindsAllowsGradleOnly() {
        BuildAgentConfiguration config = new BuildAgentConfiguration(null);
        ReflectionTestUtils.setField(config, "gradleCacheHostPath", "/var/cache/artemis-buildagent/gradle");

        List<Bind> binds = config.buildContainerCacheBinds();

        assertThat(binds).hasSize(1);
        assertThat(binds.get(0).getVolume().getPath()).isEqualTo("/root/.gradle");
    }

    @Test
    void testBuildContainerCacheBindsAreReadWriteByDefault() {
        BuildAgentConfiguration config = new BuildAgentConfiguration(null);
        ReflectionTestUtils.setField(config, "mavenCacheHostPath", "/var/cache/artemis-buildagent/m2");
        ReflectionTestUtils.setField(config, "gradleCacheHostPath", "/var/cache/artemis-buildagent/gradle");

        List<Bind> binds = config.buildContainerCacheBinds();

        assertThat(binds).allSatisfy(b -> assertThat(b.getAccessMode()).isEqualTo(AccessMode.rw));
    }

    @Test
    void testBuildContainerCacheBindsRespectReadOnlyFlag() {
        BuildAgentConfiguration config = new BuildAgentConfiguration(null);
        ReflectionTestUtils.setField(config, "mavenCacheHostPath", "/var/cache/artemis-buildagent/m2");
        ReflectionTestUtils.setField(config, "gradleCacheHostPath", "/var/cache/artemis-buildagent/gradle");
        ReflectionTestUtils.setField(config, "buildContainerCacheReadOnly", true);

        List<Bind> binds = config.buildContainerCacheBinds();

        assertThat(binds).allSatisfy(b -> assertThat(b.getAccessMode()).isEqualTo(AccessMode.ro));
    }
}
