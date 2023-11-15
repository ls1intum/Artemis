package de.tum.in.www1.artemis.domain;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

    @Column(name = "issuer")
    private String issuer;

    @Column(name = "authorization_uri")
    private String authorizationUri;

    @Column(name = "jkw_set_uri")
    private String jwkSetUri;

    @Column(name = "token_uri")
    private String tokenUri;

    @OneToMany(mappedBy = "ltiPlatformConfiguration", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties(value = "onlineCourseConfigurations", allowSetters = true)
    private Set<OnlineCourseConfiguration> onlineCourseConfigurations = new HashSet<>();

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

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Set<OnlineCourseConfiguration> getOnlineCourseConfigurations() {
        return onlineCourseConfigurations;
    }

    public void setOnlineCourseConfigurations(Set<OnlineCourseConfiguration> onlineCourseConfigurations) {
        this.onlineCourseConfigurations = onlineCourseConfigurations;
    }

    public void addOnlineCourseConfiguration(OnlineCourseConfiguration onlineCourseConfiguration) {
        this.onlineCourseConfigurations.add(onlineCourseConfiguration);
        onlineCourseConfiguration.setLtiPlatformConfiguration(this);
    }
}
