package de.tum.cit.aet.artemis.core.config;

import java.security.Security;
import java.time.Duration;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.saml2.provider.service.metadata.OpenSaml5MetadataResolver;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.security.saml2.provider.service.web.DefaultRelyingPartyRegistrationResolver;
import org.springframework.security.saml2.provider.service.web.RelyingPartyRegistrationResolver;
import org.springframework.security.saml2.provider.service.web.Saml2MetadataFilter;
import org.springframework.security.saml2.provider.service.web.authentication.OpenSaml5AuthenticationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;

import de.tum.cit.aet.artemis.core.repository.saml2.HazelcastSaml2RedirectUriRepository;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.core.security.saml2.SAML2ExternalClientAuthenticationSuccessHandler;
import de.tum.cit.aet.artemis.core.security.saml2.SAML2RedirectUriValidator;
import de.tum.cit.aet.artemis.core.security.saml2.Saml2RelayStateResolver;
import de.tum.cit.aet.artemis.core.service.connectors.SAML2Service;

/**
 * Describes the security configuration for SAML2.
 */
@Configuration
@Lazy
@Conditional(Saml2Enabled.class)
public class SAML2Configuration {

    private final SAML2Properties saml2Properties;

    private final SAML2Service saml2Service;

    private final TokenProvider tokenProvider;

    private final HazelcastSaml2RedirectUriRepository redirectUriRepository;

    private final AuditEventRepository auditEventRepository;

    /**
     * Constructs a new instance.
     *
     * @param saml2Properties       SAML2 configuration properties
     * @param saml2Service          SAML2 user handling service
     * @param tokenProvider         JWT token provider
     * @param redirectUriRepository Hazelcast nonce store
     * @param auditEventRepository  audit event repository
     */
    public SAML2Configuration(SAML2Properties saml2Properties, SAML2Service saml2Service, TokenProvider tokenProvider, HazelcastSaml2RedirectUriRepository redirectUriRepository,
            AuditEventRepository auditEventRepository) {
        // SAML2 / Shibboleth uses several algorithms that are provided by BouncyCastle
        Security.addProvider(new BouncyCastleProvider());
        this.saml2Properties = saml2Properties;
        this.saml2Service = saml2Service;
        this.tokenProvider = tokenProvider;
        this.redirectUriRepository = redirectUriRepository;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * Returns the RelyingPartyRegistrationRepository used by SAML2 configuration.
     * <p>
     * The relying parties are configured in the SAML2 properties. A helper method
     * {@link RelyingPartyRegistrations#fromMetadataLocation} extracts the needed information from the given
     * XML metadata file. Optionally X509 Credentials can be supplied to enable encryption.
     *
     * @return the RelyingPartyRegistrationRepository used by SAML2 configuration.
     */
    @Bean
    RelyingPartyRegistrationResolver relyingPartyRegistrationResolver(RelyingPartyRegistrationRepository registrations) {
        return new DefaultRelyingPartyRegistrationResolver(registrations);
    }

    @Bean
    FilterRegistrationBean<Saml2MetadataFilter> metadata(RelyingPartyRegistrationResolver registrations) {
        Saml2MetadataFilter metadata = new Saml2MetadataFilter(registrations, new OpenSaml5MetadataResolver());
        FilterRegistrationBean<Saml2MetadataFilter> filter = new FilterRegistrationBean<>(metadata);
        filter.setOrder(-101);
        return filter;
    }

    /**
     * Since this configuration is annotated with {@link Order} and {@link SecurityConfiguration}
     * is not, this configuration is evaluated first when the SAML2 Profile is active.
     *
     * @param http          The Spring http security configurer.
     * @param registrations The relying party registration resolver.
     * @return The configured http security filter chain.
     * @throws Exception Thrown in case Spring detects an issue with the security configuration.
     */
    @Bean
    @Order(1)
    protected SecurityFilterChain saml2FilterChain(final HttpSecurity http, RelyingPartyRegistrationResolver registrations) throws Exception {
        SAML2RedirectUriValidator validator = new SAML2RedirectUriValidator(saml2Properties.getAllowedRedirectSchemes());

        // Configure authentication request resolver with optional redirect_uri support
        OpenSaml5AuthenticationRequestResolver authRequestResolver = new OpenSaml5AuthenticationRequestResolver(registrations);
        authRequestResolver.setRelayStateResolver(new Saml2RelayStateResolver(validator, redirectUriRepository));

        // Configure success handler
        Duration tokenTtl = Duration.ofMinutes(saml2Properties.getExternalRedirectTokenTtlMinutes());
        SAML2ExternalClientAuthenticationSuccessHandler successHandler = new SAML2ExternalClientAuthenticationSuccessHandler(redirectUriRepository, saml2Service, tokenProvider,
                auditEventRepository, validator, tokenTtl);

        // @formatter:off
        http
            // This filter chain is only applied if the URL matches
            // Else the request is filtered by {@link SecurityConfiguration}.
            .securityMatcher("/api/core/public/saml2", "/saml2/**", "/login/saml2/**")
            // Needed for SAML to work properly
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                // The request to the api is permitted and checked directly
                // This allows returning a 401 if the user is not logged in via SAML2
                // to notify the client that a login is needed.
                .requestMatchers("/api/core/public/saml2").permitAll()
                // Every other request must be authenticated. Any request triggers a SAML2
                // authentication flow
                .anyRequest().authenticated()
            )
            // Processes the RelyingPartyRegistrationRepository Bean and installs the filters for SAML2
            .saml2Login(config -> config
                .authenticationRequestResolver(authRequestResolver)
                .successHandler(successHandler)
            );
        // @formatter:on

        return http.build();
    }
}
