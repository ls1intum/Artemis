package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import com.hazelcast.core.HazelcastInstance;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.hazelcast.HazelcastDistributedDataProviderService;

final class HyperionDistributedDataTestProvider {

    private HyperionDistributedDataTestProvider() {
    }

    static DistributedDataProvider provider(HazelcastInstance hazelcastInstance) {
        return new HazelcastDistributedDataProviderService(hazelcastInstance);
    }
}
