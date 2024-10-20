package de.tum.cit.aet.artemis.tutorialgroup.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleTestArchitectureTest;
import de.tum.cit.aet.artemis.tutorialgroup.AbstractTutorialGroupIntegrationTest;

class TutorialGroupTestArchitectureTest extends AbstractModuleTestArchitectureTest<AbstractTutorialGroupIntegrationTest> {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".tutorialgroup";
    }

    @Override
    protected Class<AbstractTutorialGroupIntegrationTest> getAbstractModuleIntegrationTestClass() {
        return AbstractTutorialGroupIntegrationTest.class;
    }
}
