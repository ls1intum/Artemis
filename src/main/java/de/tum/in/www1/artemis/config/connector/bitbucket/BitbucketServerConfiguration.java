package de.tum.in.www1.artemis.config.connector.bitbucket;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.appfire.bitbucket.cli.BitbucketClient;
import com.appfire.common.cli.Base;
import com.appfire.common.cli.Settings;

@Configuration
@Profile("bitbucket")
public class BitbucketServerConfiguration {

    @Value("${artemis.version-control.user}")
    private String BITBUCKET_USER;

    @Value("${artemis.version-control.password}")
    private String BITBUCKET_PASSWORD;

    @Value("${artemis.version-control.url}")
    private URL BITBUCKET_SERVER;

    @Bean
    public BitbucketClient bitbucketClient() {
        final var bitbucketClient = new BitbucketClient(createBase());
        // setup the Bamboo Client to use the correct username and password

        final var args = new String[] { "-s", BITBUCKET_SERVER.toString(), "--user", BITBUCKET_USER, "--password", BITBUCKET_PASSWORD, };
        bitbucketClient.doWork(args);   // only invoke this to set server address, username and password so that the following action will work
        return bitbucketClient;
    }

    private Base createBase() {
        // we override the out stream to prevent unnecessary log statements in our log files
        final var outContent = new ByteArrayOutputStream();
        final var out = new PrintStream(outContent);
        final var settings = new Settings();
        settings.setOut(out);
        settings.setOverrideOut(out);
        settings.setDebugOut(out);
        settings.setErr(out);
        return new Base(settings);
    }
}
