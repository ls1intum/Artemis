package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

class RedissonProviderLifecycleTest {

    @Test
    void connectionStateCallbacksAreImmediateOnlyWhileRunningAndRemovalIsIdempotent() {
        var client = mock(RedissonClient.class);
        var service = new RedissonDistributedDataProviderService(client, mock(RedisClientListResolver.class));
        ReflectionTestUtils.setField(service, "redisClientName", "artemis-agent");
        List<Boolean> events = new ArrayList<>();
        UUID listener = service.addConnectionStateListener(events::add);
        service.addConnectionStateListener(_ -> {
            throw new IllegalStateException("callback failed");
        });
        assertThat(events).containsExactly(true);
        assertThat(service.removeConnectionStateListener(listener)).isTrue();
        assertThat(service.removeConnectionStateListener(listener)).isFalse();
        assertThat(service.getLocalMemberAddress()).isEqualTo("artemis-agent");
        assertThat(service.isConnectedToCluster()).isTrue();
        assertThat(service.noDataMemberInClusterAvailable()).isFalse();
        assertThat(service.clientsConnectDirectlyToCoreNodes()).isFalse();
        assertThat(service.buildAgentsAppearInClusterMemberList()).isTrue();
        when(client.isShuttingDown()).thenReturn(true);
        assertThat(service.isInstanceRunning()).isFalse();
        service.addConnectionStateListener(events::add);
        assertThat(events).containsExactly(true);
        when(client.isShutdown()).thenReturn(true);
        assertThat(service.noDataMemberInClusterAvailable()).isTrue();
    }

    @Test
    void completeMembershipSnapshotIsResolvedOnceAndIncompleteSnapshotsNeverHideOtherNodes() {
        var resolver = mock(RedisClientListResolver.class);
        var service = new RedissonDistributedDataProviderService(mock(RedissonClient.class), resolver);
        var observed = Map.of("artemis-core", Set.of("192.0.2.1"), "artemis-agent", Set.of("192.0.2.2"));
        when(resolver.resolveClients()).thenReturn(new RedisClientListResolver.ClientListSnapshot(observed, true));
        assertThat(service.getClusterMemberAddresses()).containsExactlyInAnyOrder("artemis-core", "artemis-agent");
        assertThat(service.getConnectedClientNames()).containsExactlyInAnyOrder("artemis-core", "artemis-agent");
        var membership = service.getClusterMembership();
        assertThat(membership.clusterMemberAddresses()).containsExactlyInAnyOrder("artemis-core", "artemis-agent");
        assertThat(membership.connectedClientNames()).containsExactlyInAnyOrder("artemis-core", "artemis-agent");
        when(resolver.resolveClients()).thenReturn(new RedisClientListResolver.ClientListSnapshot(observed, false));
        assertThat(service.getClusterMemberAddresses()).isEmpty();
        assertThat(service.getConnectedClientNames()).isEmpty();
        assertThat(service.getClusterMembership().clusterMemberAddresses()).isEmpty();
    }

    @Test
    void pollingRequiresCompleteSnapshotAndNotifiesDisconnectionsOnceDespiteFailingListener() throws Exception {
        var client = mock(RedissonClient.class);
        var resolver = mock(RedisClientListResolver.class);
        when(resolver.getUniqueClients()).thenReturn(Set.of("artemis-core", "artemis-agent"));
        var executor = mock(ScheduledExecutorService.class);
        var future = mock(ScheduledFuture.class);
        org.mockito.Mockito.doReturn(future).when(executor).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.SECONDS));
        when(executor.awaitTermination(5, TimeUnit.SECONDS)).thenReturn(true);
        var service = new RedissonDistributedDataProviderService(client, resolver);
        try (var executors = mockStatic(Executors.class)) {
            executors.when(() -> Executors.newSingleThreadScheduledExecutor(any(ThreadFactory.class))).thenReturn(executor);
            List<String> disconnected = new ArrayList<>();
            UUID first = service.addClientDisconnectionListener(disconnected::add);
            UUID second = service.addClientDisconnectionListener(_ -> {
                throw new IllegalStateException("listener failed");
            });
            var task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
            verify(executor).scheduleAtFixedRate(task.capture(), eq(5L), eq(5L), eq(TimeUnit.SECONDS));
            when(resolver.resolveClients()).thenReturn(new RedisClientListResolver.ClientListSnapshot(Map.of(), false));
            task.getValue().run();
            assertThat(disconnected).isEmpty();
            when(resolver.resolveClients()).thenReturn(new RedisClientListResolver.ClientListSnapshot(Map.of("artemis-core", Set.of()), true));
            task.getValue().run();
            task.getValue().run();
            assertThat(disconnected).containsExactly("artemis-agent");
            when(resolver.resolveClients()).thenThrow(new IllegalStateException("redis unavailable"));
            task.getValue().run();
            assertThat(service.removeClientDisconnectionListener(first)).isTrue();
            assertThat(service.removeClientDisconnectionListener(first)).isFalse();
            assertThat(service.removeClientDisconnectionListener(second)).isTrue();
            verify(future).cancel(false);
            verify(executor).shutdown();
            task.getValue().run();
            service.destroy();
        }
    }

    @Test
    void pollingShutdownEscalatesWhenExecutorDoesNotTerminate() throws Exception {
        var resolver = mock(RedisClientListResolver.class);
        when(resolver.getUniqueClients()).thenReturn(Set.of());
        var executor = mock(ScheduledExecutorService.class);
        var service = new RedissonDistributedDataProviderService(mock(RedissonClient.class), resolver);
        try (var executors = mockStatic(Executors.class)) {
            executors.when(() -> Executors.newSingleThreadScheduledExecutor(any(ThreadFactory.class))).thenReturn(executor);
            service.addClientDisconnectionListener(_ -> {
            });
            service.destroy();
            verify(executor).shutdownNow();
        }
    }
}
