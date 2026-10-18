package de.tum.cit.aet.artemis.lti.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.AggregateRoot;
import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * Represents the configuration for an LTI platform.
 * Stores details such as registration ID, client ID, and various URIs needed for LTI communication.
 */
@Entity
@Table(name = "lti_platform_configuration")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@AggregateRoot("Server-wide platform registration.")
public class LtiPlatformConfiguration extends DomainObject {

    /** Entity name for LTI platform configuration. */
    public static final String ENTITY_NAME = "ltiPlatformConfiguration";

    /** Maximum length of the registration id, the client id and the URIs. Matches the width of the database columns. */
    public static final int MAX_FIELD_LENGTH = 255;

    /** Maximum length of the custom name. Matches the width of the database column. */
    public static final int MAX_CUSTOM_NAME_LENGTH = 50;

    @NonNull
    @Size(max = MAX_FIELD_LENGTH)
    @Column(name = "registration_id", nullable = false)
    private String registrationId;

    @NonNull
    @Size(max = MAX_FIELD_LENGTH)
    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Nullable
    @Size(max = MAX_FIELD_LENGTH)
    @Column(name = "original_url")
    private String originalUrl;

    @Nullable
    @Size(max = MAX_CUSTOM_NAME_LENGTH)
    @Column(name = "custom_name")
    private String customName;

    @NonNull
    @Size(max = MAX_FIELD_LENGTH)
    @Column(name = "authorization_uri", nullable = false)
    private String authorizationUri;

    @NonNull
    @Size(max = MAX_FIELD_LENGTH)
    @Column(name = "jwk_set_uri", nullable = false)
    private String jwkSetUri;

    @NonNull
    @Size(max = MAX_FIELD_LENGTH)
    @Column(name = "token_uri", nullable = false)
    private String tokenUri;

    public String getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(String registrationId) {
        this.registrationId = registrationId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getAuthorizationUri() {
        return authorizationUri;
    }

    public void setAuthorizationUri(String authorizationUri) {
        this.authorizationUri = authorizationUri;
    }

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }

    public String getTokenUri() {
        return tokenUri;
    }

    public void setTokenUri(String tokenUri) {
        this.tokenUri = tokenUri;
    }

    @Nullable
    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(@Nullable String originalUrl) {
        this.originalUrl = originalUrl;
    }

    @Nullable
    public String getCustomName() {
        return customName;
    }

    public void setCustomName(@Nullable String customName) {
        this.customName = customName;
    }

}
