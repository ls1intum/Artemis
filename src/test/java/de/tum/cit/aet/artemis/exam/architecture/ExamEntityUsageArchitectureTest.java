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
    @Override
    protected int getExpectedEntityReturnViolations() {
        return 0;
    }

    // TODO: Reduce this to 0 by accepting DTOs instead of entities in @RequestBody/@RequestPart
    // Note: the import-exercise-group endpoint deliberately keeps its entity request body (see PR description); the
    // shared exercise-import services copy basis fields off the incoming exercise graph, which the slim
    // ExerciseImportDTO cannot carry, so switching it to a DTO would silently drop those fields.
    @Override
    protected int getExpectedEntityInputViolations() {
        return 1;
    }
}
