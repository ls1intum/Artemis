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

    // Thirteen enums, not record DTOs: three legacy and four whole-exercise generation event enums, accounting, artifact completeness, generation mode,
    // checklist action type and the two quiz generation enums. All record DTOs retain the zero-violation threshold above.
    @Override
    protected int dtoNameEndingThreshold() {
        return 13;
    }
}
