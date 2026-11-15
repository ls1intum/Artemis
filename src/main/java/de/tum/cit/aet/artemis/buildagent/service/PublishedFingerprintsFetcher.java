package de.tum.cit.aet.artemis.buildagent.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.core.type.TypeReference;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

/**
 * Reads the fingerprints of the SSH host keys that a core node publishes at {@code api/localvc/public/ssh-fingerprints}, below the path of the configured URL.
 * Answers an empty set when the core node cannot be reached, does not publish any, or the configured URL does not use https.
 */
class PublishedFingerprintsFetcher implements Supplier<Set<String>> {

    private static final Logger log = LoggerFactory.getLogger(PublishedFingerprintsFetcher.class);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private static final String FINGERPRINTS_PATH = "api/localvc/public/ssh-fingerprints";

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();

    private final URI uri;

    PublishedFingerprintsFetcher(URI localVCBaseUri) {
        // The path of the base URL stays, as an installation can serve Artemis below a path
        this.uri = UriComponentsBuilder.fromUri(localVCBaseUri).pathSegment(FINGERPRINTS_PATH.split("/")).replaceQuery(null).fragment(null).build(true).toUri();
    }

    /**
     * The address that the fingerprints are read from.
     *
     * @return the address, below the path of the configured URL
     */
    URI uri() {
        return uri;
    }

    @Override
    public Set<String> get() {
        // The fingerprints are what the host key of the core node is compared with, so they must come over a channel that is protected itself
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            log.warn("Not reading the SSH fingerprints of the core node from {}: they are only read over https", uri);
            return Set.of();
        }
        return fetch(REQUEST_TIMEOUT);
    }

    /**
     * Requests the fingerprints, the whole exchange including the body must end within the deadline.
     *
     * @param deadline the time that the exchange may take
     * @return the fingerprints, empty when they could not be read in time
     */
    Set<String> fetch(Duration deadline) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(deadline).GET().build();
            // The timeout of the request ends with the response headers, so the whole exchange gets its own deadline that also cancels the transfer of the body
            CompletableFuture<HttpResponse<String>> exchange = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> response;
            try {
                response = exchange.get(deadline.toMillis(), TimeUnit.MILLISECONDS);
            }
            catch (TimeoutException | InterruptedException e) {
                // an interrupted wait must not leave the exchange reading a body that does not end
                exchange.cancel(true);
                throw e;
            }
            if (response.statusCode() != 200) {
                log.warn("Asking {} for the SSH fingerprints of the core node returned {}", uri, response.statusCode());
                return Set.of();
            }
            Map<String, List<String>> fingerprints = JsonObjectMapper.get().readValue(response.body(), new TypeReference<Map<String, List<String>>>() {
            });
            if (fingerprints == null) {
                return Set.of();
            }
            return fingerprints.values().stream().flatMap(List::stream).collect(Collectors.toUnmodifiableSet());
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Set.of();
        }
        catch (Exception e) {
            log.warn("Could not read the SSH fingerprints of the core node from {}: {}", uri, e.getMessage());
            return Set.of();
        }
    }
}
