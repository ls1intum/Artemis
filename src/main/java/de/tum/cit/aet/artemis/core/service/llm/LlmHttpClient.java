package de.tum.cit.aet.artemis.core.service.llm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Endpoint;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.EndpointType;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okio.BufferedSink;

/** Synchronous JSON transport shared by the Responses and embeddings APIs. */
final class LlmHttpClient {

    interface Request {

        String model();
    }

    // Legacy resource endpoints require an API version even if the operator did not configure one.
    private static final String LEGACY_FOUNDRY_VERSION = "2024-10-21";

    private static final long MAX_BACKOFF_MILLIS = 8000;

    private static final Pattern TRAILING_SLASHES = Pattern.compile("/+$");

    private final Endpoint endpoint;

    private final OkHttpClient client;

    private final JsonMapper mapper;

    private final int maxRetries;

    LlmHttpClient(Endpoint endpoint, OkHttpClient client, JsonMapper mapper, int maxRetries) {
        this.endpoint = endpoint;
        this.client = client;
        this.mapper = mapper;
        this.maxRetries = maxRetries;
    }

    // Provider requests omit unset options, but an empty message or tool result must still have its content field.
    static JsonMapper wireMapper(JsonMapper configured) {
        return configured.rebuild().changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL)).build();
    }

    <T> T post(String path, Request request, Class<T> responseType) {
        var builder = new okhttp3.Request.Builder().url(url(path, request.model())).post(new JsonBody(mapper.writeValueAsBytes(request))).header("Accept", "application/json");
        if (endpoint.hasApiKey()) {
            if (endpoint.type() == EndpointType.MICROSOFT_FOUNDRY) {
                builder.header("api-key", endpoint.apiKey());
            }
            else {
                builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + endpoint.apiKey());
            }
        }
        if (StringUtils.hasText(endpoint.organizationId())) {
            builder.header("OpenAI-Organization", endpoint.organizationId());
        }
        endpoint.customHeaders().forEach(builder::header);
        var httpRequest = builder.build();
        for (int attempt = 0;; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("LLM request interrupted");
            }
            Duration delay;
            try (var httpResponse = client.newCall(httpRequest).execute()) {
                byte[] bytes = httpResponse.body().bytes();
                if (!httpResponse.isSuccessful()) {
                    var headers = new HttpHeaders();
                    httpResponse.headers().toMultimap().forEach(headers::addAll);
                    throw new RestClientResponseException("LLM provider returned HTTP " + httpResponse.code(), httpResponse.code(), httpResponse.message(), headers, bytes,
                            StandardCharsets.UTF_8);
                }
                T response = bytes.length == 0 ? null : mapper.readValue(bytes, responseType);
                if (response == null) {
                    throw new RestClientException("Empty " + path + " response");
                }
                return response;
            }
            catch (RestClientResponseException ex) {
                if (attempt >= maxRetries || !isRetryable(ex)) {
                    throw ex;
                }
                delay = retryDelay(attempt, ex.getResponseHeaders());
                if (delay.toMillis() > MAX_BACKOFF_MILLIS) {
                    // Leave a longer cooldown to the caller, with the original status and Retry-After header.
                    throw ex;
                }
            }
            catch (IOException ex) {
                if (attempt >= maxRetries || Thread.currentThread().isInterrupted()) {
                    throw new ResourceAccessException("LLM request failed: " + ex.getMessage(), ex);
                }
                delay = retryDelay(attempt, null);
            }
            catch (JacksonException ex) {
                throw new RestClientException("Malformed " + path + " response: " + ex.getOriginalMessage(), ex);
            }
            try {
                Thread.sleep(delay);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new CancellationException("LLM retry interrupted");
            }
        }
    }

    private HttpUrl url(String path, String model) {
        String baseUrl = TRAILING_SLASHES.matcher(endpoint.baseUrl()).replaceFirst("");
        var url = HttpUrl.get(baseUrl).newBuilder();
        if (endpoint.type() == EndpointType.MICROSOFT_FOUNDRY) {
            String version = endpoint.microsoftFoundryServiceVersion();
            boolean legacy = !baseUrl.endsWith("/openai/v1");
            if (legacy) {
                if (".".equals(model) || "..".equals(model)) {
                    throw new IllegalArgumentException("Invalid Microsoft Foundry deployment name");
                }
                url.addPathSegment("openai").addPathSegment("deployments").addPathSegment(model);
                if (!StringUtils.hasText(version)) {
                    version = LEGACY_FOUNDRY_VERSION;
                }
            }
            if (StringUtils.hasText(version)) {
                url.addQueryParameter("api-version", version);
            }
        }
        return url.addPathSegment(path).build();
    }

    // Mark each attempt as single-use so OkHttp cannot resubmit a POST (even on 503 with Retry-After: 0).
    // Only the preset's retry policy below may send it again.
    private static final class JsonBody extends RequestBody {

        private static final MediaType JSON = MediaType.get("application/json");

        private final byte[] bytes;

        private JsonBody(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public MediaType contentType() {
            return JSON;
        }

        @Override
        public long contentLength() {
            return bytes.length;
        }

        @Override
        public boolean isOneShot() {
            return true;
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            sink.write(bytes);
        }
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
                catch (IllegalArgumentException ignored) {
                    // Fall back to exponential backoff for a malformed HTTP date.
                }
            }
            if (millis != null) {
                return Duration.ofMillis(Math.max(0, millis));
            }
        }
        long backoff = 500L << Math.min(attempt, 4);
        return Duration.ofMillis((long) (backoff * ThreadLocalRandom.current().nextDouble(0.75, 1)));
    }

    private static @Nullable Long numericDelay(@Nullable String value, int millisPerUnit) {
        if (value == null) {
            return null;
        }
        try {
            double amount = Double.parseDouble(value);
            return Double.isFinite(amount) ? (long) (amount * millisPerUnit) : null;
        }
        catch (NumberFormatException ignored) {
            return null;
        }
    }
}
