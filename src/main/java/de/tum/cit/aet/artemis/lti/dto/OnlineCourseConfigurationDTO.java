package de.tum.cit.aet.artemis.lti.dto;

import java.util.Objects;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lti.domain.OnlineCourseConfiguration;

/**
 * DTO for exposing and updating online course LTI configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OnlineCourseConfigurationDTO(Long id, @NotBlank String userPrefix, @NotNull Boolean requireExistingUser,
        @Valid @Nullable LtiPlatformConfigurationDTO ltiPlatformConfiguration) {

    public static OnlineCourseConfigurationDTO of(OnlineCourseConfiguration config) {
        return new OnlineCourseConfigurationDTO(config.getId(), config.getUserPrefix(), config.isRequireExistingUser(),
                config.getLtiPlatformConfiguration() == null ? null : LtiPlatformConfigurationDTO.of(config.getLtiPlatformConfiguration()));
    }

    /**
     * Creates an online course configuration entity from the given DTO.
     *
     * The linked platform is intentionally not reconstructed from request data. Controllers must
     * attach the managed platform entity explicitly to avoid detached-entity issues and to keep
     * platform updates restricted to the admin endpoints.
     *
     * @param dto the DTO to convert
     * @return the corresponding entity
     */
    public static OnlineCourseConfiguration from(OnlineCourseConfigurationDTO dto) {
        OnlineCourseConfiguration config = new OnlineCourseConfiguration();
        config.setId(dto.id());
        config.setUserPrefix(Objects.requireNonNull(dto.userPrefix()));
        config.setRequireExistingUser(Objects.requireNonNull(dto.requireExistingUser()));
        return config;
    }
}
