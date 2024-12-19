package de.tum.cit.aet.artemis.quiz.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleCodeStyleTest;

class QuizCodeStyleArchitectureTest extends AbstractModuleCodeStyleTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".quiz";
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
