package de.tum.in.www1.artemis.web.rest.dto;

import java.time.ZonedDateTime;

/**
 * Information of a lecture unit to be splitted
 */
public record LectureUnitSplitDTO(String unitName, ZonedDateTime releaseDate, int startPage, int endPage) {
}
