package de.tum.cit.aet.artemis.assessment.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleCodeStyleTest;

class AssessmentCodeStyleArchitectureTest extends AbstractModuleCodeStyleTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".assessment";
    }

    @Override
    protected int dtoAsAnnotatedRecordThreshold() {
        return 3;
    }

    @Override
    protected int dtoNameEndingThreshold() {
        return 1;
    }
}
