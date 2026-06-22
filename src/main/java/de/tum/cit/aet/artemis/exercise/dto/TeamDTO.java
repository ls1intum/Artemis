package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;
import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.UserNameDTO;
import de.tum.cit.aet.artemis.exercise.domain.Team;

/**
 * Minimal DTO identifying the team participant of a team participation (id, name, short name) plus the team members
 * (id, login, name). The members are required client-side to verify participation ownership for the owning student
 * (the client matches the logged-in login against {@code team.students[*].login}); without them the text editor cannot
 * confirm ownership. Simple DTO: only scalar values and other DTOs, no entity references.
 *
 * @param id        the team id
 * @param name      the team name
 * @param shortName the team short name
 * @param students  the team members (id, login, name); {@code null}/omitted when the team's students are not loaded
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TeamDTO(Long id, String name, String shortName, List<UserNameDTO> students) implements Serializable {

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
        List<UserNameDTO> students = null;
        if (Hibernate.isInitialized(team.getStudents()) && team.getStudents() != null) {
            students = team.getStudents().stream().map(UserNameDTO::of).toList();
        }
        return new TeamDTO(team.getId(), team.getName(), team.getShortName(), students);
    }
}
