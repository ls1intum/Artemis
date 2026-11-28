package de.tum.cit.aet.artemis.exercise.dto.versioning;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.dto.UserPublicInfoDTO;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseVersionMetadataDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id, UserPublicInfoDTO author, Instant createdDate) {

    /**
     * A constructor for ExerciseVersionMetadataDTO that takes a User entity and a creation date.
     *
     * @param id          the version id
     * @param user        the author of the exercise version
     * @param createdDate the version creation date
     */
    public ExerciseVersionMetadataDTO(long id, User user, Instant createdDate) {
        this(id, user == null ? null : new UserPublicInfoDTO(user), createdDate);
    }

}
