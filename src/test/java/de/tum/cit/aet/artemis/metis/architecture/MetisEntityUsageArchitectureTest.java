package de.tum.cit.aet.artemis.metis.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleEntityUsageArchitectureTest;

/**
 * Architecture test verifying that REST controllers in the Metis module do not use @Entity types
 * directly (neither as return types nor in request bodies) and that its DTOs do not reference entities.
 * The four Metis resources exchange DTO records only, and the records in the dto package hold ids, values,
 * enums and other DTOs, never an entity; the stored rows are mapped by the static factories of the records.
 * Every count therefore stays at zero.
 */
class MetisEntityUsageArchitectureTest extends AbstractModuleEntityUsageArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".metis";
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
