package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import com.hazelcast.core.HazelcastInstance;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.hazelcast.HazelcastDistributedDataProviderService;

final class HyperionDistributedDataTestProvider {

    private HyperionDistributedDataTestProvider() {
    }

    static DistributedDataProvider provider(HazelcastInstance hazelcastInstance) {
        var provider = new HazelcastDistributedDataProviderService(hazelcastInstance);
        // These fixtures represent an initialized cluster without pending database recovery.
        new GenerationRecoveryBootstrapService(org.mockito.Mockito.mock(de.tum.cit.aet.artemis.hyperion.test_repository.AuthoringRunTestRepository.class), provider).initialize();
        return provider;
    }
}
