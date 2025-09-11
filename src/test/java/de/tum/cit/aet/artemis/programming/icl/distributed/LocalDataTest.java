package de.tum.cit.aet.artemis.programming.icl.distributed;

import org.springframework.test.context.TestPropertySource;

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.local.LocalDataProviderService;

@TestPropertySource(properties = { "artemis.continuous-integration.data-store=Local" })
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
