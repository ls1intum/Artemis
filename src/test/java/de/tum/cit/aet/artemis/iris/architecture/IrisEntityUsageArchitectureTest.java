package de.tum.cit.aet.artemis.iris.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleEntityUsageArchitectureTest;

/**
 * Architecture test to verify that REST controllers in the Iris module
 * do not use @Entity types directly. Controllers should use DTOs instead.
 * <p>
 * TODO: Reduce violation counts to 0 by introducing DTOs for all endpoints.
 */
class IrisEntityUsageArchitectureTest extends AbstractModuleEntityUsageArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".iris";
    }

    @Override
    protected int getMaxEntityReturnViolations() {
        return 0;
    }

    // This module is already compliant for input violations
    @Override
    protected int getMaxEntityInputViolations() {
        return 0;
    }

    @Override
    protected int getMaxDtoEntityFieldViolations() {
        return 0;
    }
}
