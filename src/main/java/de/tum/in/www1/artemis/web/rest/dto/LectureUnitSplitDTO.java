package de.tum.in.www1.artemis.web.rest.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Information of a lecture unit to be split
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureUnitSplitDTO(String unitName, ZonedDateTime releaseDate, int startPage, int endPage) {
}
