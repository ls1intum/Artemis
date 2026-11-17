package de.tum.cit.aet.artemis.buildagent.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DockerRunConfigTest {

    @ParameterizedTest
    @ValueSource(strings = { "host", "HOST", "Host", " host ", "container:abc", "Container:abc", " container:abc", "container:" })
    void networksOfTheHostOrAnotherContainerAreDetected(String network) {
        assertThat(DockerRunConfig.sharesNetworkNamespace(network)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "none", "bridge", "my-network", "hosted", "host-net", "containers", "my-container:x" })
    void otherNetworksAreNotDetected(String network) {
        assertThat(DockerRunConfig.sharesNetworkNamespace(network)).isFalse();
    }
}
