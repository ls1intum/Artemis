package de.tum.cit.aet.artemis.sandbox.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleServiceArchitectureTest;

class SandboxServiceArchitectureTest extends AbstractModuleServiceArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".sandbox";
    }
}
