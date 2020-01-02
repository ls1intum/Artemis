package de.tum.in.www1.artemis.config.connector.bamboo;

import java.net.URL;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.atlassian.bamboo.specs.util.BambooServer;
import com.atlassian.bamboo.specs.util.SimpleUserPasswordCredentials;
import com.atlassian.bamboo.specs.util.UserPasswordCredentials;

@Configuration
@Profile("bamboo")
public class BambooServerConfiguration {

    @Value("${artemis.bamboo.user}")
    private String BAMBOO_USER;

    @Value("${artemis.bamboo.password}")
    private String BAMBOO_PASSWORD;

    @Value("${artemis.bamboo.url}")
    private URL BAMBOO_SERVER_URL;

    @Bean
    public BambooServer bambooServer() {
        UserPasswordCredentials userPasswordCredentials = new SimpleUserPasswordCredentials(BAMBOO_USER, BAMBOO_PASSWORD);
        return new BambooServer(BAMBOO_SERVER_URL.toString(), userPasswordCredentials);
    }
}
