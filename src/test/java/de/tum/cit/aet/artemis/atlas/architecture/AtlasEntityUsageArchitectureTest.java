package de.tum.cit.aet.artemis.atlas.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleEntityUsageArchitectureTest;

/**
 * Architecture test to verify that REST controllers in the Atlas module
 * do not use @Entity types directly. Controllers should use DTOs instead.
 */
class AtlasEntityUsageArchitectureTest extends AbstractModuleEntityUsageArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".atlas";
    }

    @Override
    protected int getMaxEntityReturnViolations() {
        return 0;
    }

    @Override
    protected int getMaxEntityInputViolations() {
        return 0;
    }
}
