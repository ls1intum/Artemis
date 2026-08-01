package de.tum.cit.aet.artemis.iris.dto;

import java.io.Serializable;

import jakarta.annotation.Nullable;

import de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdict;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdictReview;

public record IrisAssessmentProgrammingStudentParticipationProjection(Long id, String repositoryUri, String buildPlanId, String studentLogin, String studentFirstName,
        String studentLastName, Long irisAssessmentId, IrisVerdict irisAssessmentVerdict, IrisVerdictReview irisAssessmentVerdictReview) implements Serializable {

    /**
     * Converts this projection into the DTO returned by the Iris assessment endpoints.
     *
     * @param submissionCount number of submissions for the participation
     * @return the DTO representation
     */
    public IrisAssessmentProgrammingStudentParticipationDTO toDto(@Nullable Integer submissionCount) {
        return new IrisAssessmentProgrammingStudentParticipationDTO(id, submissionCount, repositoryUri, buildPlanId, null,
                new StudentIrisAssessmentDTO(studentLogin, studentName()),
                irisAssessmentId == null ? null
                        : new IrisAssessmentProgrammingStudentParticipationDTO.IrisAssessmentForParticipationDTO(irisAssessmentId, irisAssessmentVerdict,
                                irisAssessmentVerdictReview));
    }

    private String studentName() {
        return studentLastName != null && !studentLastName.isEmpty() ? studentFirstName + " " + studentLastName : studentFirstName;
    }
}
