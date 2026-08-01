package de.tum.cit.aet.artemis.atlas.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ScienceCourseConsentDTO(Long courseId, String courseTitle, String courseShortName, Boolean active, Instant decisionDate, boolean scienceEnabled) {
}
