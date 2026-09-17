package de.tum.cit.aet.artemis.exercise.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleEntityUsageArchitectureTest;

/**
 * Architecture test to verify that REST controllers in the Exercise module
 * do not use @Entity types directly. Controllers should use DTOs instead.
 */
class ExerciseEntityUsageArchitectureTest extends AbstractModuleEntityUsageArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".exercise";
    }

    @Override
    protected int getExpectedEntityReturnViolations() {
        return 0;
    }

    // This module is already compliant for input violations
    @Override
    protected int getExpectedEntityInputViolations() {
        return 0;
    }

    // This module is already compliant for DTO entity field violations
    @Override
    protected int getExpectedDtoEntityFieldViolations() {
        return 0;
    }
}
