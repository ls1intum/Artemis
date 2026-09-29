package de.tum.cit.aet.artemis.metis.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleTestArchitectureTest;

/**
 * Pins the base class the Metis integration tests extend. The module has no services yet and therefore no integration
 * base of its own; its tests use the programming exercise fixtures of the Independent profile, which is also where the
 * module flag is switched on.
 */
class MetisTestArchitectureTest extends AbstractModuleTestArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".metis";
    }

    @Override
    protected Set<Class<?>> getAbstractModuleIntegrationTestClasses() {
        return Set.of(AbstractProgrammingIntegrationIndependentTest.class);
    }
}
