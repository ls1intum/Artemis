package de.tum.cit.aet.artemis.hyperion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.core.connector.HyperionRequestMockProvider;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

/**
 * Base class for Hyperion REST integration tests providing common functionality.
 * Follows the same pattern as AbstractAthenaTest but for modern REST-based Hyperion testing.
 */
public abstract class AbstractHyperionRestTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    protected HyperionRequestMockProvider hyperionRequestMockProvider;

    @BeforeEach
    protected void initTestCase() {
        hyperionRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        hyperionRequestMockProvider.reset();
    }
}
