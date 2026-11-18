package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.core.config.ContainerDefaults;
import de.tum.cit.aet.artemis.core.config.ContainerDefaultsConfiguration;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;

class ProgrammingExerciseBuildConfigNetworkTest {

    private ProgrammingExerciseBuildConfigService service;

    @BeforeEach
    void setUp() {
        service = new ProgrammingExerciseBuildConfigService(mock(LicenseService.class),
                new ContainerDefaults(new ContainerDefaultsConfiguration(List.of(), null), List.of("none")));
        ReflectionTestUtils.setField(service, "allowedNetworks", List.of("none", "host", "HOST", " host ", "container:other", "Container:other", "restricted"));
    }

    private static ProgrammingExerciseBuildConfig configWithNetwork(String network) {
        var buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setDockerFlags("{\"network\":\"" + network + "\",\"env\":{},\"cpuCount\":1,\"memory\":1024,\"memorySwap\":1024}");
        return buildConfig;
    }

    @Test
    void listedNetworkIsAccepted() {
        assertThat(service.parseDockerFlags(configWithNetwork("restricted")).network()).isEqualTo("restricted");
    }

    @Test
    void networkOfTheHostOrAnotherContainerIsRejectedEvenWhenListed() {
        for (String network : List.of("host", "HOST", " host ", "container:other", "Container:other")) {
            assertThatThrownBy(() -> service.parseDockerFlags(configWithNetwork(network))).isInstanceOf(ResponseStatusException.class);
        }
    }

    @Test
    void missingOrBlankNetworkIsAccepted() {
        assertThat(service.parseDockerFlags(configWithNetwork("")).network()).isEmpty();
        var withoutNetwork = new ProgrammingExerciseBuildConfig();
        withoutNetwork.setDockerFlags("{\"env\":{},\"cpuCount\":1,\"memory\":1024,\"memorySwap\":1024}");
        assertThat(service.parseDockerFlags(withoutNetwork).network()).isNull();
    }

    @Test
    void unlistedNetworkIsRejected() {
        assertThatThrownBy(() -> service.parseDockerFlags(configWithNetwork("other"))).isInstanceOf(ResponseStatusException.class);
    }
}
