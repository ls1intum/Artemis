package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.FaqState;

/**
 * A visible FAQ and its source course, without loading the course entity for search results.
 *
 * @param id             source FAQ ID to submit when importing
 * @param questionTitle  source question
 * @param questionAnswer source answer shown in the preview
 * @param faqState       source publication state, retained when copying
 * @param courseId       source course ID
 * @param courseTitle    source course name displayed beside the question
 * @param courseSemester optional source semester
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FaqImportCandidateDTO(long id, String questionTitle, String questionAnswer, FaqState faqState, long courseId, String courseTitle, String courseSemester) {
}
