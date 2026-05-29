package de.tum.cit.aet.artemis.localci.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleCodeStyleTest;

class LocalCICodeStyleArchitectureTest extends AbstractModuleCodeStyleTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".localci";
    }

    // BuildJobInterface is an interface (not a record), so it cannot be a DTO and does not end with DTO.
    // TODO: rename or relocate; currently mirrors the pre-extraction violation count in programming.
    @Override
    protected int dtoNameEndingThreshold() {
        return 1;
    }
}
