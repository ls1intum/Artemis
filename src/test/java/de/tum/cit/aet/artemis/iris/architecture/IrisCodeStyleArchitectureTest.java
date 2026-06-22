package de.tum.cit.aet.artemis.iris.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleCodeStyleTest;

class IrisCodeStyleArchitectureTest extends AbstractModuleCodeStyleTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".iris";
    }

    @Override
    protected int dtoNameEndingThreshold() {
        // Non-DTO-named types living in dto packages (enums such as PyrisStageState, IngestionState and
        // the Course Memory source enum PyrisCourseMemorySource).
        return 6;
    }
}
