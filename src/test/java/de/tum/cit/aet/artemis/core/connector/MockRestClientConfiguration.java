package de.tum.cit.aet.artemis.core.connector;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Registers {@link MockRestClientServers} in every test context. The component scan of the application finds it, like the request mock
 * providers. The post-processor ignores a context without a {@link org.springframework.web.client.RestClient} bean.
 */
@Lazy
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
