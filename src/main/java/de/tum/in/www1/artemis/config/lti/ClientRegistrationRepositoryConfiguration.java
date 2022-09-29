package de.tum.in.www1.artemis.config.lti;

import java.util.*;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientPropertiesRegistrationAdapter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * Registers the default spring security oauth2 ClientRegistrationRepository if proper configuration was found.
 * Otherwise an empty fallback-repository is registered to avoid an error due to the missing ClientRegistrationRepository Bean.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OAuth2ClientProperties.class)
class ClientRegistrationRepositoryConfiguration {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(OAuth2ClientProperties properties) {
        List<ClientRegistration> registrations = new ArrayList<>(OAuth2ClientPropertiesRegistrationAdapter.getClientRegistrations(properties).values());
        if (registrations.isEmpty()) {
            return new FallbackClientRegistrationRepository();
        }
        return new InMemoryClientRegistrationRepository(registrations);
    }

    private static class FallbackClientRegistrationRepository implements ClientRegistrationRepository, Iterable<ClientRegistration> {

        @Override
        public ClientRegistration findByRegistrationId(String s) {
            return null;
        }

        @NotNull
        @Override
        public Iterator<ClientRegistration> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public void forEach(Consumer<? super ClientRegistration> action) {
            Iterable.super.forEach(action);
        }

        @Override
        public Spliterator<ClientRegistration> spliterator() {
            return Iterable.super.spliterator();
        }
    }
}
