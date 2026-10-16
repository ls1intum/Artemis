package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.FaqState;

/** A visible FAQ and its source course, without loading the course entity for search results. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FaqImportCandidateDTO(long id, String questionTitle, String questionAnswer, FaqState faqState, long courseId, String courseTitle, String courseSemester) {
}
