package de.tum.in.www1.artemis.config;

import java.util.LinkedList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;

/**
 * This class describes the security configuration for SAML2.
 */
@Configuration
@Order(1)
@Profile("saml2")
public class SAML2Configuration extends WebSecurityConfigurerAdapter {

    private final SAML2Properties properties;

    /**
     * Constructs a new instance.
     *
     * @param      properties  The SAML2 properties
     */
    public SAML2Configuration(final SAML2Properties properties) {
        this.properties = properties;
    }

    /**
     * Returns the RelyingPartyRegistrationRepository used by SAML2 configuration.
     *
     * @return the RelyingPartyRegistrationRepository used by SAML2 configuration.
     */
    @Bean
    RelyingPartyRegistrationRepository relyingPartyRegistrationRepository() {
        final List<RelyingPartyRegistration> relyingPartyRegistrations = new LinkedList<>();

        for (SAML2Properties.RelyingPartyProperties config : properties.getIdentityProviders()) {
            relyingPartyRegistrations.add(RelyingPartyRegistrations
                .fromMetadataLocation(config.getMetadata())
                .registrationId(config.getRegistrationId())
                .entityId(config.getEntityId())
                .build());
        }

        return new InMemoryRelyingPartyRegistrationRepository(relyingPartyRegistrations);
    }

    @Override
    protected void configure(final HttpSecurity http) throws Exception {
        // @formatter:off
        http
            .requestMatchers()
                .antMatchers("/api/saml2")
                .antMatchers("/saml2/**")
                .antMatchers("/login/**")
                .and()
            .csrf()
                .disable()
            .authorizeRequests()
                .antMatchers("/api/saml2").permitAll()
                .anyRequest().authenticated()
                .and()
            .saml2Login()
                .defaultSuccessUrl("/", true);
    }

}