package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.RoutingMode;
import com.hazelcast.cluster.Address;
import com.hazelcast.cluster.Cluster;
import com.hazelcast.cluster.Member;
import com.hazelcast.config.Config;
import com.hazelcast.core.DistributedObject;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@Isolated("Changes Hazelcast JVM properties while constructing configurations.")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class HazelcastConfigurationLifecycleTest {

    private final Map<String, String> savedProperties = new HashMap<>();

    @BeforeEach
    void saveSystemProperties() {
        for (String name : List.of("hazelcast.phone.home.enabled", "hazelcast.local.localAddress", "hazelcast.local.publicAddress", "hazelcast.prefer.ipv4.stack")) {
            savedProperties.put(name, System.getProperty(name));
        }
    }

    @AfterEach
    void restoreSystemProperties() {
        savedProperties.forEach((key, value) -> {
            if (value == null) {
                System.clearProperty(key);
            }
            else {
                System.setProperty(key, value);
            }
        });
    }

    @Test
    void reusesExistingMemberWithoutCreatingAnotherInstance() {
        var configuration = configuration(new MockEnvironment(), Optional.empty(), new ServerProperties(), mock(EurekaInstanceHelper.class));
        HazelcastInstance instance = mock(HazelcastInstance.class);
        try (var hazelcast = mockStatic(Hazelcast.class)) {
            hazelcast.when(() -> Hazelcast.getHazelcastInstanceByName("test-instance")).thenReturn(instance);
            assertThat(configuration.hazelcastInstance(new ArtemisProperties())).isSameAs(instance);
            hazelcast.verify(() -> Hazelcast.newHazelcastInstance(any(Config.class)), org.mockito.Mockito.never());
        }
    }

    @Test
    void standaloneMemberUsesLoopbackAndConfiguresDurableCaches() {
        MockEnvironment env = new MockEnvironment().withProperty("artemis.continuous-integration.build-agent.display-name", "Named agent");
        env.setActiveProfiles("core", "localci");
        var configuration = configuration(env, Optional.empty(), new ServerProperties(), mock(EurekaInstanceHelper.class));
        Config config = captureMemberConfig(configuration);
        assertThat(config.getMemberAttributeConfig().getAttribute("instanceId")).isEqualTo("Named agent");
        assertThat(config.getNetworkConfig().getInterfaces().getInterfaces()).containsExactly("127.0.0.1");
        assertThat(config.getNetworkConfig().getJoin().getMulticastConfig().isEnabled()).isFalse();
        assertThat(config.getNetworkConfig().getJoin().getTcpIpConfig().isEnabled()).isTrue();
        assertThat(config.getProperty("hazelcast.socket.bind.any")).isEqualTo("false");
        assertThat(config.getMapConfig("nodeMetrics").getBackupCount()).isZero();
        assertThat(config.getMapConfig("nodeMetrics").getTimeToLiveSeconds()).isEqualTo(60);
        assertThat(config.getMapConfig("atlas-orchestrator-runs").getTimeToLiveSeconds()).isEqualTo(1800);
        assertThat(config.getQueueConfig("buildJobQueue").getPriorityComparatorClassName()).endsWith("LocalCIPriorityQueueComparator");
    }

    @Test
    void registeredMemberPublishesActualAddressAndUsesConfiguredPort() throws Exception {
        Registration registration = mock(Registration.class);
        when(registration.getServiceId()).thenReturn("artemis");
        EurekaInstanceHelper helper = mock(EurekaInstanceHelper.class);
        MockEnvironment env = new MockEnvironment().withProperty("eureka.instance.instanceId", "registry-name")
                .withProperty("artemis.continuous-integration.build-agent.display-name", "Unnamed Artemis Node");
        env.setActiveProfiles("core", "buildagent");
        var configuration = configuration(env, Optional.of(registration), new ServerProperties(), helper);
        ReflectionTestUtils.setField(configuration, "hazelcastInterface", "192.0.2.5");
        ReflectionTestUtils.setField(configuration, "hazelcastLocalInstances", false);
        HazelcastInstance instance = mock(HazelcastInstance.class);
        Cluster cluster = mock(Cluster.class);
        Member member = mock(Member.class);
        when(instance.getCluster()).thenReturn(cluster);
        when(cluster.getLocalMember()).thenReturn(member);
        when(member.getAddress()).thenReturn(new Address("192.0.2.5", 5701));
        Config config = captureMemberConfig(configuration, instance);
        assertThat(config.getClusterName()).isEqualTo("prod");
        assertThat(config.getNetworkConfig().getPort()).isEqualTo(5701);
        assertThat(config.getNetworkConfig().getInterfaces().getInterfaces()).containsExactly("192.0.2.5");
        assertThat(config.getMemberAttributeConfig().getAttribute("instanceId")).isEqualTo("registry-name");
        assertThat(config.isLiteMember()).isFalse();
        verify(helper).registerHazelcastAddress("192.0.2.5", 5701);
    }

    @Test
    void missingActualAddressFallsBackToRegistrationAndDerivedLocalPort() {
        Registration registration = mock(Registration.class);
        when(registration.getHost()).thenReturn("registered-host");
        EurekaInstanceHelper helper = mock(EurekaInstanceHelper.class);
        ServerProperties serverProperties = new ServerProperties();
        serverProperties.setPort(9000);
        var configuration = configuration(new MockEnvironment(), Optional.of(registration), serverProperties, helper);
        Config config = captureMemberConfig(configuration);
        assertThat(config.getNetworkConfig().getPort()).isEqualTo(14701);
        verify(helper).registerHazelcastAddress("registered-host", 14701);
        serverProperties.setPort(null);
        ReflectionTestUtils.setField(configuration, "hazelcastInterface", "192.0.2.6");
        captureMemberConfig(configuration);
        verify(helper).registerHazelcastAddress("192.0.2.6", 13781);
    }

    @Test
    void standaloneBuildAgentCreatesAsynchronousDiscoveringClient() {
        MockEnvironment env = new MockEnvironment().withProperty("artemis.continuous-integration.build-agent.short-name", "agent-one");
        env.setActiveProfiles("buildagent");
        var configuration = configuration(env, Optional.empty(), new ServerProperties(), mock(EurekaInstanceHelper.class));
        ReflectionTestUtils.setField(configuration, "hazelcastLocalInstances", false);
        HazelcastInstance instance = mock(HazelcastInstance.class);
        try (var clients = mockStatic(HazelcastClient.class)) {
            clients.when(() -> HazelcastClient.newHazelcastClient(any(ClientConfig.class))).thenAnswer(call -> {
                ClientConfig config = call.getArgument(0);
                assertThat(config.getInstanceName()).isEqualTo("agent-one");
                assertThat(config.getClusterName()).isEqualTo("prod");
                assertThat(config.getConnectionStrategyConfig().isAsyncStart()).isTrue();
                assertThat(config.getNetworkConfig().getClusterRoutingConfig().getRoutingMode()).isEqualTo(RoutingMode.ALL_MEMBERS);
                assertThat(config.getProperty("hazelcast.discovery.enabled")).isEqualTo("true");
                assertThat(config.getNetworkConfig().getDiscoveryConfig().getDiscoveryStrategyConfigs()).hasSize(1);
                assertThat(config.getProperty("hazelcast.client.heartbeat.timeout")).isEqualTo("15000");
                return instance;
            });
            assertThat(configuration.hazelcastInstance(new ArtemisProperties())).isSameAs(instance);
        }
    }

    @Test
    void isolatedTestsDisableAllDiscoveryAndUseUniqueClusterAndPort() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test", "buildagent", Constants.PROFILE_TEST_BUILDAGENT);
        var configuration = configuration(env, Optional.empty(), new ServerProperties(), mock(EurekaInstanceHelper.class));
        Config first = captureMemberConfig(configuration);
        Config second = captureMemberConfig(configuration);
        assertThat(first.getClusterName()).startsWith("test-cluster-").isNotEqualTo(second.getClusterName());
        assertThat(first.getNetworkConfig().getPort()).isPositive();
        assertThat(first.getNetworkConfig().isPortAutoIncrement()).isFalse();
        assertThat(first.getNetworkConfig().getJoin().getTcpIpConfig().isEnabled()).isFalse();
        assertThat(first.getNetworkConfig().getJoin().getMulticastConfig().isEnabled()).isFalse();
        assertThat(first.getNetworkConfig().getJoin().getAutoDetectionConfig().isEnabled()).isFalse();
        assertThat(first.getProperty("hazelcast.discovery.enabled")).isEqualTo("false");
    }

    @Test
    void metricsBindingSkipsInternalMapsAndSurvivesUnsupportedMetrics() {
        var registry = new SimpleMeterRegistry();
        var configuration = new HazelcastConfiguration(mock(ApplicationContext.class), new ServerProperties(), Optional.empty(), mock(EurekaInstanceHelper.class),
                new MockEnvironment(), Optional.of(registry));
        ReflectionTestUtils.setField(configuration, "instanceName", "test-instance");
        HazelcastInstance instance = mock(HazelcastInstance.class);
        DistributedObject internal = distributedObject("__internal");
        DistributedObject defaultMap = distributedObject("default");
        DistributedObject nodeMetrics = distributedObject("nodeMetrics");
        DistributedObject queue = distributedObject("queue");
        IMap<?, ?> brokenMap = mock(IMap.class);
        when(brokenMap.getName()).thenReturn("broken");
        when(brokenMap.getLocalMapStats()).thenThrow(new UnsupportedOperationException("stats disabled"));
        when(instance.getDistributedObjects()).thenReturn(Set.of(internal, defaultMap, nodeMetrics, queue, brokenMap));
        try (var hazelcast = mockStatic(Hazelcast.class)) {
            configuration.bindMetricsToMicrometer();
            hazelcast.when(() -> Hazelcast.getHazelcastInstanceByName("test-instance")).thenReturn(instance);
            configuration.bindMetricsToMicrometer();
            assertThat(registry.getMeters()).isEmpty();
        }
    }

    private DistributedObject distributedObject(String name) {
        var object = mock(DistributedObject.class);
        when(object.getName()).thenReturn(name);
        return object;
    }

    private HazelcastConfiguration configuration(MockEnvironment env, Optional<Registration> registration, ServerProperties server, EurekaInstanceHelper helper) {
        var configuration = new HazelcastConfiguration(mock(ApplicationContext.class), server, registration, helper, env, Optional.empty());
        ReflectionTestUtils.setField(configuration, "instanceName", "test-instance");
        ReflectionTestUtils.setField(configuration, "hazelcastPort", 5701);
        ReflectionTestUtils.setField(configuration, "hazelcastInterface", "");
        ReflectionTestUtils.setField(configuration, "hazelcastLocalInstances", true);
        return configuration;
    }

    private Config captureMemberConfig(HazelcastConfiguration configuration) {
        return captureMemberConfig(configuration, mock(HazelcastInstance.class));
    }

    private Config captureMemberConfig(HazelcastConfiguration configuration, HazelcastInstance instance) {
        var config = new java.util.concurrent.atomic.AtomicReference<Config>();
        try (var hazelcast = mockStatic(Hazelcast.class)) {
            hazelcast.when(() -> Hazelcast.newHazelcastInstance(any(Config.class))).thenAnswer(call -> {
                config.set(call.getArgument(0));
                return instance;
            });
            assertThat(configuration.hazelcastInstance(new ArtemisProperties())).isSameAs(instance);
        }
        return config.get();
    }
}
