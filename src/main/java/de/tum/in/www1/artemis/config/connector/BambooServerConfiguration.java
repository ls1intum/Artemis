package de.tum.in.www1.artemis.config.connector;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.appfire.bamboo.cli.BambooClient;
import com.appfire.common.cli.Base;
import com.appfire.common.cli.Settings;
import com.atlassian.bamboo.specs.util.BambooServer;
import com.atlassian.bamboo.specs.util.SimpleUserPasswordCredentials;
import com.atlassian.bamboo.specs.util.UserPasswordCredentials;

@Configuration
@Profile("bamboo")
public class BambooServerConfiguration {

    @Value("${artemis.continuous-integration.user}")
    private String BAMBOO_USER;

    @Value("${artemis.continuous-integration.password}")
    private String BAMBOO_PASSWORD;

    @Value("${artemis.continuous-integration.url}")
    private URL BAMBOO_SERVER_URL;

    @Value("${artemis.version-control.user}")
    private String BITBUCKET_USER;

    @Value("${artemis.version-control.password}")
    private String BITBUCKET_PASSWORD;

    @Value("${artemis.version-control.url}")
    private URL BITBUCKET_SERVER;

    @Bean
    public BambooServer bambooServer() {
        UserPasswordCredentials userPasswordCredentials = new SimpleUserPasswordCredentials(BAMBOO_USER, BAMBOO_PASSWORD);
        return new BambooServer(BAMBOO_SERVER_URL.toString(), userPasswordCredentials);
    }

    /**
     * Creates a Bamboo client for communication with the Bamboo instance over the non-REST API. This beans is also connected to the Bitbucket server
     * if the bitbucket profile is activated (incl. authentication).
     *
     * @return BambooClient instance for the Bamboo server that is defined in the environment yml files.
     */
    @Bean
    public BambooClient bambooClient() {
        final var bambooClient = new BambooClient(createBase());
        // setup the Bamboo Client to use the correct username and password
        final var args = new String[] { "-s", BAMBOO_SERVER_URL.toString(), "--user", BAMBOO_USER, "--password", BAMBOO_PASSWORD, "--targetServer", BITBUCKET_SERVER.toString(),
                "--targetUser", BITBUCKET_USER, "--targetPassword", BITBUCKET_PASSWORD };

        bambooClient.doWork(args); // only invoke this to set server address, username and password so that the following action will work
        return bambooClient;
    }

    private Base createBase() {
        // we override the out stream to prevent unnecessary log statements in our log files
        final var outputStream = new ByteArrayOutputStream();
        final var printStream = new PrintStream(outputStream);
        final var settings = new Settings();
        settings.setOut(printStream);
        settings.setOverrideOut(printStream);
        settings.setDebugOut(printStream);
        settings.setErr(printStream);
        return new Base(settings);
    }
}
