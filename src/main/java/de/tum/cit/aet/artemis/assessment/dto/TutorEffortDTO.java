package de.tum.cit.aet.artemis.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A data entry used by the tutor effort statistics page. It represents the respective information in terms of
 * number of submissions assessed as well as time spent for each tutor in a particular exercise.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorEffortDTO(Long userId, int numberOfSubmissionsAssessed, double totalTimeSpentMinutes, Long exerciseId, Long courseId) {
}
