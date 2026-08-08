package de.tum.cit.aet.artemis.lecture.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleEntityUsageArchitectureTest;

/**
 * Architecture test to verify that REST controllers in the Lecture module
 * do not use @Entity types directly. Controllers should use DTOs instead.
 * <p>
 * The remaining (non-zero) thresholds are intentional and tracked: they both stem from the same coupling to the full
 * exercise dashboard graph, which the client renders via the shared {@code CourseExerciseRowComponent}. Migrating them
 * requires an entity-free exercise-dashboard DTO (exercise + participations + submissions + results + quiz batches)
 * that does not exist anywhere in the codebase yet and spans several modules. That is deliberately out of scope for the
 * Lecture DTO slice and is left as a dedicated follow-up.
 */
class LectureEntityUsageArchitectureTest extends AbstractModuleEntityUsageArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".lecture";
    }

    // Remaining: LectureUnitResource#getLectureUnitById returns the polymorphic LectureUnit entity. Its only client
    // (learning-path lecture unit view) needs the unit's lecture/course and, for exercise units, feeds the full
    // dashboard exercise into CourseExerciseRowComponent. It cannot be reduced without the deferred exercise-dashboard DTO.
    // NOTE: temporarily increased by the course-overview per-tab load split (PR #12999): the overview now fetches
    // the course's lectures directly instead of receiving them inside the (expensive) for-dashboard course. The
    // payload the client receives is unchanged — only the endpoint serving it moved — so this is a relocation of an
    // existing entity exposure, not a new one. It is resolved by the ongoing Course/Exercise/Lecture/Exam DTO
    // migration, which owns lowering this back down.
    @Override
    protected int getExpectedEntityReturnViolations() {
        return 2;
    }

    @Override
    protected int getExpectedEntityInputViolations() {
        return 0;
    }

    // Remaining: LectureDetailsDTO.ExerciseUnitDTO.exercise embeds the dashboard Exercise entity for the same reason.
    @Override
    protected int getExpectedDtoEntityFieldViolations() {
        return 1;
    }
}
