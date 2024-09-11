package de.tum.cit.aet.artemis.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MaxAndReachablePoints(double maxPoints, double reachablePoints, double reachablePresentationPoints) {

}
