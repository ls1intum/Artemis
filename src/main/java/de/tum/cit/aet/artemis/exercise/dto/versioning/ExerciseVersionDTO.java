package de.tum.cit.aet.artemis.exercise.dto.versioning;

import java.time.Instant;

import org.springframework.lang.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.UserPublicInfoDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseVersionDTO(long id, UserPublicInfoDTO author, Instant createdDate) {

    /**
     * A constructor for ExerciseVersionDTO that takes a User entity and a creation date.
     *
     * @param id          the version id
     * @param user        the author of the exercise version
     * @param createdDate the version creation date
     */
    public ExerciseVersionDTO(long id, @Nullable User user, Instant createdDate) {
        this(id, user == null ? null : new UserPublicInfoDTO(user), createdDate);
    }

}
