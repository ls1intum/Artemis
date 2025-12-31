package de.tum.cit.aet.artemis.exercise.dto;

import java.util.HashSet;
import java.util.Set;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for creating and updating teams.
 * Uses user IDs for students and owner references.
 *
 * @param id        the id of the team (null for creation)
 * @param name      the name of the team
 * @param shortName the short name of the team
 * @param image     the image URL of the team
 * @param students  the set of student user IDs
 * @param ownerId   the owner user ID
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TeamInputDTO(@Nullable Long id, @NotNull @Size(max = 250) String name, @NotNull @Size(max = 10) String shortName, @Nullable @Size(max = 500) String image,
        @Nullable Set<Long> students, @Nullable Long ownerId) {

    /**
     * Returns the students, defaulting to an empty set if null.
     */
    public Set<Long> studentsOrEmpty() {
        return students != null ? students : new HashSet<>();
    }
}
