package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public interface LectureUnitDTO {

    Long id();

    ZonedDateTime releaseDate();

    Set<CompetencyLinkDTO> competencyLinks();
}
