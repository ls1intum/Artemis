package de.tum.cit.aet.artemis.text.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleEntityUsageArchitectureTest;

/**
 * Architecture test to verify that REST controllers in the Text module
 * do not use @Entity types directly. Controllers use DTOs instead.
 */
class TextEntityUsageArchitectureTest extends AbstractModuleEntityUsageArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".text";
    }

    @Override
    protected int getExpectedEntityReturnViolations() {
        return 0;
    }

    @Override
    protected int getExpectedEntityInputViolations() {
        return 0;
    }

    @Override
    protected int getExpectedDtoEntityFieldViolations() {
        return 0;
    }
}
