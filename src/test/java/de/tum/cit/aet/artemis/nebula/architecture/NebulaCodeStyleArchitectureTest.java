package de.tum.cit.aet.artemis.nebula.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleCodeStyleTest;

class NebulaCodeStyleArchitectureTest extends AbstractModuleCodeStyleTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".nebula";
    }

    @Override
    protected int dtoNameEndingThreshold() {
        return 2;
    }
}
