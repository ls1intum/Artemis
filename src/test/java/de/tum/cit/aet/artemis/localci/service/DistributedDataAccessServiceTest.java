package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentStatus;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;

/**
 * Covers which build agents a core node reports as connected.
 *
 * <p>
 * A build agent reaches the cluster in one of three shapes, and each is visible somewhere else: a Hazelcast client under
 * its short name, a Hazelcast cluster member that also runs an agent under its member address, and a Redis node under
 * its configured client name, which is also what it stored as its member address. Matching only the short name against
 * the client list therefore hid the agent on every core node and, under Redis, the whole overview — while those agents
 * were in fact running builds and their capacity silently went missing.
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
        return new BuildAgentInformation(new BuildAgentDTO(name, memberAddress, name), 4, 0, List.of(), BuildAgentStatus.IDLE, "", null, 0, 0, 0);
    }

    @Test
    void shouldReportAgentRunningOnAClusterMember() {
        // A node with both the core and the buildagent profile is a Hazelcast cluster member, not a client, so neither
        // its short name nor its address is in the client list. Only the member list knows it. Missing this case hid an
        // agent that was actively running builds and dropped its capacity from every capacity calculation.
        when(buildAgentInformation.values()).thenReturn(List.of(agent("artemis-build-agent-2", "[127.0.0.1]:5702")));
        when(distributedDataProvider.getConnectedClientNames()).thenReturn(Set.of("artemis-build-agent-3"));
        when(distributedDataProvider.getClusterMemberAddresses()).thenReturn(Set.of("[127.0.0.1]:5701", "[127.0.0.1]:5702"));

        assertThat(distributedDataAccessService.getBuildAgentInformation()).hasSize(1);
    }

    @Test
    void shouldReportAgentWhoseShortNameIsTheConnectedClientName() {
        // Hazelcast names its client after the build agent short name.
        when(buildAgentInformation.values()).thenReturn(List.of(agent("artemis-build-agent-1", "[127.0.0.1]:60287")));
        when(distributedDataProvider.getConnectedClientNames()).thenReturn(Set.of("artemis-build-agent-1"));
        when(distributedDataProvider.getClusterMemberAddresses()).thenReturn(Set.of("[127.0.0.1]:5701"));

        assertThat(distributedDataAccessService.getBuildAgentInformation()).hasSize(1);
    }

    @Test
    void shouldReportAgentWhoseMemberAddressIsTheConnectedClientName() {
        // The Redis provider reports spring.data.redis.client-name, which is what the agent stored as its member
        // address. Its short name never appears in that set.
        when(buildAgentInformation.values()).thenReturn(List.of(agent("artemis-build-agent-1", "artemis-node-3")));
        when(distributedDataProvider.getConnectedClientNames()).thenReturn(Set.of("artemis-node-1", "artemis-node-2", "artemis-node-3"));

        assertThat(distributedDataAccessService.getBuildAgentInformation()).hasSize(1);
    }

    @Test
    void shouldHideAgentThatMatchesNeitherNamespace() {
        when(buildAgentInformation.values()).thenReturn(List.of(agent("artemis-build-agent-9", "artemis-node-9")));
        when(distributedDataProvider.getConnectedClientNames()).thenReturn(Set.of("artemis-node-1"));
        when(distributedDataProvider.getClusterMemberAddresses()).thenReturn(Set.of("artemis-node-1"));

        assertThat(distributedDataAccessService.getBuildAgentInformation()).isEmpty();
    }

    @Test
    void shouldReportEveryAgentWhenConnectivityCannotBeDetermined() {
        // An empty set means the provider could not tell (a build agent asking, or a failed lookup). Hiding every agent
        // there would make a healthy cluster look like it has no build capacity at all.
        when(buildAgentInformation.values()).thenReturn(List.of(agent("artemis-build-agent-1", "artemis-node-3")));
        when(distributedDataProvider.getConnectedClientNames()).thenReturn(Set.of());

        assertThat(distributedDataAccessService.getBuildAgentInformation()).hasSize(1);
    }

    @Test
    void shouldSkipEntriesWithoutAgentDetails() {
        when(buildAgentInformation.values()).thenReturn(java.util.Collections.singletonList(null));
        when(distributedDataProvider.getConnectedClientNames()).thenReturn(Set.of("artemis-node-1"));

        assertThat(distributedDataAccessService.getBuildAgentInformation()).isEmpty();
    }
}
