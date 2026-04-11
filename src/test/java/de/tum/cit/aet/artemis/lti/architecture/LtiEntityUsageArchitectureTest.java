package de.tum.cit.aet.artemis.lti.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleEntityUsageArchitectureTest;

/**
 * Architecture test to verify that REST controllers in the LTI module
 * do not use @Entity types directly. Controllers should use DTOs instead.
 */
class LtiEntityUsageArchitectureTest extends AbstractModuleEntityUsageArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".lti";
    }

    @Override
    protected int getMaxEntityReturnViolations() {
        return 0;
    }

    @Override
    protected int getMaxEntityInputViolations() {
        return 0;
    }

    // This module is already compliant for DTO entity field violations
}
