package de.tum.cit.aet.artemis.tutorialgroup.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.hibernate.validator.constraints.Range;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CreateOrUpdateTutorialGroupRequestDTO(@NotNull @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9: -]{0,19}$") String title, long tutorId,
        @NotNull @Size(min = 1, max = 255) String language, boolean isOnline, @Nullable @Size(min = 1, max = 255) String campus,
        @Nullable @Range(min = 1, max = 5000) Integer capacity, @Nullable @Size(min = 1, max = 255) String additionalInformation,
        // @Valid cascades into the schedule: without it the constraints declared on TutorialGroupScheduleDTO are
        // never evaluated, and a schedule missing its first session reaches the mapping code instead of being rejected.
        @Nullable @Valid TutorialGroupScheduleDTO tutorialGroupSchedule) {
}
