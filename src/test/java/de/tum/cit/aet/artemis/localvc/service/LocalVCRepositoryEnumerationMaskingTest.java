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
import org.springframework.http.HttpHeaders;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.cit.aet.artemis.localvc.util.LocalVCTestRepository;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;

/**
 * Verifies at the HTTP wire level that a non-existent repository cannot be told apart from an existing but forbidden one
 * on the git handshake. This is the regression guard for finding F-014 (unauthenticated repository and participation
 * enumeration via the status codes 401 versus 404).
 * <p>
 * The client-level assertions in {@link LocalVCIntegrationTest#testFetchPush_repositoryDoesNotExist} only observe the
 * status code (through JGit's exception message). This test additionally compares the challenge header and the response
 * body, which is where the oracle would otherwise reappear once the status code is unified.
 */
class LocalVCRepositoryEnumerationMaskingTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "localvcenum";

    // Fully unauthenticated, exactly as an enumerating attacker would probe: a made-up account with a wrong password.
    // The distinction between existing and missing must not survive even this, since it is decided before authentication.
    private static final String BOGUS_AUTHORIZATION = "Basic " + Base64.getEncoder().encodeToString("niemand999:FALSCH123".getBytes(StandardCharsets.UTF_8));

    private final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private LocalVCTestRepository assignmentRepository;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @BeforeEach
    void createExistingRepository() throws Exception {
        // An existing repository the bogus credentials are not authorized for.
        assignmentRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey1, assignmentRepositorySlug);
    }

    @AfterEach
    void removeRepositories() throws IOException {
        assignmentRepository.deleteWorkingCopy();
        RepositoryExportTestUtil.cleanupTrackedRepositories();
    }

    @Test
    void fetchHandshakeDoesNotRevealWhetherRepositoryExists() throws Exception {
        assertHandshakeIndistinguishable("git-upload-pack");
    }

    @Test
    void pushHandshakeDoesNotRevealWhetherRepositoryExists() throws Exception {
        assertHandshakeIndistinguishable("git-receive-pack");
    }

    private void assertHandshakeIndistinguishable(String service) throws Exception {
        String missingSlug = getProjectKeyLower() + "-doesnotexist";

        RawGitResponse existingButForbidden = infoRefs(assignmentRepositorySlug, service);
        RawGitResponse missing = infoRefs(missingSlug, service);

        // The missing repository must be answered with 401, not 404: that is the fix for the status-code oracle.
        assertThat(missing.status()).as("a missing repository must not be answered with a different status than an existing one").isEqualTo(401);
        assertThat(existingButForbidden.status()).isEqualTo(401);

        // ... and the two 401 responses must be indistinguishable, so the oracle does not simply move to the header or body.
        assertThat(missing.status()).isEqualTo(existingButForbidden.status());
        assertThat(missing.wwwAuthenticate()).isEqualTo(existingButForbidden.wwwAuthenticate());
        assertThat(missing.contentType()).isEqualTo(existingButForbidden.contentType());
        assertThat(missing.contentLength()).isEqualTo(existingButForbidden.contentLength());
        assertThat(missing.body()).isEqualTo(existingButForbidden.body());

        // The masked response must not leak that the repository is missing, neither through a challenge nor through a body.
        assertThat(missing.wwwAuthenticate()).isEqualTo("Basic");
        assertThat(missing.body()).isEmpty();
        assertThat(missing.body().toLowerCase(Locale.ROOT)).doesNotContain("not found", "does not exist", missingSlug);
    }

    private RawGitResponse infoRefs(String repositorySlug, String service) throws Exception {
        URI uri = UriComponentsBuilder.fromUri(localVCBaseUri).port(port).pathSegment("git", projectKey1.toUpperCase(Locale.ROOT), repositorySlug + ".git", "info", "refs")
                .queryParam("service", service).build().toUri();
        HttpRequest request = HttpRequest.newBuilder(uri).header(HttpHeaders.AUTHORIZATION, BOGUS_AUTHORIZATION).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new RawGitResponse(response.statusCode(), response.headers().firstValue("WWW-Authenticate").orElse(null), response.headers().firstValue("Content-Type").orElse(null),
                response.headers().firstValue("Content-Length").orElse(null), response.body());
    }

    private String getProjectKeyLower() {
        return projectKey1.toLowerCase(Locale.ROOT);
    }

    private record RawGitResponse(int status, String wwwAuthenticate, String contentType, String contentLength, String body) {
    }
}
