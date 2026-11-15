package de.tum.cit.aet.artemis.buildagent.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.type.TypeReference;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

/**
 * Reads the fingerprints of the SSH host keys that a core node publishes at {@code api/localvc/public/ssh-fingerprints}.
 * Answers an empty set when the core node cannot be reached or does not publish any.
 */
class PublishedFingerprintsFetcher implements Supplier<Set<String>> {

    private static final Logger log = LoggerFactory.getLogger(PublishedFingerprintsFetcher.class);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private static final String FINGERPRINTS_PATH = "api/localvc/public/ssh-fingerprints";

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();

    private final URI uri;

    PublishedFingerprintsFetcher(URI localVCBaseUri) {
        this.uri = localVCBaseUri.resolve("/" + FINGERPRINTS_PATH);
    }

    @Override
    public Set<String> get() {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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
