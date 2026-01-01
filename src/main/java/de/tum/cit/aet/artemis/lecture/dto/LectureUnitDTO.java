package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.CompetencyLinkDTO;
import de.tum.cit.aet.artemis.exercise.dto.CompetencyLinksHolderDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public interface LectureUnitDTO extends CompetencyLinksHolderDTO {

    Long id();

    ZonedDateTime releaseDate();

    Set<CompetencyLinkDTO> competencyLinks();
}
