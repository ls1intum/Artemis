package de.tum.cit.aet.artemis.fileupload.dto;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentNote;

/**
 * DTO representing an assessment note for a file upload submission.
 *
 * @param id      the ID of the assessment note
 * @param note    the text content of the assessment note
 * @param creator the user who created the assessment note
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadAssessmentNoteDTO(Long id, String note, FileUploadUserDTO creator) {

    /**
     * Factory method to create a {@link FileUploadAssessmentNoteDTO} from an {@link AssessmentNote} entity.
     *
     * @param assessmentNote the assessment note entity to map, can be null
     * @return the mapped DTO, or null if the input was null
     */
    public static FileUploadAssessmentNoteDTO of(AssessmentNote assessmentNote) {
        if (assessmentNote == null) {
            return null;
        }
        User creator = assessmentNote.getCreator();
        FileUploadUserDTO creatorDTO = creator != null && Hibernate.isInitialized(creator) ? FileUploadUserDTO.of(creator) : null;
        return new FileUploadAssessmentNoteDTO(assessmentNote.getId(), assessmentNote.getNote(), creatorDTO);
    }
}
