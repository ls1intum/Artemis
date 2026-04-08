package de.tum.cit.aet.artemis.exercise.dto;

import java.util.HashSet;
import java.util.Set;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Team;

/**
 * DTO for importing teams from a list.
 * Students are identified by login or registration number (not database IDs).
 *
 * @param name      the name of the team
 * @param shortName the short name of the team
 * @param image     the image URL of the team
 * @param students  the set of student identifiers (login or registration number)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TeamImportDTO(@NotNull @Size(max = 250) String name, @NotNull @Size(max = 10) String shortName, @Nullable @Size(max = 500) String image,
        @Nullable Set<StudentIdentifierDTO> students) {

    /**
     * Returns the students, defaulting to an empty set if null.
     */
    public Set<StudentIdentifierDTO> studentsOrEmpty() {
        return students != null ? students : new HashSet<>();
    }

    /**
     * DTO for identifying a student by login or registration number.
     *
     * @param login                     the login of the student
     * @param visibleRegistrationNumber the visible registration number of the student
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record StudentIdentifierDTO(@Nullable String login, @Nullable String visibleRegistrationNumber) {

        /**
         * Converts this identifier to a User object for lookup.
         *
         * @return a User with login or registration number set
         */
        public User toUser() {
            User user = new User();
            user.setLogin(login);
            user.setVisibleRegistrationNumber(visibleRegistrationNumber);
            return user;
        }
    }

    /**
     * Converts this DTO to a Team entity.
     * Note: The resulting team has students with only login/registration numbers set,
     * which need to be resolved to actual users later.
     *
     * @return a new Team entity
     */
    public Team toTeam() {
        Team team = new Team();
        team.setName(name);
        team.setShortName(shortName);
        team.setImage(image);

        Set<User> studentUsers = new HashSet<>();
        for (StudentIdentifierDTO studentId : studentsOrEmpty()) {
            studentUsers.add(studentId.toUser());
        }
        team.setStudents(studentUsers);

        return team;
    }
}
