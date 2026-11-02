package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import com.hazelcast.cluster.Address;
import com.hazelcast.cluster.Cluster;
import com.hazelcast.cluster.Member;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

class HazelcastClusterManagerTest {

    @Test
    void skipsClientsUnregisteredInstancesAndTestEnvironments() {
        EurekaInstanceHelper helper = mock(EurekaInstanceHelper.class);
        MockEnvironment environment = new MockEnvironment();
        var manager = manager(helper, environment);
        try (var hazelcast = mockStatic(Hazelcast.class)) {
            when(helper.isRunningAsClient()).thenReturn(true);
            manager.connectToAllMembers();
            when(helper.isRunningAsClient()).thenReturn(false);
            when(helper.getServiceId()).thenReturn(Optional.empty());
            manager.connectToAllMembers();
            when(helper.getServiceId()).thenReturn(Optional.of("artemis"));
            environment.setActiveProfiles("test");
            manager.connectToAllMembers();
            hazelcast.verifyNoInteractions();
        }
    }

    @Test
    void missingInstanceAndDiscoveryFailureDoNotStopScheduledReconciliation() {
        EurekaInstanceHelper helper = mock(EurekaInstanceHelper.class);
        when(helper.getServiceId()).thenReturn(Optional.of("artemis"));
        var manager = manager(helper, new MockEnvironment());
        try (var hazelcast = mockStatic(Hazelcast.class)) {
            assertThatCode(manager::connectToAllMembers).doesNotThrowAnyException();
            when(helper.getServiceId()).thenThrow(new IllegalStateException("registry down"));
            assertThatCode(manager::connectToAllMembers).doesNotThrowAnyException();
            hazelcast.verify(() -> Hazelcast.getHazelcastInstanceByName("test-instance"));
        }
    }

    @Test
    void onlyAddsNewRegistryMembersAndResolvesEquivalentLoopbackAddresses() throws Exception {
        EurekaInstanceHelper helper = mock(EurekaInstanceHelper.class);
        when(helper.getServiceId()).thenReturn(Optional.of("artemis"));
        when(helper.formatAddressForHazelcast(anyString(), anyString())).thenAnswer(call -> "[" + call.getArgument(0) + "]:" + call.getArgument(1));
        ServiceInstance same = registryMember(helper, "[127.0.0.1]:5701");
        ServiceInstance alias = registryMember(helper, "[127.0.0.1]:5702");
        ServiceInstance fresh = registryMember(helper, "[127.0.0.1]:5703");
        ServiceInstance client = mock(ServiceInstance.class);
        when(helper.getServiceInstances()).thenReturn(List.of(same, alias, fresh, client));
        HazelcastInstance instance = mock(HazelcastInstance.class);
        Cluster cluster = mock(Cluster.class);
        when(instance.getCluster()).thenReturn(cluster);
        Set<Member> clusterMembers = Set.of(member("127.0.0.1", 5701), member("localhost", 5702));
        when(cluster.getMembers()).thenReturn(clusterMembers);
        Config config = new Config();
        when(instance.getConfig()).thenReturn(config);
        var manager = manager(helper, new MockEnvironment());
        InetAddress loopback = InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 });
        try (var hazelcast = mockStatic(Hazelcast.class); var addresses = mockStatic(InetAddress.class)) {
            addresses.when(() -> InetAddress.getByName("localhost")).thenReturn(loopback);
            addresses.when(() -> InetAddress.getByName("127.0.0.1")).thenReturn(loopback);
            hazelcast.when(() -> Hazelcast.getHazelcastInstanceByName("test-instance")).thenReturn(instance);
            manager.connectToAllMembers();
            assertThat(config.getNetworkConfig().getJoin().getTcpIpConfig().getMembers()).containsExactly("[127.0.0.1]:5703");
            verify(helper, never()).formatInstanceAddress(client);
        }
    }

    @Test
    void staleMembershipCheckHandlesAbsentOwnRegistrationAndUnresolvableAddresses() {
        EurekaInstanceHelper helper = mock(EurekaInstanceHelper.class);
        when(helper.getServiceId()).thenReturn(Optional.of("artemis"));
        when(helper.formatAddressForHazelcast(anyString(), anyString())).thenAnswer(call -> "[" + call.getArgument(0) + "]:" + call.getArgument(1));
        ServiceInstance registered = registryMember(helper, "address-without-port");
        when(helper.getServiceInstances()).thenReturn(List.of(registered));
        HazelcastInstance instance = mock(HazelcastInstance.class);
        Cluster cluster = mock(Cluster.class);
        when(instance.getCluster()).thenReturn(cluster);
        Set<Member> clusterMembers = Set.of(member("127.0.0.1", 5701));
        when(cluster.getMembers()).thenReturn(clusterMembers);
        Config config = new Config();
        when(instance.getConfig()).thenReturn(config);
        var manager = manager(helper, new MockEnvironment());
        ReflectionTestUtils.setField(manager, "startupTime", Instant.now().minusSeconds(120));
        try (var hazelcast = mockStatic(Hazelcast.class)) {
            hazelcast.when(() -> Hazelcast.getHazelcastInstanceByName("test-instance")).thenReturn(instance);
            assertThatCode(manager::connectToAllMembers).doesNotThrowAnyException();
            assertThat(config.getNetworkConfig().getJoin().getTcpIpConfig().getMembers()).containsExactly("address-without-port");
            when(helper.isCurrentInstance(registered)).thenReturn(true);
            when(helper.formatInstanceAddress(registered)).thenReturn("[127.0.0.1]:5701");
            manager.connectToAllMembers();
            when(helper.formatInstanceAddress(registered)).thenReturn("[localhost]:5701");
            manager.connectToAllMembers();
            assertThat(config.getNetworkConfig().getJoin().getTcpIpConfig().getMembers()).containsExactly("address-without-port");
        }
    }

    private HazelcastClusterManager manager(EurekaInstanceHelper helper, MockEnvironment environment) {
        var manager = new HazelcastClusterManager(helper, environment);
        ReflectionTestUtils.setField(manager, "instanceName", "test-instance");
        return manager;
    }

    private ServiceInstance registryMember(EurekaInstanceHelper helper, String address) {
        ServiceInstance instance = mock(ServiceInstance.class);
        when(helper.isClusterMember(instance)).thenReturn(true);
        when(helper.formatInstanceAddress(instance)).thenReturn(address);
        return instance;
    }

    private Member member(String host, int port) {
        Member member = mock(Member.class);
        Address address = mock(Address.class);
        when(address.getHost()).thenReturn(host);
        when(address.getPort()).thenReturn(port);
        when(member.getAddress()).thenReturn(address);
        return member;
    }
}
