package de.tum.cit.aet.artemis.programming.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleCodeStyleTest;

class ProgrammingCodeStyleArchitectureTest extends AbstractModuleCodeStyleTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".programming";
    }

    @Override
    protected int dtoAsAnnotatedRecordThreshold() {
        return 1;
    }

    @Override
    protected int dtoNameEndingThreshold() {
        return 17;
    }
}
