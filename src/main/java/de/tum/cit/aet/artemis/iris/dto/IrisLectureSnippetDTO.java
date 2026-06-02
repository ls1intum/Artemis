package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single lecture-content snippet returned by the Iris lecture-search API.
 *
 * @param lectureName     human-readable name of the parent lecture
 * @param lectureUnitName human-readable name of the lecture unit the snippet was extracted from
 * @param snippet         the extracted text passage
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisLectureSnippetDTO(String lectureName, String lectureUnitName, String snippet) {
}
