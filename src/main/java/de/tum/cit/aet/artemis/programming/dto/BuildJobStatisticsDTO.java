package de.tum.cit.aet.artemis.programming.dto;

public record BuildJobStatisticsDTO(long buildDurationSeconds, long buildCountWhenUpdated, Long exerciseId) {

}
