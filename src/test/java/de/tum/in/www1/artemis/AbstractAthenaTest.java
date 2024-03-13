package de.tum.in.www1.artemis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.connector.AthenaRequestMockProvider;

/**
 * Base class for Athena tests providing common functionality
 */
public abstract class AbstractAthenaTest extends AbstractSpringIntegrationIndependentTest {

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
