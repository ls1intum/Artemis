package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextUnitDTO(Long id, String name, ZonedDateTime releaseDate, String content, Set<CompetencyLinkDTO> competencyLinks) implements LectureUnitDTO {
}
