package de.tum.cit.aet.artemis.tutorialgroup.util;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import de.tum.cit.aet.artemis.core.util.ServedFileUrl;

@JsonInclude(Include.NON_EMPTY)
public record RawTutorialGroupDTO(@NotNull Long groupId, @NotNull String title, @NotNull String language, @NotNull Boolean isOnline, @Nullable Integer capacity,
        @Nullable String campus, @NotNull String tutorName, @NotNull String tutorLogin, long tutorId, @Nullable String tutorImageUrl, @Nullable String additionalInformation,
        @Nullable Long groupChannelId, @Nullable Integer scheduleDayOfWeek, @Nullable String scheduleStartTime, @Nullable String scheduleEndTime,
        @Nullable String scheduleLocation) {

    /**
     * The tutor's profile picture comes out of the column as a filename, so it is turned into the path the client requests it under. The conversion is idempotent.
     */
    public RawTutorialGroupDTO {
        tutorImageUrl = ServedFileUrl.profilePicture(tutorId, tutorImageUrl);
    }
}
