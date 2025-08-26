package de.tum.cit.aet.artemis.athena;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider;
import de.tum.cit.aet.artemis.programming.service.GitRepositoryExportService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTest;

/**
 * Base class for Athena tests providing common functionality
 */
public abstract class AbstractAthenaTest extends AbstractSpringIntegrationJenkinsLocalVCTest {

    @Autowired
    protected AthenaRequestMockProvider athenaRequestMockProvider;

    @MockitoSpyBean
    protected GitRepositoryExportService gitRepositoryExportService;

    @BeforeEach
    protected void initTestCase() {
        athenaRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        athenaRequestMockProvider.reset();
    }

    @AfterEach
    @Override
    protected void resetSpyBeans() {
        Mockito.reset(gitRepositoryExportService);
        super.resetSpyBeans();
    }
}
