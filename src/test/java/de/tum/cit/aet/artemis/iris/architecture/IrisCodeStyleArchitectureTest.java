package de.tum.cit.aet.artemis.iris.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleCodeStyleTest;

class IrisCodeStyleArchitectureTest extends AbstractModuleCodeStyleTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".iris";
    }

    @Override
    protected int dtoAsAnnotatedRecordThreshold() {
        return 2;
    }

    @Override
    protected int dtoNameEndingThreshold() {
        return 5;
    }
}
