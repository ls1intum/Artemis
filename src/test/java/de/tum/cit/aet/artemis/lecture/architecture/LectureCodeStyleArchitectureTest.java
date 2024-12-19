package de.tum.cit.aet.artemis.lecture.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleCodeStyleTest;

class LectureCodeStyleArchitectureTest extends AbstractModuleCodeStyleTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".lecture";
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
