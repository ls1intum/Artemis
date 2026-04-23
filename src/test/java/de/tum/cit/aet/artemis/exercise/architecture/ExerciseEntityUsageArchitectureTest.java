package de.tum.cit.aet.artemis.exercise.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleEntityUsageArchitectureTest;

/**
 * Architecture test to verify that REST controllers in the Exercise module
 * do not use @Entity types directly. Controllers should use DTOs instead.
 * <p>
 * TODO: Reduce violation counts to 0 by introducing DTOs for all endpoints.
 */
class ExerciseEntityUsageArchitectureTest extends AbstractModuleEntityUsageArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".exercise";
    }

    // TODO: Reduce this to 0 by returning DTOs instead of entities
    @Override
    protected int getMaxEntityReturnViolations() {
        return 28;
    }

    // This module is already compliant for input violations
    @Override
    protected int getMaxEntityInputViolations() {
        return 0;
    }

    // TODO: Reduce this to 0 by removing entity references from DTOs
    @Override
    protected int getMaxDtoEntityFieldViolations() {
        return 4;
    }
}
