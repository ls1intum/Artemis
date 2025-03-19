package de.tum.cit.aet.artemis.core.config;

import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.saml2.provider.service.metadata.OpenSaml5MetadataResolver;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.security.saml2.provider.service.web.DefaultRelyingPartyRegistrationResolver;
import org.springframework.security.saml2.provider.service.web.RelyingPartyRegistrationResolver;
import org.springframework.security.saml2.provider.service.web.Saml2MetadataFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Describes the security configuration for SAML2.
 */
@Configuration
@Profile(Constants.PROFILE_SAML2)
public class SAML2Configuration {

    /**
     * Constructs a new instance.
     */
    public SAML2Configuration() {
        // TODO: we should describe why this line is actually needed
        Security.addProvider(new BouncyCastleProvider());
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
     * @param http The Spring http security configurer.
     * @return The configured http security filter chain.
     * @throws Exception Thrown in case Spring detects an issue with the security configuration.
     */
    @Bean
    @Order(1)
    protected SecurityFilterChain saml2FilterChain(final HttpSecurity http) throws Exception {
        // @formatter:off
        http
            // This filter chain is only applied if the URL matches
            // Else the request is filtered by {@link SecurityConfiguration}.
            .securityMatcher("/api/core/public/saml2", "/saml2/**", "/login/**")
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
            // Redirect back to the root
            .saml2Login((config) -> config.defaultSuccessUrl("/", true));
        // @formatter:on

        return http.build();
    }
}
