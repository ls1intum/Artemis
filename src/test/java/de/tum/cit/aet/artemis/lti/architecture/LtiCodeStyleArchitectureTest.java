package de.tum.cit.aet.artemis.lti.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleCodeStyleTest;

class LtiCodeStyleArchitectureTest extends AbstractModuleCodeStyleTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".lti";
    }

    @Override
    protected int dtoNameEndingThreshold() {
        return 13;
    }
}
