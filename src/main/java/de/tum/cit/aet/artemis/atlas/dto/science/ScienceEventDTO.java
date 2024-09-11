package de.tum.cit.aet.artemis.atlas.dto.science;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEventType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ScienceEventDTO(ScienceEventType type, Long resourceId) {
}
