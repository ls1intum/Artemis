package de.tum.cit.aet.artemis.exam.dto.conduction;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/**
 * Programming-exercise-specific fields carried in the conduction payload (unwrapped into the exercise object). The
 * sensitive build config / template & solution repositories are already stripped from the entity by
 * {@code filterSensitiveInformation()} before this factory runs, so only the student-facing configuration is projected.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseForConductionDTO(Boolean allowOfflineIde, boolean allowOnlineIde, AssessmentType assessmentType, String packageName,
        ProgrammingLanguage programmingLanguage, String projectKey, ProjectType projectType, boolean releaseTestsWithExampleSolution, Boolean showTestNamesToStudents,
        Boolean staticCodeAnalysisEnabled, boolean testCasesChanged) {

    /**
     * Extracts the programming-specific fields from a (masked) programming exercise.
     *
     * @param programmingExercise the programming exercise to convert
     * @return the programming-specific fields
     */
    public static ProgrammingExerciseForConductionDTO of(ProgrammingExercise programmingExercise) {
        return new ProgrammingExerciseForConductionDTO(programmingExercise.isAllowOfflineIde(), programmingExercise.isAllowOnlineIde(), programmingExercise.getAssessmentType(),
                programmingExercise.getPackageName(), programmingExercise.getProgrammingLanguage(), programmingExercise.getProjectKey(), programmingExercise.getProjectType(),
                programmingExercise.isReleaseTestsWithExampleSolution(), programmingExercise.getShowTestNamesToStudents(), programmingExercise.isStaticCodeAnalysisEnabled(),
                programmingExercise.getTestCasesChanged());
    }
}
