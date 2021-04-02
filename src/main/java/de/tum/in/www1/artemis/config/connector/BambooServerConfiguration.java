package de.tum.in.www1.artemis.config.connector;

import java.net.URL;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

import com.atlassian.bamboo.specs.util.*;

@Configuration
@Profile("bamboo")
public class BambooServerConfiguration {

    @Value("${artemis.continuous-integration.token}")
    private String bambooToken;

    @Value("${artemis.continuous-integration.url}")
    private URL bambooServerUrl;

    /**
     * initializes the bamboo server with the provided token (if available) or with username and password (fallback that will be removed soon)
     * @return the initialized BambooServer object that can be used to publish build plans
     */
    @Bean
    public BambooServer bambooServer() {
        TokenCredentials tokenCredentials = new SimpleTokenCredentials(bambooToken);
        return new BambooServer(bambooServerUrl.toString(), tokenCredentials);
    }
}
