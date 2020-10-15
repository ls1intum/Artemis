package de.tum.in.www1.artemis.config.connector;

import java.net.URL;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.atlassian.bamboo.specs.util.*;

@Configuration
@Profile("bamboo")
public class BambooServerConfiguration {

    @Value("${artemis.continuous-integration.user}")
    private String bambooUser;

    @Deprecated
    @Value("${artemis.continuous-integration.password}")
    private String bambooPassword;

    @Value("${artemis.continuous-integration.token:#{null}}")
    private Optional<String> bambooToken;

    @Value("${artemis.continuous-integration.url}")
    private URL bambooServerUrl;

    /**
     * initializes the bamboo server with the provided token (if available) or with username and password (fallback that will be removed soon)
     * @return the initialized BambooServer object that can be used to publish build plans
     */
    @Bean
    public BambooServer bambooServer() {
        if (bambooToken.isPresent()) {
            TokenCredentials tokenCredentials = new SimpleTokenCredentials(bambooToken.get());
            return new BambooServer(bambooServerUrl.toString(), tokenCredentials);
        }
        else {
            // supports the legacy case if BAMBOO_TOKEN is not available --> TODO: Remove soon, because user password credentials are deprecated
            UserPasswordCredentials userPasswordCredentials = new SimpleUserPasswordCredentials(bambooUser, bambooPassword);
            return new BambooServer(bambooServerUrl.toString(), userPasswordCredentials);
        }
    }
}
