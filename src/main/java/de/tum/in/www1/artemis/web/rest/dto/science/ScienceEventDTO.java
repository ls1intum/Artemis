package de.tum.in.www1.artemis.web.rest.dto.science;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.science.ScienceEventType;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScienceEventDTO(ScienceEventType type, Long resourceId) {
}
