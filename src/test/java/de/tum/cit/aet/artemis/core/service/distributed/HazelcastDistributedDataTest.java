package de.tum.cit.aet.artemis.core.service.distributed;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.hazelcast.HazelcastDistributedDataProviderService;

@TestPropertySource(properties = { "artemis.continuous-integration.data-store=Hazelcast" })
class HazelcastDistributedDataTest extends AbstractDistributedDataTest {

    @Autowired
    private HazelcastDistributedDataProviderService distributedDataProvider;

    @Override
    protected boolean clientsReachCoreNodesDirectly() {
        // Hazelcast clients connect to the cluster members, which are the core nodes that serve git
        return true;
    }

    @Override
    protected DistributedDataProvider getDistributedDataProvider() {
        return distributedDataProvider;
    }

    /**
     * Hazelcast takes queue ordering from a comparator statically bound to a queue name, not from the items' natural
     * ordering, so it cannot honour the priority contract for an arbitrary name.
     */
    @Override
    protected boolean supportsPriorityQueueForArbitraryNames() {
        return false;
    }

    @Test
    void testPriorityQueueWithoutConfiguredComparatorFailsFast() {
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> getDistributedDataProvider().getPriorityQueue("queueWithoutPriorityConfig"))
                .withMessageContaining("No Hazelcast priority comparator is configured");
    }
}
