package de.tum.cit.aet.artemis.tutorialgroup.util;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.util.ServedFileUrl;

@JsonInclude(Include.NON_EMPTY)
public record RawTutorialGroupDTO(@NotNull Long groupId, @NotNull String title, @NotNull String language, @NotNull Boolean isOnline, @Nullable Integer capacity,
        @Nullable String campus, @Nullable String tutorFirstName, @Nullable String tutorLastName, @Nullable String tutorLogin, @Nullable Long tutorId,
        @Nullable String tutorImageUrl, @Nullable String additionalInformation, @Nullable Long groupChannelId, @Nullable Integer scheduleDayOfWeek,
        @Nullable String scheduleStartTime, @Nullable String scheduleEndTime, @Nullable String scheduleLocation) {

    /**
     * The tutor fields come from a left join, so a tutorial group without an assigned tutor leaves them empty. The tutor's profile
     * picture comes out of the column as a filename, so it is turned into the path the client requests it under. The conversion is
     * idempotent.
     */
    public RawTutorialGroupDTO {
        tutorImageUrl = ServedFileUrl.profilePicture(tutorId, tutorImageUrl);
    }

    /**
     * The tutor's display name under the shared rule: the non-blank name parts joined by a space, or the login when a tutor has no
     * name (see {@link User#displayName}). It is null only when no tutor is assigned, since the login is then null too.
     *
     * @return the display name, or null when the group has no tutor
     */
    public @Nullable String tutorName() {
        return User.displayName(tutorFirstName, tutorLastName, tutorLogin);
    }
}
