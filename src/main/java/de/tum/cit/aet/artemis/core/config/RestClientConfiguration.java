package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_JENKINS;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.xml.JacksonXmlHttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;

import de.tum.cit.aet.artemis.athena.config.AthenaAuthorizationInterceptor;
import de.tum.cit.aet.artemis.athena.config.AthenaEnabled;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.config.PyrisAuthorizationInterceptor;
import de.tum.cit.aet.artemis.jenkins.service.JenkinsAuthorizationInterceptor;
import de.tum.cit.aet.artemis.modeling.config.ApollonEnabled;
import de.tum.cit.aet.artemis.programming.service.sharing.SharingEnabled;

/**
 * The {@link RestClient} beans for the remote services that Artemis calls, one per service and timeout. They replace the deprecated
 * {@link org.springframework.web.client.RestTemplate} beans that this configuration used to define.
 * <p>
 * Every client is built from the {@link RestClient.Builder} of Spring Boot, so that the message converters and the customizers of the
 * application apply, but keeps {@link SimpleClientHttpRequestFactory}, the HTTP stack of the former templates. Without it, Spring Boot
 * would pick Apache HttpClient 5, which has other connection pooling and timeout defaults. The URI templates are encoded like the
 * template did, because the call sites pass strings that already contain encoded parts.
 * <p>
 * As with the templates, the timeout belongs to the client: a call that has to fail faster gets its own bean. The clients of the services
 * with a shared secret carry an interceptor, and those clients do not read XML, as before.
 */
@Profile(PROFILE_CORE)
@Configuration
@Lazy
public class RestClientConfiguration {

    private static final int SHORT_CONNECTION_TIMEOUT = 10 * 1000;

    private static final int SHORT_READ_TIMEOUT = 10 * 1000;

    private static final int VERY_SHORT_CONNECTION_TIMEOUT = 1000;

    private static final int VERY_SHORT_READ_TIMEOUT = 1000;

    // The Hermes health check pings /api/health, which must fail fast: it blocks the Actuator /health endpoint
    // and every metrics scrape, so a hung relay must not tie up those threads for the full short timeout.
    private static final int HERMES_HEALTH_CONNECTION_TIMEOUT = 3 * 1000;

    private static final int HERMES_HEALTH_READ_TIMEOUT = 3 * 1000;

    /**
     * Applies the HTTP stack and the URI encoding that all clients share.
     *
     * @param builder        the builder of Spring Boot, which is a new instance for every injection
     * @param connectTimeout the connection timeout in milliseconds, 0 or less for none
     * @param readTimeout    the read timeout in milliseconds, 0 for none
     * @return the same builder
     */
    private static RestClient.Builder configured(RestClient.Builder builder, int connectTimeout, int readTimeout) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        if (connectTimeout > 0) {
            requestFactory.setConnectTimeout(connectTimeout);
        }
        if (readTimeout > 0) {
            requestFactory.setReadTimeout(readTimeout);
        }
        // The template of the same name encoded a template after the expansion, and the strings that the services pass depend on that.
        var uriBuilderFactory = new DefaultUriBuilderFactory();
        uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.URI_COMPONENT);
        return builder.requestFactory(requestFactory).uriBuilderFactory(uriBuilderFactory);
    }

    /**
     * A client with the interceptor of its service. The XML converter is removed, as it was for the templates, because it would be picked
     * for requests and responses of the tests.
     */
    private static RestClient jsonClient(RestClient.Builder builder, ClientHttpRequestInterceptor interceptor, int connectTimeout, int readTimeout) {
        return withoutXmlConverter(configured(builder, connectTimeout, readTimeout)).requestInterceptor(interceptor).build();
    }

    private static RestClient.Builder withoutXmlConverter(RestClient.Builder builder) {
        return builder.configureMessageConverters(converters -> converters.configureMessageConvertersList(list -> list.removeIf(JacksonXmlHttpMessageConverter.class::isInstance)));
    }

    // ---- Apollon

    /**
     * The client for the conversion calls to Apollon, without a timeout.
     *
     * @param restClientBuilder the builder of Spring Boot, which is a new instance for every injection
     * @return the client for the Apollon conversion service
     */
    @Bean
    @Conditional(ApollonEnabled.class)
    public RestClient apollonRestClient(RestClient.Builder restClientBuilder) {
        return configured(restClientBuilder, 0, 0).build();
    }

    /**
     * The client for the health check of Apollon, which must fail fast because it blocks the actuator health endpoint.
     *
     * @param restClientBuilder the builder of Spring Boot, which is a new instance for every injection
     * @return the client with short timeouts for the Apollon health check
     */
    @Bean
    @Conditional(ApollonEnabled.class)
    public RestClient shortTimeoutApollonRestClient(RestClient.Builder restClientBuilder) {
        return configured(restClientBuilder, SHORT_CONNECTION_TIMEOUT, SHORT_READ_TIMEOUT).build();
    }

    // ---- Hermes

    /**
     * The client for the push notification requests to the Hermes relay, without a timeout. The relay request retries with a backoff, see
     * {@code PushNotificationService}.
     *
     * @param restClientBuilder the builder of Spring Boot, which is a new instance for every injection
     * @return the client for the push notification relay
     */
    @Bean
    public RestClient hermesRestClient(RestClient.Builder restClientBuilder) {
        return configured(restClientBuilder, 0, 0).build();
    }

    /**
     * The client for the health check of Hermes, which must fail fast because it blocks the actuator health endpoint.
     *
     * @param restClientBuilder the builder of Spring Boot, which is a new instance for every injection
     * @return the client with short timeouts for the Hermes health check
     */
    @Bean
    public RestClient shortTimeoutHermesRestClient(RestClient.Builder restClientBuilder) {
        return configured(restClientBuilder, HERMES_HEALTH_CONNECTION_TIMEOUT, HERMES_HEALTH_READ_TIMEOUT).build();
    }

    // ---- GitHub, OSV and telemetry

    /**
     * The client for the version check against the GitHub releases API, without a timeout.
     *
     * @param restClientBuilder the builder of Spring Boot, which is a new instance for every injection
     * @return the client for the GitHub releases API
     */
    @Bean
    public RestClient githubRestClient(RestClient.Builder restClientBuilder) {
        return configured(restClientBuilder, 0, 0).build();
    }

    /**
     * The client for the vulnerability lookups at the OSV API, without a timeout.
     *
     * @param restClientBuilder the builder of Spring Boot, which is a new instance for every injection
     * @return the client for the OSV API
     */
    @Bean
    public RestClient osvRestClient(RestClient.Builder restClientBuilder) {
        return configured(restClientBuilder, 0, 0).build();
    }

    /**
     * The client for the startup telemetry report, without a timeout.
     *
     * @param restClientBuilder the builder of Spring Boot, which is a new instance for every injection
     * @return the client for the telemetry collector
     */
    @Bean
    public RestClient telemetryRestClient(RestClient.Builder restClientBuilder) {
        return configured(restClientBuilder, 0, 0).build();
    }

    // ---- LTI and Sharing

    /**
     * The client for the LTI platforms: the token request, the score submission and the dynamic registration. There is no timeout, as
     * before.
     *
     * @param restClientBuilder the builder of Spring Boot, which is a new instance for every injection
     * @return the client for the LTI platforms
     */
    @Bean
    public RestClient ltiRestClient(RestClient.Builder restClientBuilder) {
        return configured(restClientBuilder, 0, 0).build();
    }

    /**
     * The client for the Sharing Platform, with short timeouts. It is a client of its own so that a test can mock the platform without
     * affecting the other services.
     *
     * @param restClientBuilder the builder of Spring Boot, which is a new instance for every injection
     * @return the client with short timeouts for the Sharing Platform
     */
    @Bean
    @Conditional(SharingEnabled.class)
    public RestClient sharingRestClient(RestClient.Builder restClientBuilder) {
        return configured(restClientBuilder, SHORT_CONNECTION_TIMEOUT, SHORT_READ_TIMEOUT).build();
    }

    // ---- Athena

    /**
     * The client for the requests to Athena, without a timeout.
     *
     * @param restClientBuilder              the builder of Spring Boot, which is a new instance for every injection
     * @param athenaAuthorizationInterceptor adds the Athena secret
     * @return the client for Athena
     */
    @Bean
    @Conditional(AthenaEnabled.class)
    public RestClient athenaRestClient(RestClient.Builder restClientBuilder, AthenaAuthorizationInterceptor athenaAuthorizationInterceptor) {
        return jsonClient(restClientBuilder, athenaAuthorizationInterceptor, 0, 0);
    }

    /**
     * The client for the health check of Athena, with short timeouts.
     *
     * @param restClientBuilder              the builder of Spring Boot, which is a new instance for every injection
     * @param athenaAuthorizationInterceptor adds the Athena secret
     * @return the client with short timeouts for Athena
     */
    @Bean
    @Conditional(AthenaEnabled.class)
    public RestClient shortTimeoutAthenaRestClient(RestClient.Builder restClientBuilder, AthenaAuthorizationInterceptor athenaAuthorizationInterceptor) {
        return jsonClient(restClientBuilder, athenaAuthorizationInterceptor, SHORT_CONNECTION_TIMEOUT, SHORT_READ_TIMEOUT);
    }

    /**
     * The client for requests to Athena that must fail even faster, such as the selection of the next submission.
     *
     * @param restClientBuilder              the builder of Spring Boot, which is a new instance for every injection
     * @param athenaAuthorizationInterceptor adds the Athena secret
     * @return the client with very short timeouts for Athena
     */
    @Bean
    @Conditional(AthenaEnabled.class)
    public RestClient veryShortTimeoutAthenaRestClient(RestClient.Builder restClientBuilder, AthenaAuthorizationInterceptor athenaAuthorizationInterceptor) {
        return jsonClient(restClientBuilder, athenaAuthorizationInterceptor, VERY_SHORT_CONNECTION_TIMEOUT, VERY_SHORT_READ_TIMEOUT);
    }

    // ---- Pyris

    /**
     * The client for the requests to Pyris, with short timeouts.
     *
     * @param restClientBuilder             the builder of Spring Boot, which is a new instance for every injection
     * @param pyrisAuthorizationInterceptor adds the Pyris secret
     * @return the client for Pyris
     */
    @Bean
    @Conditional(IrisEnabled.class)
    public RestClient pyrisRestClient(RestClient.Builder restClientBuilder, PyrisAuthorizationInterceptor pyrisAuthorizationInterceptor) {
        return jsonClient(restClientBuilder, pyrisAuthorizationInterceptor, SHORT_CONNECTION_TIMEOUT, SHORT_READ_TIMEOUT);
    }

    /**
     * The Pyris ingestion census aggregates the index state of a whole course, which takes longer than the short timeout allows for large
     * courses, so it gets its own read timeout.
     *
     * @param restClientBuilder             the builder of Spring Boot, which is a new instance for every injection
     * @param pyrisAuthorizationInterceptor adds the Pyris secret
     * @param censusTimeout                 the read timeout for one census request
     * @return the client for census requests
     */
    @Bean
    @Conditional(IrisEnabled.class)
    public RestClient censusPyrisRestClient(RestClient.Builder restClientBuilder, PyrisAuthorizationInterceptor pyrisAuthorizationInterceptor,
            @Value("${artemis.iris.ingestion.census-timeout:PT60S}") Duration censusTimeout) {
        return jsonClient(restClientBuilder, pyrisAuthorizationInterceptor, SHORT_CONNECTION_TIMEOUT, (int) censusTimeout.toMillis());
    }

    /**
     * The client for the health check of Pyris, with short timeouts.
     *
     * @param restClientBuilder             the builder of Spring Boot, which is a new instance for every injection
     * @param pyrisAuthorizationInterceptor adds the Pyris secret
     * @return the client with short timeouts for the Pyris health check
     */
    @Bean
    @Conditional(IrisEnabled.class)
    public RestClient shortTimeoutPyrisRestClient(RestClient.Builder restClientBuilder, PyrisAuthorizationInterceptor pyrisAuthorizationInterceptor) {
        return jsonClient(restClientBuilder, pyrisAuthorizationInterceptor, SHORT_CONNECTION_TIMEOUT, SHORT_READ_TIMEOUT);
    }

    // ---- Jenkins

    /**
     * The client that the Jenkins interceptor uses to fetch a crumb. It has no interceptor itself, because the interceptor would otherwise
     * call itself for the request that it needs to authenticate.
     *
     * @param restClientBuilder the builder of Spring Boot, which is a new instance for every injection
     * @return the client for the crumb issuer of Jenkins
     */
    // REMOVE_IN_ARTEMIS_11 (Jenkins): remove this bean and its JenkinsAuthorizationInterceptor with Jenkins support
    @Bean
    @Profile(PROFILE_JENKINS)
    public RestClient jenkinsCrumbRestClient(RestClient.Builder restClientBuilder) {
        return configured(restClientBuilder, 0, 0).build();
    }

    /**
     * The client for the requests to Jenkins, without a timeout.
     *
     * @param restClientBuilder  the builder of Spring Boot, which is a new instance for every injection
     * @param jenkinsInterceptor adds the credentials and the crumb
     * @return the client for Jenkins
     */
    // REMOVE_IN_ARTEMIS_11 (Jenkins): remove this bean and its JenkinsAuthorizationInterceptor with Jenkins support
    @Bean
    @Profile(PROFILE_JENKINS)
    public RestClient jenkinsRestClient(RestClient.Builder restClientBuilder, JenkinsAuthorizationInterceptor jenkinsInterceptor) {
        return jsonClient(restClientBuilder, jenkinsInterceptor, 0, 0);
    }

    /**
     * The client for the health check of Jenkins, with short timeouts.
     *
     * @param restClientBuilder  the builder of Spring Boot, which is a new instance for every injection
     * @param jenkinsInterceptor adds the credentials and the crumb
     * @return the client with short timeouts for Jenkins
     */
    // REMOVE_IN_ARTEMIS_11 (Jenkins): remove this bean and its JenkinsAuthorizationInterceptor with Jenkins support
    @Bean
    @Profile(PROFILE_JENKINS)
    public RestClient shortTimeoutJenkinsRestClient(RestClient.Builder restClientBuilder, JenkinsAuthorizationInterceptor jenkinsInterceptor) {
        return jsonClient(restClientBuilder, jenkinsInterceptor, SHORT_CONNECTION_TIMEOUT, SHORT_READ_TIMEOUT);
    }
}
