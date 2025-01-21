package de.tum.cit.aet.artemis.plagiarism.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleCodeStyleTest;

class PlagiarismCodeStyleArchitectureTest extends AbstractModuleCodeStyleTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".plagiarism";
    }

    @Override
    protected int dtoNameEndingThreshold() {
        return 1;
    }
}
