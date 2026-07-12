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
    // Reduced from 24 to 22: getStudentExamForConduction + getTestRunForConduction now return StudentExamForConductionDTO.
    // Reduced from 22 to 21: getStudentExamForSummary now returns StudentExamForSummaryDTO.
    @Override
    protected int getExpectedEntityReturnViolations() {
        return 7;
    }

    // TODO: Reduce this to 0 by accepting DTOs instead of entities in @RequestBody/@RequestPart
    @Override
    protected int getExpectedEntityInputViolations() {
        return 3;
    }

    // TODO: Reduce this to 0 by removing entity references from DTOs.
    // The exam-import endpoints return ExamImportResultDTO/ExerciseGroupImportResultDTO, which wrap the imported
    // Exam/ExerciseGroup entity alongside the skipped/incomplete exercise titles. This trades two raw-entity returns
    // (the more severe anti-pattern) for two DTO-wrapped-entity fields; both should be reduced to 0 eventually.
    // Reduced from 3 to 2: StudentExamWithGradeDTO.studentExam is now a StudentExamForDetailDTO projection, not the entity.
    @Override
    protected int getExpectedDtoEntityFieldViolations() {
        return 2;
    }
}
