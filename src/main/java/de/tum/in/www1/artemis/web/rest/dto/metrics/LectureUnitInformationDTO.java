package de.tum.in.www1.artemis.web.rest.dto.metrics;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureUnitInformationDTO(long id, String name, ZonedDateTime releaseDate) {
    // TODO add more information about the lecture unit
}
