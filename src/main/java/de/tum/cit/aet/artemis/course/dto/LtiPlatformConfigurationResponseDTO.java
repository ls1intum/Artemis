package de.tum.cit.aet.artemis.course.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration;

/**
 * LTI platform information needed by the course settings response.
 *
 * @param id               the platform identifier
 * @param registrationId   the platform registration identifier
 * @param clientId         the OAuth client identifier
 * @param authorizationUri the authorization endpoint
 * @param jwkSetUri        the JSON Web Key endpoint
 * @param tokenUri         the token endpoint
 * @param originalUrl      the optional original issuer URL
 * @param customName       the optional display name
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LtiPlatformConfigurationResponseDTO(long id, String registrationId, String clientId, String authorizationUri, String jwkSetUri, String tokenUri,
        @Nullable String originalUrl, @Nullable String customName) {

    /**
     * Maps an LTI platform without its online-course collection.
     *
     * @param configuration the platform configuration
     * @return the platform response
     */
    public static LtiPlatformConfigurationResponseDTO of(LtiPlatformConfiguration configuration) {
        return new LtiPlatformConfigurationResponseDTO(configuration.getId(), configuration.getRegistrationId(), configuration.getClientId(), configuration.getAuthorizationUri(),
                configuration.getJwkSetUri(), configuration.getTokenUri(), configuration.getOriginalUrl(), configuration.getCustomName());
    }
}
