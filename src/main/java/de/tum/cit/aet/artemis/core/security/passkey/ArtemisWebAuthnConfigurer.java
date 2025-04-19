package de.tum.cit.aet.artemis.core.security.passkey;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.config.annotation.web.configurers.WebAuthnConfigurer;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.authentication.PublicKeyCredentialRequestOptionsFilter;
import org.springframework.security.web.webauthn.authentication.PublicKeyCredentialRequestOptionsRepository;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;
import org.springframework.security.web.webauthn.registration.PublicKeyCredentialCreationOptionsRepository;
import org.springframework.security.web.webauthn.registration.WebAuthnRegistrationFilter;

import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.jwt.JWTCookieService;

/**
 * Configures WebAuthn for Spring Security applications using a custom {@link ArtemisWebAuthnAuthenticationFilter}.
 * In contrast to the Spring Security implementation, we want to set a JWT in the response.
 *
 * @see WebAuthnConfigurer on which this class is based and which can probably replace this custom class once a
 *      configuration of the in {@link WebAuthnAuthenticationFilter} configured
 *      {@link org.springframework.security.web.authentication.HttpMessageConverterAuthenticationSuccessHandler}
 *      can be modified / overwriten by specifying a bean.
 */
public class ArtemisWebAuthnConfigurer<H extends HttpSecurityBuilder<H>> extends WebAuthnConfigurer<H> {

    private static final Logger log = LoggerFactory.getLogger(ArtemisWebAuthnConfigurer.class);

    /**
     * Typically the domain of the website that is using WebAuthn for authentication.
     */
    private String relyingPartyId;

    private String relyingPartyName;

    /**
     * To ensure that only trusted parties can create and authenticate with passkeys
     */
    private Set<String> allowedOrigins = new HashSet<>();

    private final HttpMessageConverter<Object> converter;

    private final JWTCookieService jwtCookieService;

    private final PublicKeyCredentialUserEntityRepository publicKeyCredentialUserEntityRepository;

    private final UserCredentialRepository userCredentialRepository;

    private final UserRepository userRepository;

    private final PublicKeyCredentialCreationOptionsRepository publicKeyCredentialCreationOptionsRepository;

    private final PublicKeyCredentialRequestOptionsRepository publicKeyCredentialRequestOptionsRepository;

    public ArtemisWebAuthnConfigurer(HttpMessageConverter<Object> converter, JWTCookieService jwtCookieService, UserRepository userRepository,
            PublicKeyCredentialUserEntityRepository publicKeyCredentialUserEntityRepository, UserCredentialRepository userCredentialRepository,
            PublicKeyCredentialCreationOptionsRepository publicKeyCredentialCreationOptionsRepository,
            PublicKeyCredentialRequestOptionsRepository publicKeyCredentialRequestOptionsRepository) {
        this.converter = converter;
        this.jwtCookieService = jwtCookieService;
        this.publicKeyCredentialUserEntityRepository = publicKeyCredentialUserEntityRepository;
        this.userCredentialRepository = userCredentialRepository;
        this.userRepository = userRepository;
        this.publicKeyCredentialCreationOptionsRepository = publicKeyCredentialCreationOptionsRepository;
        this.publicKeyCredentialRequestOptionsRepository = publicKeyCredentialRequestOptionsRepository;
    }

    /**
     * The Relying Party id.
     *
     * @param relyingPartyId the relying party id
     * @return the {@link ArtemisWebAuthnConfigurer} for further customization
     */
    public ArtemisWebAuthnConfigurer<H> rpId(String relyingPartyId) {
        log.info("RelyingPartyId: {}", relyingPartyId);
        this.relyingPartyId = relyingPartyId;
        return this;
    }

    /**
     * Sets the relying party name
     *
     * @param rpName the relying party name
     * @return the {@link ArtemisWebAuthnConfigurer} for further customization
     */
    public ArtemisWebAuthnConfigurer<H> rpName(String rpName) {
        this.relyingPartyName = rpName;
        return this;
    }

    /**
     * Convenience method for {@link #allowedOrigins(Set)}
     *
     * @param allowedOrigins the allowed origins
     * @return the {@link ArtemisWebAuthnConfigurer} for further customization
     * @see #allowedOrigins(Set)
     */
    public ArtemisWebAuthnConfigurer<H> allowedOrigins(String... allowedOrigins) {
        log.info("AllowedOrigins: {}", Set.of(allowedOrigins));
        return allowedOrigins(Set.of(allowedOrigins));
    }

    /**
     * Sets the allowed origins.
     *
     * @param allowedOrigins the allowed origins
     * @return the {@link ArtemisWebAuthnConfigurer} for further customization
     * @see #allowedOrigins(String...)
     */
    public ArtemisWebAuthnConfigurer<H> allowedOrigins(Set<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
        return this;
    }

    @Override
    public void configure(H http) throws Exception {
        WebAuthnRelyingPartyOperations rpOperations = webAuthnRelyingPartyOperations(publicKeyCredentialUserEntityRepository, userCredentialRepository);
        WebAuthnAuthenticationFilter webAuthnAuthnFilter = new ArtemisWebAuthnAuthenticationFilter(converter, jwtCookieService, publicKeyCredentialRequestOptionsRepository);

        // we need to use custom repositories to ensure that multinode systems share challenges created in option requests
        // the default implementation only works on single-node systems (at least on Spring Security version 6.4.4)
        var createCredentialOptionsFilter = new ArtemisPublicKeyCredentialCreationOptionsFilter(rpOperations);
        createCredentialOptionsFilter.setCreationOptionsRepository(publicKeyCredentialCreationOptionsRepository);

        var webAuthnRegistrationFilter = new WebAuthnRegistrationFilter(userCredentialRepository, rpOperations);
        webAuthnRegistrationFilter.setCreationOptionsRepository(publicKeyCredentialCreationOptionsRepository);

        var authOptionsFilter = new PublicKeyCredentialRequestOptionsFilter(rpOperations);
        authOptionsFilter.setRequestOptionsRepository(publicKeyCredentialRequestOptionsRepository);

        webAuthnAuthnFilter.setAuthenticationManager(new ProviderManager(new ArtemisWebAuthnAuthenticationProvider(rpOperations, userRepository)));
        http.addFilterBefore(webAuthnAuthnFilter, BasicAuthenticationFilter.class);
        http.addFilterAfter(webAuthnRegistrationFilter, AuthorizationFilter.class);
        http.addFilterBefore(createCredentialOptionsFilter, AuthorizationFilter.class);
        http.addFilterBefore(authOptionsFilter, AuthorizationFilter.class);
    }

    private <T> Optional<T> getBeanOrNull(Class<T> type) {
        ApplicationContext context = getBuilder().getSharedObject(ApplicationContext.class);
        if (context == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(context.getBean(type));
        }
        catch (NoSuchBeanDefinitionException ex) {
            return Optional.empty();
        }
    }

    private WebAuthnRelyingPartyOperations webAuthnRelyingPartyOperations(PublicKeyCredentialUserEntityRepository userEntities, UserCredentialRepository userCredentials) {
        if (relyingPartyId == null || relyingPartyName == null) {
            throw new IllegalStateException(
                    "WebAuthn relyingPartyId and relyingPartyName must be configured for passkey authentication; rpId: " + relyingPartyId + ", rpName: " + relyingPartyName);
        }
        Optional<WebAuthnRelyingPartyOperations> webauthnOperationsBean = getBeanOrNull(WebAuthnRelyingPartyOperations.class);
        return webauthnOperationsBean.orElseGet(() -> new Webauthn4JRelyingPartyOperations(userEntities, userCredentials,
                PublicKeyCredentialRpEntity.builder().id(this.relyingPartyId).name(this.relyingPartyName).build(), this.allowedOrigins));
    }
}
