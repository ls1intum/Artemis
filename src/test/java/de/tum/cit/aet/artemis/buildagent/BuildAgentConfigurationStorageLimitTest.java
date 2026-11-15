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

    private BuildAgentConfiguration configuration(Long limitBytes) {
        BuildAgentConfiguration configuration = new BuildAgentConfiguration(mock(ProgrammingLanguageConfiguration.class));
        configuration.storageLimitBytes = limitBytes;
        return configuration;
    }

    @Test
    void withoutALimitTheHostConfigIsUnchanged() {
        assertThat(configuration(null).applyStorageLimit(HostConfig.newHostConfig()).getStorageOpt()).isNull();
        assertThat(BuildAgentConfiguration.parseStorageLimit("")).isNull();
        assertThat(BuildAgentConfiguration.parseStorageLimit("  ")).isNull();
        assertThat(BuildAgentConfiguration.parseStorageLimit(null)).isNull();
    }

    @Test
    void aLimitIsPassedToDockerInBytes() {
        assertThat(configuration(1024L).applyStorageLimit(HostConfig.newHostConfig()).getStorageOpt()).containsEntry("size", "1024");
    }

    @Test
    void sizesWithUnitsAreParsedIntoBytes() {
        assertThat(BuildAgentConfiguration.parseStorageLimit("10g")).isEqualTo(10L * 1024 * 1024 * 1024);
        assertThat(BuildAgentConfiguration.parseStorageLimit(" 512MB ")).isEqualTo(512L * 1024 * 1024);
        assertThat(BuildAgentConfiguration.parseStorageLimit("64k")).isEqualTo(64L * 1024);
        assertThat(BuildAgentConfiguration.parseStorageLimit("2048")).isEqualTo(2048L);
        assertThat(BuildAgentConfiguration.parseStorageLimit("2048b")).isEqualTo(2048L);
    }

    @Test
    void aLimitThatIsNotASizeOrTooLargeIsRefused() {
        assertThatThrownBy(() -> BuildAgentConfiguration.parseStorageLimit("lots")).isInstanceOf(LocalCIException.class);
        assertThatThrownBy(() -> BuildAgentConfiguration.parseStorageLimit("-5g")).isInstanceOf(LocalCIException.class);
        assertThatThrownBy(() -> BuildAgentConfiguration.parseStorageLimit("999999999999g")).isInstanceOf(LocalCIException.class);
    }
}
