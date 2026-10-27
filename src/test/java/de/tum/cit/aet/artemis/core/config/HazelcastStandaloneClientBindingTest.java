package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;

class HazelcastStandaloneClientBindingTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "build-agent-1", "  " })
    void standaloneBuildAgentRetainsItsConfiguredNameOrDefaultFallback(String shortName) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("buildagent");
        if (shortName != null) {
            environment.setProperty("artemis.continuous-integration.build-agent.short-name", shortName);
        }
        var configuration = new HazelcastConfiguration(mock(ApplicationContext.class), new ServerProperties(), Optional.empty(), mock(EurekaInstanceHelper.class), environment,
                Optional.empty());
        ReflectionTestUtils.setField(configuration, "instanceName", "Artemis");
        var client = new ClientConfig();

        ReflectionTestUtils.invokeMethod(configuration, "configureClientIdentity", client);

        assertThat(client.getInstanceName()).isEqualTo(shortName == null || shortName.isBlank() ? "Artemis-client" : shortName);
    }

    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void standaloneCoreWithoutDiscoveryRemainsLoopbackBound() {
        String previousLocalAddress = System.getProperty("hazelcast.local.localAddress");
        String previousPublicAddress = System.getProperty("hazelcast.local.publicAddress");
        try {
            verifyBinding();
        }
        finally {
            restoreProperty("hazelcast.local.localAddress", previousLocalAddress);
            restoreProperty("hazelcast.local.publicAddress", previousPublicAddress);
        }
    }

    private void verifyBinding() {
        var configuration = new HazelcastConfiguration(mock(ApplicationContext.class), new ServerProperties(), Optional.empty(), mock(EurekaInstanceHelper.class),
                mock(Environment.class), Optional.empty());
        ReflectionTestUtils.setField(configuration, "hazelcastLocalInstances", false);
        ReflectionTestUtils.setField(configuration, "hazelcastPort", 5701);
        ReflectionTestUtils.setField(configuration, "instanceName", "Artemis");

        var localOnly = new Config();
        ReflectionTestUtils.invokeMethod(configuration, "configureNetworkBindingAndDiscovery", localOnly);
        assertThat(localOnly.getNetworkConfig().getInterfaces().isEnabled()).isTrue();
        assertThat(localOnly.getNetworkConfig().getInterfaces().getInterfaces()).containsExactly("127.0.0.1");

        ReflectionTestUtils.setField(configuration, "hazelcastInterface", "0.0.0.0");
        var externalClient = new Config();
        ReflectionTestUtils.invokeMethod(configuration, "configureNetworkBindingAndDiscovery", externalClient);
        assertThat(externalClient.getNetworkConfig().getInterfaces().isEnabled()).isTrue();
        assertThat(externalClient.getNetworkConfig().getInterfaces().getInterfaces()).containsExactly("127.0.0.1");
        assertThat(externalClient.getProperty("hazelcast.socket.bind.any")).isEqualTo("false");
        assertThat(externalClient.getProperty("hazelcast.socket.server.bind.any")).isEqualTo("false");
        assertThat(externalClient.getProperty("hazelcast.socket.client.bind.any")).isEqualTo("false");
    }

    private static void restoreProperty(String name, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(name);
        }
        else {
            System.setProperty(name, previousValue);
        }
    }
}
