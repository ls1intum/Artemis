package de.tum.cit.aet.artemis.lti.architecture;

import de.tum.cit.aet.artemis.lti.AbstractLtiIntegrationTest;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleTestArchitectureTest;

public class LtiTestArchitectureTest extends AbstractModuleTestArchitectureTest<AbstractLtiIntegrationTest> {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".lti";
    }

    @Override
    protected Class<AbstractLtiIntegrationTest> getAbstractModuleIntegrationTestClass() {
        return AbstractLtiIntegrationTest.class;
    }
}
