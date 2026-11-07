package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.cit.aet.artemis.core.config.RateLimitingProperties;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.localvc.util.LocalVCTestRepository;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;

/**
 * Verifies that an exhausted authentication rate limit cannot be used to enumerate repositories. Once the bucket is
 * empty, a missing repository and an existing but forbidden one must be answered with an identical 429, for both the
 * fetch and the push handshake. This guards the residual oracle on top of finding F-014: before the fix a missing repo
 * returned 401 (never reaching the limiter) while an existing one returned a 500 from the uncaught rate-limit exception.
 * <p>
 * Rate limiting is enabled at runtime rather than through {@code @TestPropertySource}, which would spawn an extra Spring
 * context and is forbidden by {@code SpringContextConfigurationArchitectureTest}.
 */
class LocalVCRateLimitEnumerationMaskingTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "localvcratelimit";

    private static final int AUTHENTICATION_REQUESTS_PER_MINUTE = 3;

    // A made-up account with a wrong password, exactly as an enumerating attacker would probe.
    private static final String BOGUS_AUTHORIZATION = "Basic " + Base64.getEncoder().encodeToString("niemand999:FALSCH123".getBytes(StandardCharsets.UTF_8));

    private final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @Autowired
    private RateLimitingProperties rateLimitingProperties;

    @Autowired
    private FeatureToggleService featureToggleService;

    private LocalVCTestRepository assignmentRepository;

    private boolean previousRateLimitingEnabled;

    private Integer previousAuthenticationRequestsPerMinute;

    private boolean previousRateLimitFeatureEnabled;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @BeforeEach
    void enableRateLimitingAndCreateExistingRepository() throws Exception {
        previousRateLimitingEnabled = rateLimitingProperties.isEnabled();
        previousAuthenticationRequestsPerMinute = rateLimitingProperties.getAuthenticationRequestsPerMinute();
        previousRateLimitFeatureEnabled = featureToggleService.isFeatureEnabled(Feature.RateLimit);
        rateLimitingProperties.setEnabled(true);
        rateLimitingProperties.setAuthenticationRequestsPerMinute(AUTHENTICATION_REQUESTS_PER_MINUTE);
        featureToggleService.enableFeature(Feature.RateLimit);

        assignmentRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey1, assignmentRepositorySlug);
    }

    @AfterEach
    void disableRateLimitingAndRemoveRepositories() throws IOException {
        // Restore the previous feature state rather than always disabling it, so a shared context that had the feature
        // on keeps it on for later tests.
        if (previousRateLimitFeatureEnabled) {
            featureToggleService.enableFeature(Feature.RateLimit);
        }
        else {
            featureToggleService.disableFeature(Feature.RateLimit);
        }
        rateLimitingProperties.setEnabled(previousRateLimitingEnabled);
        rateLimitingProperties.setAuthenticationRequestsPerMinute(previousAuthenticationRequestsPerMinute);
        assignmentRepository.deleteWorkingCopy();
        RepositoryExportTestUtil.cleanupTrackedRepositories();
    }

    @Test
    void fetchHandshakeUnderAnExhaustedRateLimitDoesNotRevealWhetherRepositoryExists() throws Exception {
        assertExhaustedHandshakeIndistinguishable("git-upload-pack");
    }

    @Test
    void pushHandshakeUnderAnExhaustedRateLimitDoesNotRevealWhetherRepositoryExists() throws Exception {
        assertExhaustedHandshakeIndistinguishable("git-receive-pack");
    }

    private void assertExhaustedHandshakeIndistinguishable(String service) throws Exception {
        String missingSlug = projectKey1.toLowerCase(Locale.ROOT) + "-doesnotexist";

        // Spend the whole authentication budget on a known existing repository until the limiter starts refusing.
        RawGitResponse throttled = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            RawGitResponse response = infoRefs(assignmentRepositorySlug, service);
            if (response.status() == 429) {
                throttled = response;
                break;
            }
        }
        assertThat(throttled).as("the authentication rate limit should become exhausted").isNotNull();

        RawGitResponse existingButForbidden = infoRefs(assignmentRepositorySlug, service);
        RawGitResponse missing = infoRefs(missingSlug, service);

        // The missing and the existing repository must now be answered identically, so existence cannot be inferred.
        assertThat(missing.status()).isEqualTo(429);
        assertThat(existingButForbidden.status()).isEqualTo(429);
        assertThat(missing.wwwAuthenticate()).isEqualTo(existingButForbidden.wwwAuthenticate());
        assertThat(missing.retryAfter()).isEqualTo(existingButForbidden.retryAfter());
        assertThat(missing.contentType()).isEqualTo(existingButForbidden.contentType());
        assertThat(missing.contentLength()).isEqualTo(existingButForbidden.contentLength());
        assertThat(missing.body()).isEqualTo(existingButForbidden.body());

        // The throttled response must not leak existence through a challenge, a body, or the repository name.
        assertThat(missing.wwwAuthenticate()).as("throttling is not a missing credential").isNull();
        assertThat(missing.body()).isEmpty();
        assertThat(missing.body().toLowerCase(Locale.ROOT)).doesNotContain("not found", "does not exist", missingSlug);
    }

    private RawGitResponse infoRefs(String repositorySlug, String service) throws Exception {
        URI uri = UriComponentsBuilder.fromUri(localVCBaseUri).port(port).pathSegment("git", projectKey1.toUpperCase(Locale.ROOT), repositorySlug + ".git", "info", "refs")
                .queryParam("service", service).build().toUri();
        HttpRequest request = HttpRequest.newBuilder(uri).header(HttpHeaders.AUTHORIZATION, BOGUS_AUTHORIZATION).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new RawGitResponse(response.statusCode(), response.headers().firstValue("WWW-Authenticate").orElse(null), response.headers().firstValue("Retry-After").orElse(null),
                response.headers().firstValue("Content-Type").orElse(null), response.headers().firstValue("Content-Length").orElse(null), response.body());
    }

    private record RawGitResponse(int status, String wwwAuthenticate, String retryAfter, String contentType, String contentLength, String body) {
    }
}
