package de.tum.cit.aet.artemis.course.dto.home;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseHomeWelcomeRequestDTO(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 64) String contextFingerprint,
        @jakarta.validation.constraints.Pattern(regexp = "en|de") String locale) {
}
