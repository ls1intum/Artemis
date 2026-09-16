package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;
import java.util.List;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.UserPublicInfoDTO;
import de.tum.cit.aet.artemis.exercise.domain.Team;

/**
 * Minimal DTO identifying the team participant of a team participation (id, name, short name, image) plus the team
 * members. The members are required client-side to verify participation ownership for the owning student (the client
 * matches the logged-in login against {@code team.students[*].login}); without them the text editor cannot confirm
 * ownership. Simple DTO: only scalar values and other DTOs, no entity references.
 *
 * @param id        the team id
 * @param name      the team name
 * @param shortName the team short name
 * @param image     the team image path, if available
 * @param students  the team members; {@code null}/omitted when the team's students are not loaded
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TeamDTO(Long id, String name, String shortName, @Nullable String image, List<UserPublicInfoDTO> students) implements Serializable {

    /**
     * Converts a {@link Team} into a {@link TeamDTO}. The team members are only mapped when the {@code students}
     * collection is already initialized; this never triggers a lazy load.
     *
     * @param team the team to convert (may be {@code null})
     * @return the converted DTO, or {@code null} if the input was {@code null}
     */
    public static TeamDTO of(Team team) {
        if (team == null) {
            return null;
        }
        List<UserPublicInfoDTO> students = null;
        if (team.getStudents() != null && Hibernate.isInitialized(team.getStudents())) {
            students = team.getStudents().stream().map(UserPublicInfoDTO::new).toList();
        }
        return new TeamDTO(team.getId(), team.getName(), team.getShortName(), team.getImage(), students);
    }
}
