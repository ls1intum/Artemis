package de.tum.cit.aet.artemis.metis.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleTestArchitectureTest;

/**
 * Pins the base classes the Metis integration tests extend. The module has no integration base of its own; its tests use the programming exercise fixtures,
 * whose bases switch the module flag on. Most of them run in the Independent profile; the generation test needs real LocalVC repositories and therefore runs
 * in the local CI lane.
 */
class MetisTestArchitectureTest extends AbstractModuleTestArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".metis";
    }

    @Override
    protected Set<Class<?>> getAbstractModuleIntegrationTestClasses() {
        return Set.of(AbstractProgrammingIntegrationIndependentTest.class, AbstractProgrammingIntegrationLocalCILocalVCTest.class);
    }
}
