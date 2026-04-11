package de.tum.cit.aet.artemis.lti.dto;

import java.util.Objects;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration;
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

    public static OnlineCourseConfiguration from(OnlineCourseConfigurationDTO dto) {
        OnlineCourseConfiguration config = new OnlineCourseConfiguration();
        config.setId(dto.id());
        config.setUserPrefix(Objects.requireNonNull(dto.userPrefix()));
        config.setRequireExistingUser(Objects.requireNonNull(dto.requireExistingUser()));
        if (dto.ltiPlatformConfiguration() != null) {
            LtiPlatformConfigurationDTO platformDTO = dto.ltiPlatformConfiguration();
            LtiPlatformConfiguration platform = new LtiPlatformConfiguration();
            platform.setId(platformDTO.id());
            platform.setRegistrationId(platformDTO.registrationId());
            platform.setClientId(platformDTO.clientId());
            platform.setOriginalUrl(platformDTO.originalUrl());
            platform.setCustomName(platformDTO.customName());
            platform.setAuthorizationUri(platformDTO.authorizationUri());
            platform.setJwkSetUri(platformDTO.jwkSetUri());
            platform.setTokenUri(platformDTO.tokenUri());
            config.setLtiPlatformConfiguration(platform);
        }
        return config;
    }
}
