package de.tum.cit.aet.artemis.localvc.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleTestArchitectureTest;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalVCSamlTest;

class LocalVCTestArchitectureTest extends AbstractModuleTestArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".localvc";
    }

    @Override
    protected Set<Class<?>> getAbstractModuleIntegrationTestClasses() {
        return Set.of(AbstractSpringIntegrationIndependentTest.class, AbstractSpringIntegrationLocalCILocalVCTest.class, AbstractSpringIntegrationLocalVCSamlTest.class);
    }
}
