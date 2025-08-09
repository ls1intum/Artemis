package de.tum.cit.aet.artemis.nebula;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.core.connector.NebulaRequestMockProvider;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

public abstract class AbstractNebulaIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    @Qualifier("nebulaRequestMockProvider")
    protected NebulaRequestMockProvider nebulaRequestMockProvider;

    @Autowired
    protected FaqRepository faqRepository;

    @Autowired
    protected CourseTestRepository courseRepository;

    @Autowired
    protected UserTestRepository userRepository;

    private static final long TIMEOUT_MS = 200;

    @BeforeEach
    void setup() {
        nebulaRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        nebulaRequestMockProvider.reset();
    }

}
