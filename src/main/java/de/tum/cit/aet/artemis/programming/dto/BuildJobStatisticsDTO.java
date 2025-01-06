package de.tum.cit.aet.artemis.programming.dto;

public record BuildJobStatisticsDTO(double buildDurationSeconds, long buildCountWhenUpdated, Long exerciseId) {

}
