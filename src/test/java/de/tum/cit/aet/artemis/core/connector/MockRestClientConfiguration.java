package de.tum.cit.aet.artemis.core.connector;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

/**
 * Registers {@link MockRestClientServers} in the test contexts with the {@code core} profile. The component scan of the application finds
 * it, like the request mock providers.
 * <p>
 * The {@link org.springframework.web.client.RestClient} beans exist only with the {@code core} profile, see
 * {@code RestClientConfiguration}. The profile also keeps the post-processor out of the contexts of the AI worker: they must not contain
 * a bean of the server, and {@code WorkerContextTest} fails if they do.
 */
@Lazy
@Profile(PROFILE_CORE)
@Configuration(proxyBeanMethods = false)
public class MockRestClientConfiguration {

    /**
     * A static method, because a post-processor has to exist before the configuration class can be instantiated.
     *
     * @return the post-processor that binds a mock server to every RestClient bean
     */
    @Bean
    public static MockRestClientServers mockRestClientServers() {
        return new MockRestClientServers();
    }
}
