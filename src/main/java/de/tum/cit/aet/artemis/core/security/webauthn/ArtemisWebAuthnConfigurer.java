package de.tum.cit.aet.artemis.core.security.webauthn;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.config.annotation.web.configurers.WebAuthnConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.authentication.PublicKeyCredentialRequestOptionsFilter;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationProvider;
import org.springframework.security.web.webauthn.management.MapPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.MapUserCredentialRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;
import org.springframework.security.web.webauthn.registration.PublicKeyCredentialCreationOptionsFilter;
import org.springframework.security.web.webauthn.registration.WebAuthnRegistrationFilter;

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

    /**
     * Typically the domain of the website that is using WebAuthn for authentication.
     */
    private String relyingPartyId;

    private String relyingPartyName;

    private Set<String> allowedOrigins = new HashSet<>();

    private final HttpMessageConverter<Object> converter;

    private final JWTCookieService jwtCookieService;

    public ArtemisWebAuthnConfigurer(HttpMessageConverter<Object> converter, JWTCookieService jwtCookieService) {
        this.converter = converter;
        this.jwtCookieService = jwtCookieService;
    }

    /**
     * The Relying Party id.
     *
     * @param relyingPartyId the relying party id
     * @return the {@link ArtemisWebAuthnConfigurer} for further customization
     */
    public ArtemisWebAuthnConfigurer<H> rpId(String relyingPartyId) {
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
        UserDetailsService userDetailsService = getSharedOrBean(http, UserDetailsService.class).orElseThrow(() -> new IllegalStateException("Missing UserDetailsService Bean"));
        PublicKeyCredentialUserEntityRepository userEntities = getSharedOrBean(http, PublicKeyCredentialUserEntityRepository.class).orElse(userEntityRepository());
        UserCredentialRepository userCredentials = getSharedOrBean(http, UserCredentialRepository.class).orElse(userCredentialRepository());
        WebAuthnRelyingPartyOperations rpOperations = webAuthnRelyingPartyOperations(userEntities, userCredentials);

        WebAuthnAuthenticationFilter webAuthnAuthnFilter = new ArtemisWebAuthnAuthenticationFilter(converter, jwtCookieService);

        webAuthnAuthnFilter.setAuthenticationManager(new ProviderManager(new WebAuthnAuthenticationProvider(rpOperations, userDetailsService)));
        http.addFilterBefore(webAuthnAuthnFilter, BasicAuthenticationFilter.class);
        http.addFilterAfter(new WebAuthnRegistrationFilter(userCredentials, rpOperations), AuthorizationFilter.class);
        http.addFilterBefore(new PublicKeyCredentialCreationOptionsFilter(rpOperations), AuthorizationFilter.class);
        http.addFilterBefore(new PublicKeyCredentialRequestOptionsFilter(rpOperations), AuthorizationFilter.class);
    }

    private <C> Optional<C> getSharedOrBean(H http, Class<C> type) {
        C shared = http.getSharedObject(type);
        return Optional.ofNullable(shared).or(() -> getBeanOrNull(type));
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

    private MapUserCredentialRepository userCredentialRepository() {
        return new MapUserCredentialRepository();
    }

    private PublicKeyCredentialUserEntityRepository userEntityRepository() {
        return new MapPublicKeyCredentialUserEntityRepository();
    }

    private WebAuthnRelyingPartyOperations webAuthnRelyingPartyOperations(PublicKeyCredentialUserEntityRepository userEntities, UserCredentialRepository userCredentials) {
        Optional<WebAuthnRelyingPartyOperations> webauthnOperationsBean = getBeanOrNull(WebAuthnRelyingPartyOperations.class);
        return webauthnOperationsBean.orElseGet(() -> new Webauthn4JRelyingPartyOperations(userEntities, userCredentials,
                PublicKeyCredentialRpEntity.builder().id(this.relyingPartyId).name(this.relyingPartyName).build(), this.allowedOrigins));
    }

}
