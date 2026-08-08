package de.tum.cit.aet.artemis.course.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleEntityUsageArchitectureTest;

/**
 * Architecture test to verify that REST controllers in the Course module
 * do not use @Entity types directly. Controllers should use DTOs instead.
 * <p>
 * TODO: Reduce violation counts to 0 by introducing DTOs for all endpoints.
 */
class CourseEntityUsageArchitectureTest extends AbstractModuleEntityUsageArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".course";
    }

    // TODO: Reduce this to 0 by returning DTOs instead of entities
    @Override
    protected int getExpectedEntityReturnViolations() {
        return 21;
    }

    // TODO: Reduce this to 0 by accepting DTOs instead of entities in @RequestBody/@RequestPart
    @Override
    protected int getExpectedEntityInputViolations() {
        return 0;
    }

    // TODO: Reduce this to 0 by removing entity references from DTOs
    // NOTE: temporarily increased by 2 by the course-overview per-tab load split (PR #12999):
    // CourseForOverviewDTO.course and CourseExercisesForOverviewDTO.exercises carry the same entities that
    // CourseForDashboardDTO.course already carried — the split moves them onto separate endpoints so a course visit
    // stops loading content the user never opens. Resolved by the ongoing Course/Exercise DTO migration, which owns
    // lowering this back down; CourseExercisesForOverviewDTO in particular needs a polymorphic exercise DTO with
    // participations, submissions and results (today's ExerciseDTO is only (id, type)).
    @Override
    protected int getExpectedDtoEntityFieldViolations() {
        return 4;
    }
}
