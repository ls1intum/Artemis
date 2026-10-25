package de.tum.cit.aet.artemis.course.dto.home;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseHomeGeneratedWelcomeDTO(String contextFingerprint, @Nullable String message, Source source) {

    public enum Source {
        AI, FALLBACK
    }
}
