package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.CompetencyLinkDTO;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AttachmentVideoUnitDTO(Long id, String name, ZonedDateTime releaseDate, String description, String videoSource, Set<CompetencyLinkDTO> competencyLinks)
        implements LectureUnitDTO {
}
