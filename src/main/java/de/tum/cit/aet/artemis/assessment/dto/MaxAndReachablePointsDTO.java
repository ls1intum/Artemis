package de.tum.cit.aet.artemis.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MaxAndReachablePointsDTO(double maxPoints, double reachablePoints, double reachablePresentationPoints) {

}
