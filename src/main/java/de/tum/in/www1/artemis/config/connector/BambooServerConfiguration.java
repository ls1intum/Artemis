package de.tum.in.www1.artemis.config.connector;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.appfire.bamboo.cli.BambooClient;
import com.appfire.bitbucket.cli.BitbucketClient;
import com.appfire.common.cli.Base;
import com.appfire.common.cli.Settings;
import com.atlassian.bamboo.specs.util.*;

@Configuration
@Profile("bamboo")
public class BambooServerConfiguration {

    @Value("${artemis.continuous-integration.user}")
    private String bambooUser;

    @Value("${artemis.continuous-integration.password}")
    private String bambooPassword;

    @Value("${artemis.continuous-integration.token:#{null}}")
    private Optional<String> bambooToken;

    @Value("${artemis.continuous-integration.url}")
    private URL bambooServerUrl;

    @Value("${artemis.version-control.user}")
    private String bitbucketUser;

    @Value("${artemis.version-control.password}")
    private String bitbucketPassword;

    @Value("${artemis.version-control.url}")
    private URL bitbucketServerUrl;

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

    /**
     * Creates a Bamboo client for communication with the Bamboo instance over the non-REST API. This beans is also connected to the Bitbucket server
     * if the bitbucket profile is activated (incl. authentication).
     *
     * @return BambooClient instance for the Bamboo server that is defined in the environment yml files.
     */
    @Bean
    // TODO: as soon as available, we should also use a token here
    public BambooClient bambooClient() {
        final var bambooClient = new BambooClient(createBase());
        // setup the Bamboo Client to use the correct username and password
        final var args = new String[] { "-s", bambooServerUrl.toString(), "--user", bambooUser, "--password", bambooPassword, "--targetServer", bitbucketServerUrl.toString(),
                "--targetUser", bitbucketUser, "--targetPassword", bitbucketPassword };

        bambooClient.doWork(args); // only invoke this to set server address, username and password so that the following action will work
        return bambooClient;
    }

    @Bean
    public BitbucketClient bitbucketClient() {
        final var bitbucketClient = new BitbucketClient(createBase());
        final var args = new String[] { "-s", bitbucketServerUrl.toString(), "--user", bitbucketUser, "--password", bitbucketPassword };
        bitbucketClient.doWork(args);
        return bitbucketClient;
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
