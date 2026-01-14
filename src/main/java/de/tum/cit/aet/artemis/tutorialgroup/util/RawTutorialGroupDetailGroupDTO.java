package de.tum.cit.aet.artemis.tutorialgroup.util;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_EMPTY)
public record RawTutorialGroupDetailGroupDTO(@NotNull Long groupId, @NotNull String title, @NotNull String language, @NotNull Boolean isOnline, @Nullable Integer capacity,
        @Nullable String campus, @NotNull String teachingAssistantName, @NotNull String teachingAssistantLogin, @Nullable String teachingAssistantImageUrl,
        @Nullable Long groupChannelId, @Nullable Integer scheduleDayOfWeek, @Nullable String scheduleStartTime, @Nullable String scheduleEndTime,
        @Nullable String scheduleLocation) {
}
