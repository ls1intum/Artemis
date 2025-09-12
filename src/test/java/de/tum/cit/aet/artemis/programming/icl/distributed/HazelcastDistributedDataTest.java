package de.tum.cit.aet.artemis.programming.icl.distributed;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_TEST_BUILDAGENT;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.hazelcast.HazelcastDistributedDataProviderService;

@ActiveProfiles({ PROFILE_TEST_BUILDAGENT, PROFILE_BUILDAGENT })
@TestPropertySource(properties = { "artemis.continuous-integration.data-store=Hazelcast" })
class HazelcastDistributedDataTest extends AbstractDistributedDataTest {

    @Autowired
    private HazelcastDistributedDataProviderService distributedDataProvider;

    @Override
    protected DistributedDataProvider getDistributedDataProvider() {
        return distributedDataProvider;
    }
}
