package de.tum.cit.aet.artemis.tutorialgroup.util;

import jakarta.validation.constraints.NotNull;

public record TutorialGroupDetailGroupData(@NotNull Long courseId, @NotNull Long groupId, @NotNull String title, @NotNull String language, @NotNull Boolean isOnline,
        Integer capacity, String campus, @NotNull String teachingAssistantName, @NotNull String teachingAssistantLogin, String teachingAssistantImageUrl, Long groupChannelId,
        @NotNull Integer scheduleDayOfWeek, @NotNull String scheduleStartTime, @NotNull String scheduleEndTime, @NotNull String scheduleLocation) {
}
