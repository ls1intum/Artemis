package de.tum.cit.aet.artemis.athena;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVcTest;

/**
 * Base class for Athena tests providing common functionality
 */
public abstract class AbstractAthenaTest extends AbstractSpringIntegrationJenkinsLocalVcTest {

    @Autowired
    protected AthenaRequestMockProvider athenaRequestMockProvider;

    @BeforeEach
    protected void initTestCase() {
        athenaRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        athenaRequestMockProvider.reset();
    }
}
