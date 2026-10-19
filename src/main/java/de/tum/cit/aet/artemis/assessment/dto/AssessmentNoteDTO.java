package de.tum.cit.aet.artemis.assessment.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentNote;

/**
 * DTO containing the relevant {@link AssessmentNote} information for the client.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AssessmentNoteDTO(Long id, String note) implements Serializable {

    /**
     * Converts an AssessmentNote into an AssessmentNoteDTO.
     *
     * @param assessmentNote to convert
     * @return the converted DTO, or null if the assessment note is null
     */
    public static AssessmentNoteDTO of(AssessmentNote assessmentNote) {
        if (assessmentNote == null) {
            return null;
        }
        return new AssessmentNoteDTO(assessmentNote.getId(), assessmentNote.getNote());
    }
}
