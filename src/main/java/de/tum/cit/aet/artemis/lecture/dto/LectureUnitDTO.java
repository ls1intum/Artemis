package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;
import java.util.Set;

public interface LectureUnitDTO {

    Long id();

    ZonedDateTime releaseDate();

    Set<CompetencyLinkDTO> competencyLinks();
}
