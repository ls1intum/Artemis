package de.tum.cit.aet.artemis.lti.architecture;

import de.tum.cit.aet.artemis.lti.AbstractLtiIntegrationTest;
import de.tum.cit.aet.artemis.shared.architecture.AbstractModuleTestArchitectureTest;

class LtiTestArchitectureTest extends AbstractModuleTestArchitectureTest<AbstractLtiIntegrationTest> {

    @Override
    protected String getModulePackageName() {
        return "lti";
    }

    @Override
    protected Class<AbstractLtiIntegrationTest> getAbstractModuleIntegrationTestClass() {
        return AbstractLtiIntegrationTest.class;
    }
}
