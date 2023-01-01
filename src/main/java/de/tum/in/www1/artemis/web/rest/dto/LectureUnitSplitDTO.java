package de.tum.in.www1.artemis.web.rest.dto;

import java.time.ZonedDateTime;

public record LectureUnitSplitDTO(String unitName, ZonedDateTime releaseDate, String startPage, String endPage) {
}
