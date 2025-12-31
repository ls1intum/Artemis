package de.tum.cit.aet.artemis.lti.dto;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration;

/**
 * DTO for creating and updating LtiPlatformConfiguration.
 * Uses DTOs instead of entity classes to avoid Hibernate detached entity issues.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LtiPlatformConfigurationUpdateDTO(@Nullable Long id, @Nullable String registrationId, @NotNull String clientId, @Nullable String originalUrl,
        @Nullable String customName, @NotNull String authorizationUri, @NotNull String jwkSetUri, @NotNull String tokenUri) {

    /**
     * Creates a LtiPlatformConfigurationUpdateDTO from the given LtiPlatformConfiguration domain object.
     *
     * @param config the LtiPlatformConfiguration to convert
     * @return the corresponding DTO
     */
    public static LtiPlatformConfigurationUpdateDTO of(LtiPlatformConfiguration config) {
        return new LtiPlatformConfigurationUpdateDTO(config.getId(), config.getRegistrationId(), config.getClientId(), config.getOriginalUrl(), config.getCustomName(),
                config.getAuthorizationUri(), config.getJwkSetUri(), config.getTokenUri());
    }

    /**
     * Creates a new LtiPlatformConfiguration entity from this DTO.
     * Used for create operations.
     *
     * @return a new LtiPlatformConfiguration entity
     */
    public LtiPlatformConfiguration toEntity() {
        LtiPlatformConfiguration config = new LtiPlatformConfiguration();
        config.setClientId(clientId);
        config.setOriginalUrl(originalUrl);
        config.setCustomName(customName);
        config.setAuthorizationUri(authorizationUri);
        config.setJwkSetUri(jwkSetUri);
        config.setTokenUri(tokenUri);
        return config;
    }

    /**
     * Applies the DTO values to an existing LtiPlatformConfiguration entity.
     * This updates the managed entity with values from the DTO.
     * Note: registrationId is NOT updated as it should remain immutable after creation.
     *
     * @param config the existing configuration to update
     */
    public void applyTo(LtiPlatformConfiguration config) {
        config.setClientId(clientId);
        config.setOriginalUrl(originalUrl);
        config.setCustomName(customName);
        config.setAuthorizationUri(authorizationUri);
        config.setJwkSetUri(jwkSetUri);
        config.setTokenUri(tokenUri);
    }
}
