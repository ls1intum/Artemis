package de.tum.cit.aet.artemis.localci.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildJobStatisticsDTO(double buildDurationSeconds, long buildCountWhenUpdated, Long exerciseId) {

}
