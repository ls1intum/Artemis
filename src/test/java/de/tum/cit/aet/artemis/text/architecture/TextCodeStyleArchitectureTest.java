package de.tum.cit.aet.artemis.text.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleCodeStyleTest;

class TextCodeStyleArchitectureTest extends AbstractModuleCodeStyleTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".text";
    }

    @Override
    protected int dtoAsAnnotatedRecordThreshold() {
        return 1;
    }

    @Override
    protected int dtoNameEndingThreshold() {
        return 1;
    }
}
