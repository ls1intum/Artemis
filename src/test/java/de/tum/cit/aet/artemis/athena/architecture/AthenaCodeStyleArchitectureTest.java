package de.tum.cit.aet.artemis.athena.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleCodeStyleTest;

class AthenaCodeStyleArchitectureTest extends AbstractModuleCodeStyleTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".athena";
    }

    @Override
    protected int dtoNameEndingThreshold() {
        return 1;
    }
}
