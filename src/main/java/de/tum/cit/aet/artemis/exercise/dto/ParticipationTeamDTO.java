package de.tum.cit.aet.artemis.exercise.dto;

import java.util.List;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.UserPublicInfoDTO;
import de.tum.cit.aet.artemis.exercise.domain.Team;

/**
 * Safe team information included in a participation response.
 *
 * @param id        the unique identifier of the team
 * @param name      the display name of the team, if available
 * @param shortName the short name used to identify the team, if available
 * @param image     the team image path, if available
 * @param students  public information for initialized team members, or absent when members were not loaded
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationTeamDTO(Long id, @Nullable String name, @Nullable String shortName, @Nullable String image, @Nullable List<UserPublicInfoDTO> students) {

    /**
     * Creates a safe team response without initializing the team's students.
     *
     * @param team the team to map after participant visibility has been checked
     * @return the safe team response
     */
    public static ParticipationTeamDTO of(Team team) {
        List<UserPublicInfoDTO> students = null;
        if (team.getStudents() != null && Hibernate.isInitialized(team.getStudents())) {
            students = team.getStudents().stream().map(UserPublicInfoDTO::new).toList();
        }
        return new ParticipationTeamDTO(team.getId(), team.getName(), team.getShortName(), team.getImage(), students);
    }
}
