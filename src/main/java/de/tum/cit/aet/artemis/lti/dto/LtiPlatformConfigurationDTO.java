package de.tum.cit.aet.artemis.lti.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration;

/**
 * DTO for exposing LTI platform configurations via the REST API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LtiPlatformConfigurationDTO(@NotNull Long id, @NotBlank String registrationId, @NotBlank String clientId, @Nullable String originalUrl, @Nullable String customName,
        @NotBlank String authorizationUri, @NotBlank String jwkSetUri, @NotBlank String tokenUri) {

    public static LtiPlatformConfigurationDTO of(LtiPlatformConfiguration config) {
        return new LtiPlatformConfigurationDTO(config.getId(), config.getRegistrationId(), config.getClientId(), config.getOriginalUrl(), config.getCustomName(),
                config.getAuthorizationUri(), config.getJwkSetUri(), config.getTokenUri());
    }
}
