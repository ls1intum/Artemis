package de.tum.cit.aet.artemis.hyperion.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleCodeStyleTest;

class HyperionCodeStyleArchitectureTest extends AbstractModuleCodeStyleTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".hyperion";
    }

    @Override
    protected int dtoAsAnnotatedRecordThreshold() {
        return 0;
    }

    // return 3 as HyperionCodeGenerationEventDTO and ChecklistActionRequestDTO use ENUMs inside as part of the data transfer,
    // and ChecklistSection is a standalone enum used as a path variable for section-level analysis
    @Override
    protected int dtoNameEndingThreshold() {
        return 3;
    }
}
