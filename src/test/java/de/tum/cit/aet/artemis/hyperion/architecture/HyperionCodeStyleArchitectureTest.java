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

    // Sixteen enums, not record DTOs: thirteen existing authoring/checklist/quiz enums and three variant-generation enums.
    // All record DTOs retain the zero-violation threshold above.
    @Override
    protected int dtoNameEndingThreshold() {
        return 16;
    }
}
