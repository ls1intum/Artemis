package de.tum.cit.aet.artemis.metis.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationJenkinsLocalVCTest;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleTestArchitectureTest;

/**
 * Pins the base classes the Metis integration tests extend. The module has no integration base of its own; its tests use the programming exercise fixtures.
 * Most of them run in the Independent profile, which switches the module flag on; the generation test needs real LocalVC repositories and therefore runs in
 * the local CI lane, which switches it on as well; and the test of a server without the module runs in the Jenkins profile, one of the shared contexts that
 * keep it switched off.
 */
class MetisTestArchitectureTest extends AbstractModuleTestArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".metis";
    }

    @Override
    protected Set<Class<?>> getAbstractModuleIntegrationTestClasses() {
        return Set.of(AbstractProgrammingIntegrationIndependentTest.class, AbstractProgrammingIntegrationLocalCILocalVCTest.class,
                AbstractProgrammingIntegrationJenkinsLocalVCTest.class);
    }
}
