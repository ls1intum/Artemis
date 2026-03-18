package de.tum.cit.aet.artemis.core.service.distributed;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.hazelcast.HazelcastDistributedDataProviderService;

@TestPropertySource(properties = { "artemis.continuous-integration.data-store=Hazelcast" })
class HazelcastDistributedDataTest extends AbstractDistributedDataTest {

    @Autowired
    private HazelcastDistributedDataProviderService distributedDataProvider;

    @Override
    protected DistributedDataProvider getDistributedDataProvider() {
        return distributedDataProvider;
    }
}
