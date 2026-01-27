package de.tum.cit.aet.artemis.lti.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleEntityUsageArchitectureTest;

/**
 * Architecture test to verify that REST controllers in the LTI module
 * do not use @Entity types directly. Controllers should use DTOs instead.
 * <p>
 * TODO: Reduce violation counts to 0 by introducing DTOs for all endpoints.
 */
class LtiEntityUsageArchitectureTest extends AbstractModuleEntityUsageArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".lti";
    }

    // TODO: Reduce this to 0 by returning DTOs instead of entities
    @Override
    protected int getMaxEntityReturnViolations() {
        return 3;
    }

    // TODO: Reduce this to 0 by accepting DTOs instead of entities in @RequestBody/@RequestPart
    @Override
    protected int getMaxEntityInputViolations() {
        return 3;
    }

    // This module is already compliant for DTO entity field violations
}
