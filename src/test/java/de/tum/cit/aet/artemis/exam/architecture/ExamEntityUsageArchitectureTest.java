package de.tum.cit.aet.artemis.exam.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleEntityUsageArchitectureTest;

/**
 * Architecture test to verify that REST controllers in the Exam module
 * do not use @Entity types directly. Controllers should use DTOs instead.
 * <p>
 * TODO: Reduce violation counts to 0 by introducing DTOs for all endpoints.
 */
class ExamEntityUsageArchitectureTest extends AbstractModuleEntityUsageArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".exam";
    }

    // TODO: Reduce this to 0 by returning DTOs instead of entities
    // NOTE: temporarily increased by the course-overview per-tab load split (PR #12999): the overview now fetches
    // the course's visible exams directly instead of receiving them inside the (expensive) for-dashboard course. The
    // payload the client receives is unchanged — only the endpoint serving it moved — so this is a relocation of an
    // existing entity exposure, not a new one. It is resolved by the ongoing Course/Exercise/Lecture/Exam DTO
    // migration, which owns lowering this back down.
    @Override
    protected int getExpectedEntityReturnViolations() {
        return 11;
    }

    // TODO: Reduce this to 0 by accepting DTOs instead of entities in @RequestBody/@RequestPart
    // Note: the import-exercise-group endpoint deliberately keeps its entity request body (see PR description); the
    // shared exercise-import services copy basis fields off the incoming exercise graph, which the slim
    // ExerciseImportDTO cannot carry, so switching it to a DTO would silently drop those fields.
    @Override
    protected int getExpectedEntityInputViolations() {
        return 3;
    }

    // TODO: Reduce this to 0 by removing entity references from DTOs.
    // StudentExamWithGradeDTO still wraps the full StudentExam entity alongside the computed grade summary; this
    // remaining DTO-wrapped-entity field should be reduced to 0 eventually.
    @Override
    protected int getExpectedDtoEntityFieldViolations() {
        return 1;
    }
}
