package de.tum.cit.aet.artemis.iris.dto;

import java.io.Serializable;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdict;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdictReview;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisAssessmentProgrammingStudentParticipationProjectionDTO(Long id, Long exerciseId, String repositoryUri, String buildPlanId, String studentLogin,
        String studentFirstName, String studentLastName, Long irisAssessmentId, IrisVerdict irisAssessmentVerdict, IrisVerdictReview irisAssessmentVerdictReview)
        implements Serializable {

    /**
     * Converts this projection into the DTO returned by the Iris assessment endpoints.
     *
     * @param submissionCount number of submissions for the participation
     * @return the DTO representation
     */
    public IrisAssessmentProgrammingStudentParticipationDTO toDto(@Nullable Integer submissionCount) {
        return new IrisAssessmentProgrammingStudentParticipationDTO(id, exerciseId, submissionCount, repositoryUri, buildPlanId, null,
                new StudentIrisAssessmentDTO(studentLogin, studentName()),
                irisAssessmentId == null ? null
                        : new IrisAssessmentProgrammingStudentParticipationDTO.IrisAssessmentForParticipationDTO(irisAssessmentId, irisAssessmentVerdict,
                                irisAssessmentVerdictReview));
    }

    private String studentName() {
        return studentLastName != null && !studentLastName.isEmpty() ? studentFirstName + " " + studentLastName : studentFirstName;
    }
}
