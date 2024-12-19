package de.tum.cit.aet.artemis.modeling.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleCodeStyleTest;

class ModelingCodeStyleArchitectureTest extends AbstractModuleCodeStyleTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".modeling";
    }

    @Override
    protected int dtoAsAnnotatedRecordThreshold() {
        return 0;
    }

    @Override
    protected int dtoNameEndingThreshold() {
        return 0;
    }
}
