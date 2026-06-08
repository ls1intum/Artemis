package de.tum.cit.aet.artemis.localvc.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleServiceArchitectureTest;

class LocalVCServiceArchitectureTest extends AbstractModuleServiceArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".localvc";
    }
}
