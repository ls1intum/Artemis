package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentStatus;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalQueue;

/**
 * Covers which build agents a core node reports as connected.
 */
class DistributedDataAccessServiceTest {

    private DistributedDataProvider distributedDataProvider;

    private DistributedMap<String, BuildAgentInformation> buildAgentInformation;

    private DistributedDataAccessService distributedDataAccessService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        distributedDataProvider = mock(DistributedDataProvider.class);
        buildAgentInformation = mock(DistributedMap.class);
        DistributedMap<String, BuildJobQueueItem> processingJobs = mock(DistributedMap.class);
        when(processingJobs.values()).thenReturn(List.of());
        when(distributedDataProvider.getMap(anyString())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0);
            return "buildAgentInformation".equals(name) ? buildAgentInformation : processingJobs;
        });
        distributedDataAccessService = new DistributedDataAccessService(Optional.of(distributedDataProvider));
    }

    private static BuildAgentInformation agent(String name, String memberAddress) {
        return new BuildAgentInformation(new BuildAgentDTO(name, memberAddress, name), 4, 0, List.of(), BuildAgentStatus.IDLE, "", null, 0);
    }

    @Test
    void shouldIgnoreAnUnrelatedClientMatchingAnAgentShortName() {
        when(buildAgentInformation.values()).thenReturn(List.of(agent("artemis-build-agent-1", "artemis-node-2")));
        when(distributedDataProvider.getClusterMemberAddresses()).thenReturn(Set.of("artemis-build-agent-1"));

        assertThat(distributedDataAccessService.getBuildAgentInformation()).isEmpty();
    }

    @Test
    void shouldReportAgentWhoseMemberAddressIsTheConnectedClientName() {
        // The Redis provider reports spring.data.redis.client-name, which is what the agent stored as its member
        // address. Its short name never appears in that set.
        when(buildAgentInformation.values()).thenReturn(List.of(agent("artemis-build-agent-1", "artemis-node-3")));
        when(distributedDataProvider.getClusterMemberAddresses()).thenReturn(Set.of("artemis-node-1", "artemis-node-2", "artemis-node-3"));

        assertThat(distributedDataAccessService.getBuildAgentInformation()).hasSize(1);
    }

    @Test
    void shouldHideAgentThatMatchesNeitherNamespace() {
        when(buildAgentInformation.values()).thenReturn(List.of(agent("artemis-build-agent-9", "artemis-node-9")));
        when(distributedDataProvider.getClusterMemberAddresses()).thenReturn(Set.of("artemis-node-1"));

        assertThat(distributedDataAccessService.getBuildAgentInformation()).isEmpty();
    }

    @Test
    void shouldReportEveryAgentWhenConnectivityCannotBeDetermined() {
        // An empty set means the provider could not tell (a build agent asking, or a failed lookup). Hiding every agent
        // there would make a healthy cluster look like it has no build capacity at all.
        when(buildAgentInformation.values()).thenReturn(List.of(agent("artemis-build-agent-1", "artemis-node-3")));
        when(distributedDataProvider.getClusterMemberAddresses()).thenReturn(Set.of());

        assertThat(distributedDataAccessService.getBuildAgentInformation()).hasSize(1);
    }

    @Test
    void shouldSkipEntriesWithoutAgentDetails() {
        when(buildAgentInformation.values()).thenReturn(java.util.Collections.singletonList(null));
        when(distributedDataProvider.getClusterMemberAddresses()).thenReturn(Set.of("artemis-node-1"));

        assertThat(distributedDataAccessService.getBuildAgentInformation()).isEmpty();
    }

    @Test
    void resultListingAndCountIncludeDeferredResults() {
        var ordinary = new LocalQueue<ResultQueueItem>(new LinkedBlockingQueue<>(), "buildResultQueue");
        var deferred = new LocalQueue<ResultQueueItem>(new LinkedBlockingQueue<>(), "deferredBuildResultQueue");
        when(distributedDataProvider.<ResultQueueItem>getQueue("buildResultQueue")).thenReturn(ordinary);
        when(distributedDataProvider.<ResultQueueItem>getQueue("deferredBuildResultQueue")).thenReturn(deferred);
        ResultQueueItem first = mock(ResultQueueItem.class);
        ResultQueueItem second = mock(ResultQueueItem.class);
        ordinary.add(first);
        deferred.add(second);

        assertThat(distributedDataAccessService.getBuildResultQueue()).containsExactly(first, second);
        assertThat(distributedDataAccessService.getResultQueueSize()).isEqualTo(2);
    }

}
