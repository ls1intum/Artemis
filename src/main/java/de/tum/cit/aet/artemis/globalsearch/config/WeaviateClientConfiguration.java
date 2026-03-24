package de.tum.cit.aet.artemis.globalsearch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.core.exception.WeaviateAuthenticationException;
import de.tum.cit.aet.artemis.core.exception.WeaviateConnectionException;
import io.weaviate.client6.v1.api.Authentication;
import io.weaviate.client6.v1.api.WeaviateClient;

/**
 * Configuration for Weaviate client
 */
@Lazy
@Configuration
@Conditional(WeaviateEnabled.class)
@EnableConfigurationProperties(WeaviateConfigurationProperties.class)
public class WeaviateClientConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WeaviateClientConfiguration.class);

    private final WeaviateConfigurationProperties weaviateProperties;

    public WeaviateClientConfiguration(WeaviateConfigurationProperties weaviateProperties) {
        this.weaviateProperties = weaviateProperties;
    }

    /**
     * Creates and configures a Weaviate client bean.
     * Configuration validation is handled by {@link de.tum.cit.aet.artemis.core.config.ConfigurationValidator#validateWeaviateConfiguration()}.
     * <p>
     * When using the text2vec-openai vectorizer, the GPU API key is passed as a request header
     * so that Weaviate can forward it to the OpenAI-compatible API (e.g. Ollama).
     * <p>
     * When {@code api-key} is configured, the client authenticates against the Weaviate
     * server using API key authentication (see <a href="https://docs.weaviate.io/deploy/configuration/authentication#api-key-authentication">Weaviate docs</a>).
     *
     * @return the configured WeaviateClient instance
     * @throws WeaviateConnectionException if the connection to Weaviate fails
     */
    @Bean(destroyMethod = "close")
    public WeaviateClient weaviateClient() {
        try {
            boolean usesOpenAiVectorizer = SupportedVectorizer.TEXT2VEC_OPENAI.configValue().equals(weaviateProperties.vectorizerModule());
            boolean hasGpuApiKey = StringUtils.hasText(weaviateProperties.gpuApiKey());
            boolean hasApiKey = StringUtils.hasText(weaviateProperties.apiKey());

            WeaviateClient client = weaviateProperties.secure() ? createSecureClient(hasApiKey, hasGpuApiKey, usesOpenAiVectorizer)
                    : createLocalClient(hasApiKey, hasGpuApiKey, usesOpenAiVectorizer);

            logClientConfiguration(hasApiKey, hasGpuApiKey);
            verifyReadiness(client);
            return client;
        }
        catch (Exception exception) {
            log.error("Failed to configure Weaviate client for {}://{}:{} (gRPC port: {})", weaviateProperties.scheme(), weaviateProperties.httpHost(),
                    weaviateProperties.httpPort(), weaviateProperties.grpcPort(), exception);
            if (isAuthenticationError(exception)) {
                throw new WeaviateAuthenticationException("Weaviate authentication failed", exception, weaviateProperties.httpHost(), weaviateProperties.httpPort(),
                        weaviateProperties.secure());
            }
            throw new WeaviateConnectionException("Failed to configure Weaviate client", exception, weaviateProperties.httpHost(), weaviateProperties.httpPort(),
                    weaviateProperties.grpcPort(), weaviateProperties.secure());
        }
    }

    /**
     * Creates a Weaviate client using a custom (HTTPS/secure) connection.
     * <p>
     * IMPORTANT: {@code scheme()} must be called first because it auto-sets both ports
     * (443 for https, 80 for http). {@code httpPort()} and {@code grpcPort()} are called
     * after to override with the configured values.
     */
    private WeaviateClient createSecureClient(boolean hasApiKey, boolean hasGpuApiKey, boolean usesOpenAiVectorizer) {
        return WeaviateClient.connectToCustom(config -> {
            config.scheme(weaviateProperties.scheme()).httpHost(weaviateProperties.httpHost()).httpPort(weaviateProperties.httpPort()).grpcHost(weaviateProperties.httpHost())
                    .grpcPort(weaviateProperties.grpcPort());
            if (hasApiKey) {
                config.authentication(Authentication.apiKey(weaviateProperties.apiKey()));
            }
            if (hasGpuApiKey && usesOpenAiVectorizer) {
                config.setHeader("X-OpenAI-Api-Key", weaviateProperties.gpuApiKey());
            }
            return config;
        });
    }

    /**
     * Creates a Weaviate client using a local (non-secure) connection.
     */
    private WeaviateClient createLocalClient(boolean hasApiKey, boolean hasGpuApiKey, boolean usesOpenAiVectorizer) {
        return WeaviateClient.connectToLocal(config -> {
            config.host(weaviateProperties.httpHost()).port(weaviateProperties.httpPort()).grpcPort(weaviateProperties.grpcPort());
            if (hasApiKey) {
                config.authentication(Authentication.apiKey(weaviateProperties.apiKey()));
            }
            if (hasGpuApiKey && usesOpenAiVectorizer) {
                config.setHeader("X-OpenAI-Api-Key", weaviateProperties.gpuApiKey());
            }
            return config;
        });
    }

    private void logClientConfiguration(boolean hasApiKey, boolean hasGpuApiKey) {
        if (hasApiKey) {
            log.debug("Configured Weaviate client with API key authentication");
        }
        if (hasGpuApiKey) {
            log.debug("Configured Weaviate client with X-OpenAI-Api-Key header for OpenAI-compatible vectorizer");
        }
    }

    /**
     * Checks whether the exception (or any exception in its cause chain) indicates
     * an HTTP 401 authentication error from Weaviate.
     */
    private boolean isAuthenticationError(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains("HTTP 401")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Verifies that the Weaviate server is ready to accept requests.
     * The client constructor already verified liveness; this is an additional readiness check.
     * A transient readiness failure is logged but does not destroy the client.
     */
    private void verifyReadiness(WeaviateClient client) {
        try {
            if (client.isReady()) {
                log.info("Connected to Weaviate at {}://{}:{}", weaviateProperties.scheme(), weaviateProperties.httpHost(), weaviateProperties.httpPort());
            }
            else {
                log.warn("Weaviate client created but server is not ready at {}://{}:{}", weaviateProperties.scheme(), weaviateProperties.httpHost(),
                        weaviateProperties.httpPort());
            }
        }
        catch (Exception readinessException) {
            log.warn("Could not verify Weaviate readiness at {}://{}:{}: {}", weaviateProperties.scheme(), weaviateProperties.httpHost(), weaviateProperties.httpPort(),
                    readinessException.getMessage());
        }
    }

}
