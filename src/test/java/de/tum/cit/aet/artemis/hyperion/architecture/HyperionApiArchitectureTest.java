package de.tum.cit.aet.artemis.hyperion.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleAccessArchitectureTest;

class HyperionApiArchitectureTest extends AbstractModuleAccessArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".hyperion";
    }
}
