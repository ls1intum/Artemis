package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import com.hazelcast.config.Config;

class HazelcastStandaloneClientBindingTest {

    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void standaloneCoreAcceptsExternalClientsOnlyWithExplicitInterfaceSetting() {
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
        assertThat(externalClient.getNetworkConfig().getInterfaces().isEnabled()).isFalse();
        assertThat(externalClient.getNetworkConfig().getPort()).isEqualTo(5701);
        assertThat(externalClient.getClusterName()).isEqualTo("prod");
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
