package de.tum.cit.aet.artemis.exam.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleCodeStyleTest;

class ExamCodeStyleArchitectureTest extends AbstractModuleCodeStyleTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".exam";
    }

    @Override
    protected int dtoNameEndingThreshold() {
        return 4;
    }
}
