package de.tum.cit.aet.artemis.assessment.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentMode;
import de.tum.cit.aet.artemis.assessment.domain.PresentationGradingMethod;

/**
 * An individual presentation assessment instance with its presentation definition.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresentationAssessmentStudentRowDTO(PresentationAssessmentDTO presentationAssessment, PresentationAssessmentInstanceDTO instance) {

    public PresentationAssessmentStudentRowDTO(Long assessmentId, String title, String description, PresentationGradingMethod gradingMethod, Double maxPoints, Long courseId,
            Long exerciseId, String exerciseTitle, Long instanceId, ZonedDateTime presentationDate, Double resultPoints, Boolean passed, Double rubricScore, String language,
            PresentationAssessmentMode mode, String location, String meetingLink, String remark, String studentLogin, String firstName, String lastName, String email) {
        this(new PresentationAssessmentDTO(assessmentId, title, description, gradingMethod, maxPoints, courseId, exerciseId, exerciseTitle),
                new PresentationAssessmentInstanceDTO(instanceId, presentationDate, resultPoints, passed, rubricScore, language, mode, location, meetingLink, remark,
                        new PresentationAssessmentStudentDTO(studentLogin, firstName, lastName, email)));
    }
}
