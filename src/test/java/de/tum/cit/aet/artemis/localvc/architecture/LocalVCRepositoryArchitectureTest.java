package de.tum.cit.aet.artemis.localvc.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleRepositoryArchitectureTest;

class LocalVCRepositoryArchitectureTest extends AbstractModuleRepositoryArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".localvc";
    }
}
