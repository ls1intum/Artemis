package de.tum.cit.aet.artemis.tutorialgroup.util;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

public record TutorialGroupDetailGroupData(@NotNull Long courseId, @NotNull Long groupId, @NotNull String title, @NotNull String language, @NotNull Boolean isOnline,
        @Nullable Integer capacity, @Nullable String campus, @NotNull String teachingAssistantName, @NotNull String teachingAssistantLogin,
        @Nullable String teachingAssistantImageUrl, @Nullable Long groupChannelId, @Nullable Integer scheduleDayOfWeek, @Nullable String scheduleStartTime,
        @Nullable String scheduleEndTime, @Nullable String scheduleLocation) {
}
