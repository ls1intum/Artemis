package de.tum.cit.aet.artemis.course.dto.home;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseHomeWelcomeDTO(CourseHomeWelcomeCase welcomeCase, int variantIndex, java.util.Map<String, String> params, String contextFingerprint, boolean aiEligible) {
}
