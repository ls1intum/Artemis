package de.tum.cit.aet.artemis.tutorialgroup.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.springframework.lang.Nullable;

public record CreateAndUpdateTutorialGroupDTO(@NotNull @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9: -]{0,19}$") String title, long tutorId, @NotNull String language,
        boolean isOnline, @Nullable String campus, @Nullable @Min(1) Integer capacity, @Nullable String additionalInformation,
        @NotNull TutorialGroupScheduleDTO tutorialGroupScheduleDTO) {
}
