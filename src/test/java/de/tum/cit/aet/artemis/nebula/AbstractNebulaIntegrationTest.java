package de.tum.cit.aet.artemis.nebula;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;

import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.core.connector.NebulaRequestMockProvider;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.nebula.config.NebulaEnabled;
import de.tum.cit.aet.artemis.nebula.service.NebulaConnectionService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

@Conditional(NebulaEnabled.class)
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

    @Autowired
    protected NebulaConnectionService nebulaConnectionService;

    @BeforeEach
    void setup() {
        nebulaRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        nebulaRequestMockProvider.reset();
    }

}
