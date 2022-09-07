package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipantScoreAverageDTO(String name, Double averageScore, Double averageRatedScore, Double averagePoints, Double averageRatedPoints) {
}
