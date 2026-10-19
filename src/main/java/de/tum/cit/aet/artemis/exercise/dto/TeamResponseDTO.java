package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Team;

/**
 * A team as the team management pages render it.
 * <p>
 * The auditing fields are part of the contract: {@link Team} re-enables them with {@code @JsonIgnore(false)}, so the
 * entity payload carried them and the team detail header renders them.
 *
 * @param id               the id of the team
 * @param name             the team name
 * @param shortName        the team short name, which identifies the team across the exercises of a course
 * @param image            the team image, when one was set
 * @param createdBy        the login of the user who created the team
 * @param createdDate      when the team was created
 * @param lastModifiedBy   the login of the user who last changed the team
 * @param lastModifiedDate when the team was last changed
 * @param owner            the tutor owning the team, when one was assigned
 * @param students         the team members; absent when the students are not loaded
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TeamResponseDTO(Long id, String name, String shortName, String image, String createdBy, Instant createdDate, String lastModifiedBy, Instant lastModifiedDate,
        TeamMemberDTO owner, List<TeamMemberDTO> students) implements Serializable {

    /**
     * Converts a {@link Team} into a {@link TeamResponseDTO}. The members are only mapped when the {@code students}
     * collection is already initialized; this never triggers a lazy load.
     *
     * @param team the team to convert (may be {@code null})
     * @return the converted DTO, or {@code null} if the input was {@code null}
     */
    public static TeamResponseDTO of(Team team) {
        if (team == null) {
            return null;
        }
        List<TeamMemberDTO> students = null;
        if (Hibernate.isInitialized(team.getStudents()) && team.getStudents() != null) {
            students = team.getStudents().stream().map(TeamMemberDTO::of).toList();
        }
        return new TeamResponseDTO(team.getId(), team.getName(), team.getShortName(), team.getImage(), team.getCreatedBy(), team.getCreatedDate(), team.getLastModifiedBy(),
                team.getLastModifiedDate(), TeamMemberDTO.of(team.getOwner()), students);
    }

    /**
     * Converts a list of {@link Team}s into their DTOs.
     *
     * @param teams the teams to convert
     * @return the converted DTOs
     */
    public static List<TeamResponseDTO> of(List<Team> teams) {
        return teams.stream().map(TeamResponseDTO::of).toList();
    }
}
