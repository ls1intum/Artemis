package de.tum.cit.aet.artemis.tutorialgroup.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleEntityUsageArchitectureTest;

/**
 * Architecture test to verify that REST controllers in the TutorialGroup module
 * do not use @Entity types directly. Controllers should use DTOs instead.
 */
class TutorialGroupEntityUsageArchitectureTest extends AbstractModuleEntityUsageArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".tutorialgroup";
    }

    @Override
    protected int getMaxEntityReturnViolations() {
        return 0;
    }

    @Override
    protected int getMaxEntityInputViolations() {
        return 0;
    }

    @Override
    protected int getMaxDtoEntityFieldViolations() {
        return 0;
    }
}
