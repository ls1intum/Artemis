package de.tum.cit.aet.artemis.atlas.dto;

import java.time.ZonedDateTime;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEventType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ScienceResearchExportRequestDTO(Set<Long> courseIds, ZonedDateTime from, ZonedDateTime to, Set<ScienceEventType> eventTypes, String purpose) {
}
