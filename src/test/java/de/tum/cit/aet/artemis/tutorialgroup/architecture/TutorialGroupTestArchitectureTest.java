package de.tum.cit.aet.artemis.tutorialgroup.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleTestArchitectureTest;
import de.tum.cit.aet.artemis.tutorialgroup.AbstractTutorialGroupIntegrationTest;

class TutorialGroupTestArchitectureTest extends AbstractModuleTestArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".tutorialgroup";
    }

    @Override
    protected Set<Class<?>> getAbstractModuleIntegrationTestClasses() {
        return Set.of(AbstractTutorialGroupIntegrationTest.class);
    }
}
