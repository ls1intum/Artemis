package de.tum.cit.aet.artemis.course.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleEntityUsageArchitectureTest;

/**
 * Architecture test to verify that REST controllers in the Course module
 * do not use @Entity types directly. Controllers should use DTOs instead.
 */
class CourseEntityUsageArchitectureTest extends AbstractModuleEntityUsageArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".course";
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
