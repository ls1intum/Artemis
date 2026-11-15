package de.tum.cit.aet.artemis.buildagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.github.dockerjava.api.model.HostConfig;

import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;

/**
 * Unit tests for the optional limit of the writable layer of a build container.
 */
class BuildAgentConfigurationStorageLimitTest {

    private BuildAgentConfiguration configuration(String limit) {
        BuildAgentConfiguration configuration = new BuildAgentConfiguration(mock(ProgrammingLanguageConfiguration.class));
        configuration.containerStorageLimit = limit;
        return configuration;
    }

    @Test
    void withoutALimitTheHostConfigIsUnchanged() {
        HostConfig hostConfig = HostConfig.newHostConfig();

        assertThat(configuration("").applyStorageLimit(hostConfig).getStorageOpt()).isNull();
        assertThat(configuration(null).applyStorageLimit(hostConfig).getStorageOpt()).isNull();
    }

    @Test
    void aLimitIsPassedToDockerInBytes() {
        assertThat(configuration("10g").applyStorageLimit(HostConfig.newHostConfig()).getStorageOpt()).containsEntry("size", String.valueOf(10L * 1024 * 1024 * 1024));
        assertThat(configuration("512M").applyStorageLimit(HostConfig.newHostConfig()).getStorageOpt()).containsEntry("size", String.valueOf(512L * 1024 * 1024));
        assertThat(configuration("2048").applyStorageLimit(HostConfig.newHostConfig()).getStorageOpt()).containsEntry("size", "2048");
    }

    @Test
    void aLimitThatIsNotASizeIsRefused() {
        assertThatThrownBy(() -> configuration("lots").applyStorageLimit(HostConfig.newHostConfig())).isInstanceOf(LocalCIException.class);
    }
}
