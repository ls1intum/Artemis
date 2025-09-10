package de.tum.cit.aet.artemis.core.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleCodeStyleTest;

class CoreCodeStyleArchitectureTest extends AbstractModuleCodeStyleTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".core";
    }

    @Override
    protected int dtoAsAnnotatedRecordThreshold() {
        return 10;
    }

    @Override
    protected int dtoNameEndingThreshold() {
        return 9;
    }
}
