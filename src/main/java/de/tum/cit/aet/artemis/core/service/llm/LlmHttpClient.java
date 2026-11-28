package de.tum.cit.aet.artemis.core.service.llm;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadLocalRandom;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Endpoint;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.EndpointType;
import de.tum.cit.aet.artemis.core.exception.LlmResponseException;
import io.micrometer.observation.ObservationRegistry;

/** Synchronous JSON transport shared by the Responses and embeddings APIs. */
final class LlmHttpClient {

    // Legacy resource endpoints require an API version even if the operator did not configure one.
    private static final String LEGACY_FOUNDRY_RESPONSES_VERSION = "2025-04-01-preview";

    private static final long MAX_BACKOFF_MILLIS = 8000;

    private final Endpoint endpoint;

    private final RestClient client;

    private final JsonMapper mapper;

    private final int maxRetries;

    LlmHttpClient(Endpoint endpoint, ClientHttpRequestFactory factory, JsonMapper mapper, ObservationRegistry observations, int maxRetries) {
        this.endpoint = endpoint;
        this.client = RestClient.builder().requestFactory(factory).observationRegistry(observations).defaultHeaders(headers -> {
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (endpoint.hasApiKey()) {
                if (endpoint.type() == EndpointType.MICROSOFT_FOUNDRY) {
                    headers.set("api-key", endpoint.apiKey());
                }
                else {
                    headers.setBearerAuth(endpoint.apiKey());
                }
            }
            if (StringUtils.hasText(endpoint.organizationId())) {
                headers.set("OpenAI-Organization", endpoint.organizationId());
            }
            endpoint.customHeaders().forEach(headers::set);
        }).configureMessageConverters(converters -> converters.disableDefaults().addCustomConverter(new ByteArrayHttpMessageConverter())).build();
        this.mapper = mapper;
        this.maxRetries = maxRetries;
    }

    // Provider requests omit unset options, but an empty message or tool result must still have its content field.
    static JsonMapper wireMapper(JsonMapper configured) {
        return configured.rebuild().changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL)).build();
    }

    <T> T post(String path, Record request, Class<T> responseType) {
        URI uri = url(path);
        byte[] body = mapper.writeValueAsBytes(request);
        for (int attempt = 0;; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("LLM request interrupted");
            }
            Duration delay;
            try {
                return exchange(uri, path, body, responseType);
            }
            catch (RestClientResponseException ex) {
                delay = httpRetryDelay(attempt, ex);
            }
            catch (ResourceAccessException ex) {
                if (attempt >= maxRetries || Thread.currentThread().isInterrupted()) {
                    throw ex;
                }
                delay = retryDelay(attempt, null);
            }
            sleepBeforeRetry(delay);
        }
    }

    private Duration httpRetryDelay(int attempt, RestClientResponseException failure) {
        if (attempt >= maxRetries || !isRetryable(failure)) {
            throw failure;
        }
        var delay = retryDelay(attempt, failure.getResponseHeaders());
        if (delay.toMillis() > MAX_BACKOFF_MILLIS) {
            // Leave a longer cooldown to the caller, with the original status and Retry-After header.
            throw failure;
        }
        return delay;
    }

    private <T> T exchange(URI uri, String path, byte[] body, Class<T> responseType) {
        byte[] bytes = client.post().uri(uri).body(body).exchange((sent, response) -> {
            var status = response.getStatusCode();
            if (!status.is2xxSuccessful()) {
                throw new RestClientResponseException("LLM provider returned HTTP " + status.value(), status, response.getStatusText(), response.getHeaders(), errorBody(response),
                        StandardCharsets.UTF_8);
            }
            return response.getBody().readAllBytes();
        });
        try {
            T response = bytes.length == 0 ? null : mapper.readValue(bytes, responseType);
            if (response == null) {
                throw new LlmResponseException("Empty " + path + " response", null);
            }
            return response;
        }
        catch (JacksonException ex) {
            throw new LlmResponseException("Malformed " + path + " response: " + ex.getOriginalMessage(), ex);
        }
    }

    private static byte[] errorBody(ClientHttpResponse response) {
        try {
            return response.getBody().readAllBytes();
        }
        catch (IOException _) {
            // A truncated error body must still propagate the provider status and retry headers.
            return new byte[0];
        }
    }

    private static void sleepBeforeRetry(Duration delay) {
        try {
            Thread.sleep(delay);
        }
        catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new CancellationException("LLM retry interrupted");
        }
    }

    private URI url(String path) {
        URI base = URI.create(endpoint.baseUrl());
        var url = UriComponentsBuilder.fromUri(base);
        if (endpoint.type() == EndpointType.MICROSOFT_FOUNDRY) {
            String version = endpoint.microsoftFoundryServiceVersion();
            String basePath = StringUtils.trimTrailingCharacter(base.getPath(), '/');
            if (basePath == null || !basePath.endsWith("/openai/v1")) {
                url.pathSegment("openai");
                if (!StringUtils.hasText(version)) {
                    version = LEGACY_FOUNDRY_RESPONSES_VERSION;
                }
            }
            if (StringUtils.hasText(version)) {
                url.queryParam("api-version", UriUtils.encode(version, StandardCharsets.UTF_8));
            }
        }
        return url.pathSegment(path).build(true).toUri();
    }

    private static boolean isRetryable(RestClientResponseException ex) {
        var headers = ex.getResponseHeaders();
        String directive = headers == null ? null : headers.getFirst("X-Should-Retry");
        if ("true".equals(directive) || "false".equals(directive)) {
            return Boolean.parseBoolean(directive);
        }
        int status = ex.getStatusCode().value();
        return status == 408 || status == 409 || status == 429 || status >= 500;
    }

    private static Duration retryDelay(int attempt, @Nullable HttpHeaders headers) {
        if (headers != null) {
            Long millis = numericDelay(headers.getFirst("Retry-After-Ms"), 1);
            if (millis == null) {
                millis = numericDelay(headers.getFirst(HttpHeaders.RETRY_AFTER), 1000);
            }
            if (millis == null && headers.containsHeader(HttpHeaders.RETRY_AFTER)) {
                try {
                    millis = headers.getFirstDate(HttpHeaders.RETRY_AFTER) - System.currentTimeMillis();
                }
                catch (IllegalArgumentException _) {
                    // Fall back to exponential backoff for a malformed HTTP date.
                }
            }
            if (millis != null) {
                return Duration.ofMillis(Math.max(0, millis));
            }
        }
        long backoff = 500L << Math.min(attempt, 4);
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(backoff * 3 / 4, backoff));
    }

    private static @Nullable Long numericDelay(@Nullable String value, int millisPerUnit) {
        if (value == null) {
            return null;
        }
        try {
            double amount = Double.parseDouble(value);
            return Double.isFinite(amount) ? (long) (amount * millisPerUnit) : null;
        }
        catch (NumberFormatException _) {
            return null;
        }
    }
}
