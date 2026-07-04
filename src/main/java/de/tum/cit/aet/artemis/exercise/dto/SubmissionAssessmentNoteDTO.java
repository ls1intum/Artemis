package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;
import java.util.Objects;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentNote;
import de.tum.cit.aet.artemis.core.dto.UserPublicInfoDTO;

/**
 * DTO representing an internal assessment note nested in a submission result.
 *
 * @param id      the assessment-note identifier
 * @param note    the note text, if available
 * @param creator safe public information about the initialized creator, if available
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionAssessmentNoteDTO(Long id, @Nullable String note, @Nullable UserPublicInfoDTO creator) implements Serializable {

    /**
     * Maps an assessment note without initializing its creator association.
     *
     * @param assessmentNote the assessment note to map
     * @return the submission assessment-note DTO
     */
    public static SubmissionAssessmentNoteDTO of(AssessmentNote assessmentNote) {
        Objects.requireNonNull(assessmentNote, "The assessment note must be set");
        UserPublicInfoDTO creator = assessmentNote.getCreator() != null && Hibernate.isInitialized(assessmentNote.getCreator()) ? new UserPublicInfoDTO(assessmentNote.getCreator())
                : null;
        return new SubmissionAssessmentNoteDTO(assessmentNote.getId(), assessmentNote.getNote(), creator);
    }
}
