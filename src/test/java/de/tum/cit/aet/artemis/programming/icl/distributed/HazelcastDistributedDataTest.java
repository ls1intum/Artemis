package de.tum.cit.aet.artemis.programming.icl.distributed;

import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.hazelcast.HazelcastDistributedDataProviderService;

class HazelcastDistributedDataTest extends AbstractDistributedDataTest {

    @Autowired
    private HazelcastDistributedDataProviderService distributedDataProvider;

    @Override
    protected DistributedDataProvider getDistributedDataProvider() {
        return distributedDataProvider;
    }
}
