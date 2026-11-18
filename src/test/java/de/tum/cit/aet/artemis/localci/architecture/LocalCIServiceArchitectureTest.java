package de.tum.cit.aet.artemis.localci.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleServiceArchitectureTest;

class LocalCIServiceArchitectureTest extends AbstractModuleServiceArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".localci";
    }
}
