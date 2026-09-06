package de.tum.cit.aet.artemis.fileupload.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.TeamAssignmentConfig;

/**
 * DTO for the team-assignment settings of a file upload exercise.
 *
 * @param id          the persisted configuration identifier, if present in a response
 * @param minTeamSize the minimum number of students in a team
 * @param maxTeamSize the maximum number of students in a team
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadTeamAssignmentConfigDTO(@Nullable Long id, @Nullable Integer minTeamSize, @Nullable Integer maxTeamSize) {

    /**
     * Creates a response DTO from a team-assignment configuration.
     *
     * @param config the configuration to map
     * @return the mapped configuration
     */
    public static FileUploadTeamAssignmentConfigDTO of(TeamAssignmentConfig config) {
        return new FileUploadTeamAssignmentConfigDTO(config.getId(), config.getMinTeamSize(), config.getMaxTeamSize());
    }

    /**
     * Creates a new configuration for a create or import request.
     * The DTO identifier is intentionally ignored so that requests cannot attach an existing configuration.
     *
     * @return a new team-assignment configuration
     */
    public TeamAssignmentConfig toNewEntity() {
        TeamAssignmentConfig config = new TeamAssignmentConfig();
        config.setMinTeamSize(minTeamSize);
        config.setMaxTeamSize(maxTeamSize);
        return config;
    }
}
