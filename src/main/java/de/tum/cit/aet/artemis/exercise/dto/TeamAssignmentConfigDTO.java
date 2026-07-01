package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.TeamAssignmentConfig;

/**
 * DTO holding the team assignment configuration of an exercise.
 * Dumb DTO: only scalar values, no entity references.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TeamAssignmentConfigDTO(Long id, Integer minTeamSize, Integer maxTeamSize) implements Serializable {

    /**
     * Creates a {@link TeamAssignmentConfigDTO} from the given {@link TeamAssignmentConfig}.
     *
     * @param teamAssignmentConfig the entity to convert (may be {@code null})
     * @return the corresponding DTO, or {@code null} if the input was {@code null}
     */
    public static TeamAssignmentConfigDTO of(TeamAssignmentConfig teamAssignmentConfig) {
        return Optional.ofNullable(teamAssignmentConfig).map(config -> new TeamAssignmentConfigDTO(config.getId(), config.getMinTeamSize(), config.getMaxTeamSize())).orElse(null);
    }

    /**
     * Builds a transient {@link TeamAssignmentConfig} entity from this DTO (min/max team size only).
     *
     * @return the transient entity
     */
    public TeamAssignmentConfig toEntity() {
        TeamAssignmentConfig config = new TeamAssignmentConfig();
        config.setMinTeamSize(minTeamSize);
        config.setMaxTeamSize(maxTeamSize);
        return config;
    }
}
