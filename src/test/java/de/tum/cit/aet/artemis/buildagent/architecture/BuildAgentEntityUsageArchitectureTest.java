package de.tum.cit.aet.artemis.buildagent.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleEntityUsageArchitectureTest;

/**
 * Architecture test verifying that REST controllers in the BuildAgent module do not use @Entity types
 * directly (neither as return types nor in request bodies) and that DTOs do not reference entities.
 * Controllers should use DTOs instead.
 */
class BuildAgentEntityUsageArchitectureTest extends AbstractModuleEntityUsageArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".buildagent";
    }

    @Override
    protected int getExpectedEntityReturnViolations() {
        return 0;
    }

    @Override
    protected int getExpectedEntityInputViolations() {
        return 0;
    }
}
