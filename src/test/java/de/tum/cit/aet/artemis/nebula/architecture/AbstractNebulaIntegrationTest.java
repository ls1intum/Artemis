package de.tum.cit.aet.artemis.nebula.architecture;

import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.nebula.service.NebulaConnectionService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

public abstract class AbstractNebulaIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    // External Repositories
    @Autowired
    protected FaqRepository faqRepository;

    @Autowired
    protected CourseRepository courseRepository;

    @Autowired
    protected UserRepository userRepository;

    // External Services
    @Autowired
    protected NebulaConnectionService nebulaConnectionService;
}
