package de.tum.cit.aet.artemis.fileupload.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.assessment.dto.GradingInstructionDTO;

/**
 * DTO for feedback submitted as part of a file upload assessment.
 *
 * @param id                 the ID of an existing feedback item
 * @param text               the feedback category or summary text
 * @param detailText         the detailed feedback text
 * @param reference          the reference to an assessed element
 * @param credits            the score credits awarded or deducted
 * @param positive           whether the feedback is positive
 * @param type               the feedback type
 * @param visibility         the feedback visibility
 * @param gradingInstruction the structured grading instruction linked to the feedback
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadFeedbackInputDTO(Long id, String text, String detailText, String reference, Double credits, Boolean positive, FeedbackType type, Visibility visibility,
        GradingInstructionDTO gradingInstruction) {

    /**
     * Creates detached feedback entity state for the existing assessment service.
     *
     * @return the detached feedback entity
     */
    public Feedback toEntity() {
        Feedback feedback = new Feedback();
        feedback.setId(id);
        feedback.setText(text);
        feedback.setDetailText(detailText);
        feedback.setReference(reference);
        feedback.setCredits(credits);
        feedback.setPositive(positive);
        feedback.setType(type);
        feedback.setVisibility(visibility);
        feedback.setGradingInstruction(gradingInstruction != null ? gradingInstruction.toEntity() : null);
        return feedback;
    }
}
