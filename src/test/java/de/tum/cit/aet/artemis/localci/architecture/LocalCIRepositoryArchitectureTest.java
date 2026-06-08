package de.tum.cit.aet.artemis.localci.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleRepositoryArchitectureTest;

class LocalCIRepositoryArchitectureTest extends AbstractModuleRepositoryArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".localci";
    }
}
