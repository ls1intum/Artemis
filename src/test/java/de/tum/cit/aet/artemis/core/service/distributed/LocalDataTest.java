package de.tum.cit.aet.artemis.core.service.distributed;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;

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
