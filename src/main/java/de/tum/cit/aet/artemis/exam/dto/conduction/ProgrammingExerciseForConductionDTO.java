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
public record ProgrammingExerciseForConductionDTO(Boolean allowOfflineIde, Boolean allowOnlineEditor, boolean allowOnlineIde, AssessmentType assessmentType, String packageName,
        ProgrammingLanguage programmingLanguage, String projectKey, ProjectType projectType, boolean releaseTestsWithExampleSolution, Boolean showTestNamesToStudents,
        Boolean staticCodeAnalysisEnabled, SubmissionPolicyForConductionDTO submissionPolicy, boolean testCasesChanged) {

    /**
     * Extracts the programming-specific fields from a (masked) programming exercise.
     * <p>
     * {@code allowOnlineEditor} gates the embedded code editor the student works in
     * ({@code programming-exam-submission.component.html}) and drives the "offline IDE only" branches in the exam
     * navigation; {@code submissionPolicy} feeds the student's remaining-submissions indicator. Both survive
     * {@code filterSensitiveInformation()} (it only strips the repositories, build plans and build config), so the
     * pre-DTO entity wire carried them and the projection must too.
     *
     * @param programmingExercise the programming exercise to convert
     * @return the programming-specific fields
     */
    public static ProgrammingExerciseForConductionDTO of(ProgrammingExercise programmingExercise) {
        return new ProgrammingExerciseForConductionDTO(programmingExercise.isAllowOfflineIde(), programmingExercise.isAllowOnlineEditor(), programmingExercise.isAllowOnlineIde(),
                programmingExercise.getAssessmentType(), programmingExercise.getPackageName(), programmingExercise.getProgrammingLanguage(), programmingExercise.getProjectKey(),
                programmingExercise.getProjectType(), programmingExercise.isReleaseTestsWithExampleSolution(), programmingExercise.getShowTestNamesToStudents(),
                programmingExercise.isStaticCodeAnalysisEnabled(), SubmissionPolicyForConductionDTO.of(programmingExercise.getSubmissionPolicy()),
                programmingExercise.getTestCasesChanged());
    }
}
