package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.netflix.eureka.serviceregistry.EurekaRegistration;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;

class EurekaInstanceHelperTest {

    @Test
    void unregisteredNodesHaveNoDiscoveryButCanStillFormatAddresses() {
        var helper = helper(mock(DiscoveryClient.class), Optional.empty(), new MockEnvironment());
        assertThat(helper.getServiceId()).isEmpty();
        assertThat(helper.getServiceInstances()).isEmpty();
        assertThat(helper.discoverCoreNodeAddresses()).isEmpty();
        assertThat(helper.isCurrentInstance(instance("host", 8080, Map.of()))).isFalse();
        assertThat(helper.normalizeHost(null)).isNull();
        assertThat(helper.normalizeHost("[::1]")).isEqualTo("::1");
        assertThat(helper.normalizeHost("[unfinished")).isEqualTo("[unfinished");
        assertThat(helper.formatAddressForHazelcast("::1", "5701")).isEqualTo("[::1]:5701");
        assertThat(helper.formatAddressForHazelcast("127.0.0.1", "5701")).isEqualTo("127.0.0.1:5701");
        assertThatCode(() -> helper.registerHazelcastAddress("127.0.0.1", 5701)).doesNotThrowAnyException();
    }

    @Test
    void discoveryExcludesSelfAndClientNodesAndUsesAdvertisedHazelcastMetadata() {
        Registration own = registration("127.0.0.1", 8080);
        DiscoveryClient discovery = mock(DiscoveryClient.class);
        var self = instance("127.0.0.1", 8080, Map.of("profile", "core"));
        var core = instance("127.0.0.2", 8081, Map.of("profile", "core,localci", "hazelcast.host", "[::1]", "hazelcast.port", "5702"));
        var client = instance("127.0.0.3", 8082, Map.of("profile", "buildagent"));
        var unknown = instance("127.0.0.4", 8083, Map.of());
        when(discovery.getInstances("artemis")).thenReturn(List.of(self, core, client, unknown));
        var helper = helper(discovery, Optional.of(own), new MockEnvironment());
        assertThat(helper.getServiceId()).contains("artemis");
        assertThat(helper.getServiceInstances()).containsExactly(self, core, client, unknown);
        assertThat(helper.discoverCoreNodeAddresses()).containsExactly("[::1]:5702");
        assertThat(helper.formatInstanceAddress(client)).isEqualTo("127.0.0.3:5701");
        client.getMetadata().put("hazelcast.host", "");
        assertThat(helper.getHazelcastHost(client)).isEqualTo("127.0.0.3");
        when(discovery.getInstances("artemis")).thenReturn(List.of(client));
        assertThat(helper.discoverCoreNodeAddresses()).isEmpty();
    }

    @Test
    void identifiesOwnInstanceByNormalizedHostOrResolvedAddressWithMatchingPort() throws Exception {
        Registration own = registration("[::1]", 8080);
        var helper = helper(mock(DiscoveryClient.class), Optional.of(own), new MockEnvironment());
        assertThat(helper.isCurrentInstance(instance("::1", 8080, Map.of()))).isTrue();
        assertThat(helper.isCurrentInstance(instance("::1", 8081, Map.of()))).isFalse();
        when(own.getHost()).thenReturn("own-host");
        InetAddress same = InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 });
        InetAddress other = InetAddress.getByAddress(new byte[] { 127, 0, 0, 2 });
        try (var addresses = mockStatic(InetAddress.class)) {
            addresses.when(() -> InetAddress.getByName("own-host")).thenReturn(same);
            addresses.when(() -> InetAddress.getByName("same-host")).thenReturn(same);
            addresses.when(() -> InetAddress.getByName("other-host")).thenReturn(other);
            addresses.when(() -> InetAddress.getByName("unknown-host")).thenThrow(new UnknownHostException("unresolvable"));
            assertThat(helper.isCurrentInstance(instance("same-host", 8080, Map.of()))).isTrue();
            assertThat(helper.isCurrentInstance(instance("other-host", 8080, Map.of()))).isFalse();
            assertThat(helper.isCurrentInstance(instance("unknown-host", 8080, Map.of()))).isFalse();
            assertThat(helper.isCurrentInstance(instance(null, 8080, Map.of()))).isFalse();
            when(own.getHost()).thenReturn(null);
            assertThat(helper.isCurrentInstance(instance("same-host", 8080, Map.of()))).isFalse();
        }
    }

    @Test
    void onlyStandaloneBuildAgentProfilesUseClientMode() {
        MockEnvironment environment = new MockEnvironment();
        var helper = helper(mock(DiscoveryClient.class), Optional.empty(), environment);
        assertThat(helper.isRunningAsClient()).isFalse();
        environment.setActiveProfiles("buildagent");
        assertThat(helper.isRunningAsClient()).isTrue();
        environment.setActiveProfiles("buildagent", "core");
        assertThat(helper.isRunningAsClient()).isFalse();
        environment.setActiveProfiles("buildagent", Constants.PROFILE_TEST_BUILDAGENT);
        assertThat(helper.isRunningAsClient()).isFalse();
    }

    @Test
    void advertisesMetadataWithoutARegistryAndUpdatesTheLiveSdkCopyOnEveryChange() {
        Registration registration = registration("127.0.0.1", 8080);
        var helper = helper(mock(DiscoveryClient.class), Optional.of(registration), new MockEnvironment());
        helper.registerHazelcastAddress("192.0.2.8", 5703);
        assertThat(registration.getMetadata()).containsEntry("hazelcast.host", "192.0.2.8").containsEntry("hazelcast.port", "5703");
        assertThatCode(() -> helper.registerHazelcastAddress("192.0.2.9", 5704)).doesNotThrowAnyException();
        assertThat(registration.getMetadata()).containsEntry("hazelcast.host", "192.0.2.9").containsEntry("hazelcast.port", "5704");

        EurekaRegistration eureka = mock(EurekaRegistration.class);
        var metadata = new HashMap<>(Map.of("profile", "core", "zone", "existing"));
        InstanceInfo info = InstanceInfo.Builder.newBuilder().setAppName("artemis").setHostName("core").setMetadata(new HashMap<>(metadata)).build();
        var manager = new ApplicationInfoManager(mock(EurekaInstanceConfig.class), info);
        when(eureka.getMetadata()).thenReturn(metadata);
        when(eureka.getApplicationInfoManager()).thenReturn(manager);
        var sdkHelper = helper(mock(DiscoveryClient.class), Optional.of(eureka), new MockEnvironment());
        for (int port : List.of(5703, 5704)) {
            info.setIsDirty(false);
            sdkHelper.registerHazelcastAddress("192.0.2.10", port);
            assertThat(metadata).containsEntry("hazelcast.host", "192.0.2.10").containsEntry("hazelcast.port", String.valueOf(port));
            assertThat(info.getMetadata()).containsEntry("hazelcast.host", "192.0.2.10").containsEntry("hazelcast.port", String.valueOf(port)).containsEntry("profile", "core")
                    .containsEntry("zone", "existing");
            assertThat(info.isDirty()).isTrue();
        }
    }

    private EurekaInstanceHelper helper(DiscoveryClient discovery, Optional<Registration> registration, MockEnvironment environment) {
        var helper = new EurekaInstanceHelper(discovery, registration, environment);
        ReflectionTestUtils.setField(helper, "hazelcastPort", 5701);
        return helper;
    }

    private Registration registration(String host, int port) {
        Registration registration = mock(Registration.class);
        when(registration.getHost()).thenReturn(host);
        when(registration.getPort()).thenReturn(port);
        when(registration.getServiceId()).thenReturn("artemis");
        when(registration.getMetadata()).thenReturn(new HashMap<>());
        return registration;
    }

    private DefaultServiceInstance instance(String host, int port, Map<String, String> metadata) {
        return new DefaultServiceInstance("instance", "artemis", host, port, false, new HashMap<>(metadata));
    }
}
