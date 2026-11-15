package de.tum.cit.aet.artemis.core.service.distributed.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.hazelcast.client.Client;
import com.hazelcast.client.ClientListener;
import com.hazelcast.client.ClientService;
import com.hazelcast.client.impl.clientside.HazelcastClientProxy;
import com.hazelcast.cluster.Address;
import com.hazelcast.cluster.Cluster;
import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceNotActiveException;
import com.hazelcast.core.LifecycleEvent;
import com.hazelcast.core.LifecycleListener;
import com.hazelcast.core.LifecycleService;

class HazelcastDistributedDataProviderServiceTest {

    @Test
    void lifecycleNotificationsDistinguishInitialConnectionFromReconnectAndSurviveBrokenListeners() {
        HazelcastInstance instance = memberInstance();
        var service = new HazelcastDistributedDataProviderService(instance);
        List<Boolean> states = new ArrayList<>();
        UUID listenerId = service.addConnectionStateListener(states::add);
        @SuppressWarnings("unchecked")
        Consumer<Boolean> broken = mock(Consumer.class);
        doThrow(new IllegalStateException("broken consumer")).when(broken).accept(any());
        service.addConnectionStateListener(broken);
        var captor = ArgumentCaptor.forClass(LifecycleListener.class);
        verify(instance.getLifecycleService()).addLifecycleListener(captor.capture());
        LifecycleListener listener = captor.getValue();
        listener.stateChanged(new LifecycleEvent(LifecycleEvent.LifecycleState.STARTING));
        listener.stateChanged(new LifecycleEvent(LifecycleEvent.LifecycleState.STARTED));
        listener.stateChanged(new LifecycleEvent(LifecycleEvent.LifecycleState.CLIENT_DISCONNECTED));
        listener.stateChanged(new LifecycleEvent(LifecycleEvent.LifecycleState.CLIENT_CONNECTED));
        assertThat(states).containsExactly(true, true, false);
        assertThat(service.removeConnectionStateListener(listenerId)).isTrue();
        assertThat(service.removeConnectionStateListener(listenerId)).isFalse();
        listener.stateChanged(new LifecycleEvent(LifecycleEvent.LifecycleState.CLIENT_CONNECTED));
        assertThat(states).containsExactly(true, true, false);
    }

    @Test
    void disconnectedMembersExposeEmptyTopologyAndRejectLocalAddress() {
        HazelcastInstance instance = memberInstance();
        var service = new HazelcastDistributedDataProviderService(instance);
        when(instance.getLifecycleService().isRunning()).thenReturn(false);
        assertThat(service.isInstanceRunning()).isFalse();
        assertThat(service.isConnectedToCluster()).isFalse();
        assertThat(service.getClusterMemberAddresses()).isEmpty();
        assertThat(service.noDataMemberInClusterAvailable()).isTrue();
        assertThat(service.getConnectedClientNames()).isEmpty();
        assertThat(service.getConnectedClientAddresses()).isEmpty();
        assertThatThrownBy(service::getLocalMemberAddress).isInstanceOf(HazelcastInstanceNotActiveException.class);
        List<Boolean> events = new ArrayList<>();
        service.addConnectionStateListener(events::add);
        assertThat(events).isEmpty();
        when(instance.getLifecycleService().isRunning()).thenThrow(new HazelcastInstanceNotActiveException());
        assertThat(service.isInstanceRunning()).isFalse();
    }

    @Test
    void exposesMemberAddressesAndDataMemberAvailability() throws Exception {
        HazelcastInstance instance = memberInstance();
        Member member = mock(Member.class);
        when(member.getAddress()).thenReturn(new Address("127.0.0.1", 5701));
        when(instance.getCluster().getLocalMember()).thenReturn(member);
        when(instance.getCluster().getMembers()).thenReturn(Set.of(member));
        var service = new HazelcastDistributedDataProviderService(instance);
        assertThat(service.getLocalMemberAddress()).isEqualTo("[127.0.0.1]:5701");
        assertThat(service.getClusterMemberAddresses()).containsExactly(member.getAddress().toString());
        assertThat(service.noDataMemberInClusterAvailable()).isFalse();
        when(member.isLiteMember()).thenReturn(true);
        assertThat(service.noDataMemberInClusterAvailable()).isTrue();
        assertThat(service.buildAgentsAppearInClusterMemberList()).isFalse();
        assertThat(service.clientsConnectDirectlyToCoreNodes()).isTrue();
    }

    @Test
    void connectedClientAddressesGroupDuplicateNamesAndIgnoreIncompleteEndpoints() throws Exception {
        HazelcastInstance instance = memberInstance();
        Client first = client("agent", new InetSocketAddress(InetAddress.getByName("192.0.2.1"), 1000));
        Client duplicate = client("agent", new InetSocketAddress(InetAddress.getByName("192.0.2.1"), 2000));
        Client otherHost = client("agent", new InetSocketAddress(InetAddress.getByName("192.0.2.2"), 3000));
        Client noName = client(null, new InetSocketAddress(1000));
        Client noAddress = client("missing", null);
        Client unresolved = client("unresolved", InetSocketAddress.createUnresolved("example.invalid", 1000));
        when(instance.getClientService().getConnectedClients()).thenReturn(List.of(first, duplicate, otherHost, noName, noAddress, unresolved));
        var service = new HazelcastDistributedDataProviderService(instance);
        assertThat(service.getConnectedClientAddresses()).contains(Map.of("agent", Set.of("192.0.2.1", "192.0.2.2")));
        when(instance.getClientService().getConnectedClients()).thenReturn(List.of(first));
        assertThat(service.getConnectedClientNames()).containsExactly("agent");
        when(instance.getClientService().getConnectedClients()).thenThrow(new UnsupportedOperationException());
        assertThat(service.getConnectedClientNames()).isEmpty();
        assertThat(service.getConnectedClientAddresses()).isEmpty();
    }

    @Test
    void clientDisconnectionListenerRegistersOnceAndCanBeRemovedIndependently() {
        HazelcastInstance instance = memberInstance();
        when(instance.getClientService().addClientListener(any())).thenReturn(UUID.randomUUID());
        var service = new HazelcastDistributedDataProviderService(instance);
        List<String> disconnected = new ArrayList<>();
        UUID id = service.addClientDisconnectionListener(disconnected::add);
        service.addClientDisconnectionListener(_ -> {
            throw new IllegalStateException("listener failed");
        });
        var captor = ArgumentCaptor.forClass(ClientListener.class);
        verify(instance.getClientService(), times(1)).addClientListener(captor.capture());
        Client client = client("agent", null);
        captor.getValue().clientConnected(client);
        assertThat(disconnected).isEmpty();
        captor.getValue().clientDisconnected(client);
        assertThat(disconnected).containsExactly("agent");
        assertThat(service.removeClientDisconnectionListener(id)).isTrue();
        assertThat(service.removeClientDisconnectionListener(id)).isFalse();
        captor.getValue().clientDisconnected(client);
        assertThat(disconnected).containsExactly("agent");
    }

    @Test
    void clientConnectionRequiresAtLeastOneMemberAndIgnoresMemberStartedEvents() {
        HazelcastClientProxy client = clientInstance();
        var service = new HazelcastDistributedDataProviderService(client);
        when(client.getCluster().getMembers()).thenReturn(Set.of());
        assertThat(service.isConnectedToCluster()).isFalse();
        List<Boolean> states = new ArrayList<>();
        service.addConnectionStateListener(states::add);
        var captor = ArgumentCaptor.forClass(LifecycleListener.class);
        verify(client.getLifecycleService()).addLifecycleListener(captor.capture());
        captor.getValue().stateChanged(new LifecycleEvent(LifecycleEvent.LifecycleState.STARTED));
        assertThat(states).isEmpty();
        captor.getValue().stateChanged(new LifecycleEvent(LifecycleEvent.LifecycleState.CLIENT_CONNECTED));
        assertThat(states).containsExactly(true);
        when(client.getCluster().getMembers()).thenReturn(Set.of(mock(Member.class)));
        assertThat(service.isConnectedToCluster()).isTrue();
        when(client.getCluster()).thenThrow(new IllegalStateException("not connected"));
        assertThat(service.isConnectedToCluster()).isFalse();
        assertThat(service.getConnectedClientAddresses()).isEmpty();
        assertThat(service.getConnectedClientNames()).isEmpty();
        assertThat(service.addClientDisconnectionListener(_ -> {
        })).isNull();
    }

    @Test
    void clientsUseObservedEndpointOrStableInstanceFallback() throws Exception {
        HazelcastClientProxy client = clientInstance();
        when(client.getName()).thenReturn("agent-one");
        var service = new HazelcastDistributedDataProviderService(client);
        InetAddress localHost = InetAddress.getByAddress("build-agent.test", new byte[] { 127, 0, 0, 1 });
        InetSocketAddress observed = new InetSocketAddress(localHost, 51000);
        Client endpoint = mock(Client.class);
        try (var addresses = org.mockito.Mockito.mockStatic(InetAddress.class)) {
            addresses.when(InetAddress::getLocalHost).thenReturn(localHost);
            assertThat(service.getLocalMemberAddress()).isEqualTo("build-agent.test/agent-one");
            when(client.getLocalEndpoint()).thenReturn(endpoint);
            assertThat(service.getLocalMemberAddress()).isEqualTo("build-agent.test/agent-one");
            when(endpoint.getSocketAddress()).thenReturn(observed);
            assertThat(service.getLocalMemberAddress()).isEqualTo("[127.0.0.1]:51000");
            when(endpoint.getSocketAddress()).thenReturn(null);
            addresses.when(InetAddress::getLocalHost).thenThrow(new java.net.UnknownHostException("no hostname"));
            assertThat(service.getLocalMemberAddress()).isEqualTo("unknown-host/agent-one");
        }
    }

    private HazelcastInstance memberInstance() {
        return initialize(mock(HazelcastInstance.class));
    }

    private HazelcastClientProxy clientInstance() {
        return initialize(mock(HazelcastClientProxy.class));
    }

    private <T extends HazelcastInstance> T initialize(T instance) {
        LifecycleService lifecycle = mock(LifecycleService.class);
        when(instance.getLifecycleService()).thenReturn(lifecycle);
        when(lifecycle.isRunning()).thenReturn(true);
        when(instance.getCluster()).thenReturn(mock(Cluster.class));
        if (!(instance instanceof HazelcastClientProxy)) {
            when(instance.getClientService()).thenReturn(mock(ClientService.class));
        }
        return instance;
    }

    private Client client(String name, InetSocketAddress address) {
        Client client = mock(Client.class);
        when(client.getName()).thenReturn(name);
        when(client.getSocketAddress()).thenReturn(address);
        return client;
    }
}
