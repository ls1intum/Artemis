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

    // Threshold is 3:
    // 1. HyperionCodeGenerationEventDTO contains inner enum HyperionCodeGenerationEventType
    // 2. ChecklistActionRequestDTO contains inner enum ActionType
    // 3. ChecklistSection is a standalone enum used as a @PathVariable for section-level analysis
    @Override
    protected int dtoNameEndingThreshold() {
        return 3;
    }
}
