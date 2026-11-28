package de.tum.cit.aet.artemis.buildagent.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.localvc.dto.SshHostKeyFingerprintDTO;
import de.tum.cit.aet.artemis.localvc.dto.SshHostKeyFingerprintsDTO;

/**
 * Reads the fingerprints of the SSH host keys that a core node publishes at {@code api/localvc/public/ssh-fingerprints}, below the path of the configured URL.
 * Answers an empty set when the core node cannot be reached, does not publish any, or the configured URL does not use https.
 */
class PublishedFingerprintsFetcher implements Supplier<Set<String>> {

    private static final Logger log = LoggerFactory.getLogger(PublishedFingerprintsFetcher.class);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private static final String FINGERPRINTS_PATH = "api/localvc/public/ssh-fingerprints";

    private final RestClient restClient = RestClient.builder().requestFactory(requestFactory()).build();

    private final URI uri;

    PublishedFingerprintsFetcher(URI localVCBaseUri) {
        // The path of the base URL stays, as an installation can serve Artemis below a path
        this.uri = UriComponentsBuilder.fromUri(localVCBaseUri).pathSegment(FINGERPRINTS_PATH.split("/")).replaceQuery(null).fragment(null).build(true).toUri();
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build());
        requestFactory.setReadTimeout(REQUEST_TIMEOUT);
        return requestFactory;
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
            return awaitFingerprints(deadline);
        }
        catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return Set.of();
        }
        catch (ExecutionException e) {
            log.warn("Could not read the SSH fingerprints of the core node from {}: {}", uri, e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
            return Set.of();
        }
        catch (Exception e) {
            log.warn("Could not read the SSH fingerprints of the core node from {}: {}", uri, e.getMessage());
            return Set.of();
        }
    }

    /**
     * Asks for the fingerprints and reads the whole answer.
     *
     * @return the fingerprints, empty when the answer is an error or in another shape
     */
    private Set<String> requestFingerprints() {
        return restClient.get().uri(uri).exchange((request, response) -> {
            // The exchange is used, not retrieve(), because every status other than 200 is an empty answer here and not an exception
            if (response.getStatusCode().value() != 200) {
                log.warn("Asking {} for the SSH fingerprints of the core node returned {}", uri, response.getStatusCode().value());
                return Set.<String>of();
            }
            SshHostKeyFingerprintsDTO published = JsonObjectMapper.get().readValue(StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8),
                    SshHostKeyFingerprintsDTO.class);
            if (published == null || published.hostKeys() == null) {
                return Set.<String>of();
            }
            return published.hostKeys().stream().filter(Objects::nonNull).map(SshHostKeyFingerprintDTO::fingerprint).filter(Objects::nonNull)
                    .collect(Collectors.toUnmodifiableSet());
        });
    }

    /**
     * Runs the exchange and waits for the whole answer. The read timeout of the request factory ends with the response headers, so the exchange gets its own
     * deadline that also cancels the transfer of the body: the exchange runs on its own thread, which is interrupted when the deadline passes. That closes the stream
     * of the body, and an interrupted wait must not leave the exchange reading a body that does not end.
     */
    private Set<String> awaitFingerprints(Duration deadline) throws InterruptedException, ExecutionException, TimeoutException {
        FutureTask<Set<String>> exchange = new FutureTask<>(this::requestFingerprints);
        Thread.ofVirtual().name("ssh-fingerprints-exchange").start(exchange);
        try {
            return exchange.get(deadline.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException | InterruptedException e) {
            exchange.cancel(true);
            throw e;
        }
    }
}
