package de.tum.in.www1.artemis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;

@Configuration
@Profile("saml2")
public class SAML2Configuration {

    @Bean
    RelyingPartyRegistrationRepository relyingPartyRegistrationRepository() {
        final RelyingPartyRegistration relyingPartyRegistration = RelyingPartyRegistrations
                .fromMetadataLocation("http://saml:8080/simplesaml/saml2/idp/metadata.php")
                .registrationId("testidp") // inititates flow by calling {baseurl}/saml2/authenticate/testidp
                .entityId("artemis")
                .build();
        return new InMemoryRelyingPartyRegistrationRepository(relyingPartyRegistration);
    }

}