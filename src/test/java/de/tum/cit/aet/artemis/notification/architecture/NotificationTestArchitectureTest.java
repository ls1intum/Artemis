package de.tum.cit.aet.artemis.notification.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleTestArchitectureTest;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class NotificationTestArchitectureTest extends AbstractModuleTestArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".notification";
    }

    @Override
    protected Set<Class<?>> getAbstractModuleIntegrationTestClasses() {
        return Set.of(AbstractSpringIntegrationIndependentTest.class);
    }
}
