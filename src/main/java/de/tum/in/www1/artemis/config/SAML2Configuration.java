package de.tum.in.www1.artemis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@Order(1)
@Profile("saml2")
public class SAML2Configuration extends WebSecurityConfigurerAdapter {

    @Bean
    RelyingPartyRegistrationRepository relyingPartyRegistrationRepository() {
        final RelyingPartyRegistration relyingPartyRegistration = RelyingPartyRegistrations
                .fromMetadataLocation("http://saml:8080/simplesaml/saml2/idp/metadata.php")
                .registrationId("testidp") // inititates flow by calling {baseurl}/saml2/authenticate/testidp
                .entityId("artemis")
                .build();
        return new InMemoryRelyingPartyRegistrationRepository(relyingPartyRegistration);
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
            .authorizeRequests()
                .antMatchers("/api/saml2").permitAll()
                .anyRequest().authenticated()
                .and()
            .saml2Login()
                .defaultSuccessUrl("/", true);
    }

}