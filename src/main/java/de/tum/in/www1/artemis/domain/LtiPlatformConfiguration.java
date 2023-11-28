package de.tum.in.www1.artemis.domain;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "lti_platform_configuration")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LtiPlatformConfiguration extends DomainObject {

    public static final String ENTITY_NAME = "ltiPlatformConfiguration";

    @Column(name = "registration_id")
    private String registrationId;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "original_url")
    private String originalUrl;

    @Column(name = "custom_name")
    private String customName;

    @Column(name = "authorization_uri")
    private String authorizationUri;

    @Column(name = "jwk_set_uri")
    private String jwkSetUri;

    @Column(name = "token_uri")
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

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String issuer) {
        this.originalUrl = issuer;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

}
