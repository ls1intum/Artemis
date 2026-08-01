package de.tum.cit.aet.artemis.iris.dto;

import java.io.Serializable;
import java.util.Optional;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.askuser.IrisAssessment;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdict;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdictReview;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisAssessmentProgrammingStudentParticipationDTO(Long id, Integer submissionCount, String repositoryUri, String buildPlanId, String buildPlanUrl,
        StudentIrisAssessmentDTO student, IrisAssessmentForParticipationDTO irisAssessment) implements Serializable {

    /**
     * Creates a DTO for a programming exercise student participation and its Iris assessment.
     *
     * @param participation the participation to convert
     * @param inClass       whether to use the in-class assessment
     * @param buildPlanUrl  optional build plan URL
     * @return the DTO or null if the participation is null
     */
    @Nullable
    public static IrisAssessmentProgrammingStudentParticipationDTO of(ProgrammingExerciseStudentParticipation participation, boolean inClass, @Nullable String buildPlanUrl) {
        return Optional.ofNullable(participation)
                .map(value -> new IrisAssessmentProgrammingStudentParticipationDTO(value.getId(), value.getSubmissionCount(), value.getRepositoryUri(), value.getBuildPlanId(),
                        buildPlanUrl, StudentIrisAssessmentDTO.of(value.getStudent().orElse(null)),
                        IrisAssessmentForParticipationDTO.of(inClass ? value.getIrisAssessmentInClass() : value.getIrisAssessment())))
                .orElse(null);
    }

    /**
     * Creates a DTO for a programming exercise student participation and its Iris assessment.
     *
     * @param participation the participation to convert
     * @param inClass       whether to use the in-class assessment
     * @return the DTO or null if the participation is null
     */
    @Nullable
    public static IrisAssessmentProgrammingStudentParticipationDTO of(ProgrammingExerciseStudentParticipation participation, boolean inClass) {
        return of(participation, inClass, null);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record IrisAssessmentForParticipationDTO(Long id, IrisVerdict verdict, IrisVerdictReview verdictReview) implements Serializable {

        /**
         * Creates a DTO for an Iris assessment.
         *
         * @param assessment the assessment to convert
         * @return the DTO or null if the assessment is null
         */
        @Nullable
        public static IrisAssessmentForParticipationDTO of(IrisAssessment assessment) {
            return Optional.ofNullable(assessment).map(value -> new IrisAssessmentForParticipationDTO(value.getId(), value.getVerdict(), value.getVerdictReview())).orElse(null);
        }
    }
}
