package de.tum.cit.aet.artemis.programming.icl.distributed;

import de.tum.cit.aet.artemis.localci.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.localci.service.distributed.local.LocalDataProviderService;

class LocalDataTest extends AbstractDistributedDataTest {

    private final LocalDataProviderService distributedDataProvider;

    public LocalDataTest() {
        this.distributedDataProvider = new LocalDataProviderService();
    }

    @Override
    protected DistributedDataProvider getDistributedDataProvider() {
        return distributedDataProvider;
    }
}
