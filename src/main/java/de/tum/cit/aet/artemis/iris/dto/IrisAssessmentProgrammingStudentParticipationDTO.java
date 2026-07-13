package de.tum.cit.aet.artemis.iris.dto;

import java.io.Serializable;
import java.util.Optional;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.promptuser.IrisAssessment;
import de.tum.cit.aet.artemis.iris.domain.promptuser.IrisVerdict;
import de.tum.cit.aet.artemis.iris.domain.promptuser.IrisVerdictReview;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisAssessmentProgrammingStudentParticipationDTO(Long id, Integer submissionCount, String repositoryUri, String buildPlanId, String buildPlanUrl,
        StudentIrisAssessmentDTO student, IrisAssessmentForParticipationDTO irisAssessment) implements Serializable {

    @Nullable
    public static IrisAssessmentProgrammingStudentParticipationDTO of(ProgrammingExerciseStudentParticipation participation, @Nullable String buildPlanUrl) {
        return Optional.ofNullable(participation)
                .map(value -> new IrisAssessmentProgrammingStudentParticipationDTO(value.getId(), value.getSubmissionCount(), value.getRepositoryUri(), value.getBuildPlanId(),
                        buildPlanUrl, StudentIrisAssessmentDTO.of(value.getStudent().orElse(null)), IrisAssessmentForParticipationDTO.of(value.getIrisAssessment())))
                .orElse(null);
    }

    @Nullable
    public static IrisAssessmentProgrammingStudentParticipationDTO of(ProgrammingExerciseStudentParticipation participation) {
        return of(participation, null);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record IrisAssessmentForParticipationDTO(Long id, IrisVerdict verdict, IrisVerdictReview verdictReview) implements Serializable {

        @Nullable
        public static IrisAssessmentForParticipationDTO of(IrisAssessment assessment) {
            return Optional.ofNullable(assessment).map(value -> new IrisAssessmentForParticipationDTO(value.getId(), value.getVerdict(), value.getVerdictReview())).orElse(null);
        }
    }
}
