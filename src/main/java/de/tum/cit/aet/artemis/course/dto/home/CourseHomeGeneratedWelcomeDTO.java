package de.tum.cit.aet.artemis.course.dto.home;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeSource;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseHomeGeneratedWelcomeDTO(String contextFingerprint, @Nullable String message, CourseHomeWelcomeSource source) {

}
