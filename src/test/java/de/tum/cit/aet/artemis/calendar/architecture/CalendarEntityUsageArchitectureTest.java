package de.tum.cit.aet.artemis.calendar.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleEntityUsageArchitectureTest;

/**
 * Architecture test verifying that REST controllers in the Calendar module do not use @Entity types
 * directly (neither as return types nor in request bodies) and that DTOs do not reference entities.
 * Controllers should use DTOs instead.
 */
class CalendarEntityUsageArchitectureTest extends AbstractModuleEntityUsageArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".calendar";
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
