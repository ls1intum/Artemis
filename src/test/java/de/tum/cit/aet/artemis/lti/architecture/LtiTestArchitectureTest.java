package de.tum.cit.aet.artemis.lti.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.lti.AbstractLtiIntegrationTest;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleTestArchitectureTest;

class LtiTestArchitectureTest extends AbstractModuleTestArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".lti";
    }

    @Override
    protected Set<Class<?>> getAbstractModuleIntegrationTestClasses() {
        return Set.of(AbstractLtiIntegrationTest.class);
    }
}
