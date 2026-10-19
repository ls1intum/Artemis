package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisDashboardDigestCourseDTO(String courseName, long sessions, long messages, double noResponseRate, double costEur) {
}
