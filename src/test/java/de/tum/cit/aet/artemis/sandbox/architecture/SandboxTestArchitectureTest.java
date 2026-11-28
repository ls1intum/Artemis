package de.tum.cit.aet.artemis.sandbox.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.sandbox.AbstractSandboxIntegrationTest;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleTestArchitectureTest;

class SandboxTestArchitectureTest extends AbstractModuleTestArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".sandbox";
    }

    @Override
    protected Set<Class<?>> getAbstractModuleIntegrationTestClasses() {
        return Set.of(AbstractSandboxIntegrationTest.class);
    }
}
