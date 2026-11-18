package de.tum.cit.aet.artemis.lti.dto;

import static de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration.MAX_CUSTOM_NAME_LENGTH;
import static de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration.MAX_FIELD_LENGTH;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration;

/**
 * DTO for exposing LTI platform configurations via the REST API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LtiPlatformConfigurationDTO(@NotNull Long id, @NotBlank @Size(max = MAX_FIELD_LENGTH) String registrationId, @NotBlank @Size(max = MAX_FIELD_LENGTH) String clientId,
        @Nullable @Size(max = MAX_FIELD_LENGTH) String originalUrl, @Nullable @Size(max = MAX_CUSTOM_NAME_LENGTH) String customName,
        @NotBlank @Size(max = MAX_FIELD_LENGTH) String authorizationUri, @NotBlank @Size(max = MAX_FIELD_LENGTH) String jwkSetUri,
        @NotBlank @Size(max = MAX_FIELD_LENGTH) String tokenUri) {

    public static LtiPlatformConfigurationDTO of(LtiPlatformConfiguration config) {
        return new LtiPlatformConfigurationDTO(config.getId(), config.getRegistrationId(), config.getClientId(), config.getOriginalUrl(), config.getCustomName(),
                config.getAuthorizationUri(), config.getJwkSetUri(), config.getTokenUri());
    }
}
