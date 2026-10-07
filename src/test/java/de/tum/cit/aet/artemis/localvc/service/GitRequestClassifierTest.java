package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import de.tum.cit.aet.artemis.localvc.service.GitRequestClassifier.GitRequestTarget;
import de.tum.cit.aet.artemis.localvc.service.GitRequestClassifier.Kind;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * Unit tests for the single source of truth that classifies git smart-HTTP requests. The four valid combinations must
 * map to the right action, and everything else must be rejected as malformed so it never reaches repository resolution.
 */
class GitRequestClassifierTest {

    private static final String REPO = "/git/TEST/test-student.git";

    private static MockHttpServletRequest request(String method, String uri, String service) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        if (service != null) {
            request.setParameter("service", service);
        }
        return request;
    }

    @Test
    void uploadPackHandshakeIsAReadHandshake() {
        GitRequestTarget target = GitRequestClassifier.classify(request("GET", REPO + "/info/refs", "git-upload-pack"));
        assertThat(target.kind()).isEqualTo(Kind.HANDSHAKE);
        assertThat(target.action()).isEqualTo(RepositoryActionType.READ);
    }

    @Test
    void receivePackHandshakeIsAWriteHandshake() {
        GitRequestTarget target = GitRequestClassifier.classify(request("GET", REPO + "/info/refs", "git-receive-pack"));
        assertThat(target.kind()).isEqualTo(Kind.HANDSHAKE);
        assertThat(target.action()).isEqualTo(RepositoryActionType.WRITE);
    }

    @Test
    void uploadPackServiceIsAReadService() {
        GitRequestTarget target = GitRequestClassifier.classify(request("POST", REPO + "/git-upload-pack", null));
        assertThat(target.kind()).isEqualTo(Kind.SERVICE);
        assertThat(target.action()).isEqualTo(RepositoryActionType.READ);
    }

    @Test
    void receivePackServiceIsAWriteService() {
        GitRequestTarget target = GitRequestClassifier.classify(request("POST", REPO + "/git-receive-pack", null));
        assertThat(target.kind()).isEqualTo(Kind.SERVICE);
        assertThat(target.action()).isEqualTo(RepositoryActionType.WRITE);
    }

    @Test
    void infoRefsWithoutAServiceIsMalformed() {
        assertThat(GitRequestClassifier.classify(request("GET", REPO + "/info/refs", null)).kind()).isEqualTo(Kind.MALFORMED);
    }

    @Test
    void infoRefsWithAnUnknownServiceIsMalformed() {
        assertThat(GitRequestClassifier.classify(request("GET", REPO + "/info/refs", "git-evil-pack")).kind()).isEqualTo(Kind.MALFORMED);
    }

    @Test
    void postToInfoRefsIsMalformed() {
        assertThat(GitRequestClassifier.classify(request("POST", REPO + "/info/refs", "git-upload-pack")).kind()).isEqualTo(Kind.MALFORMED);
    }

    @Test
    void getToAServicePathIsMalformed() {
        assertThat(GitRequestClassifier.classify(request("GET", REPO + "/git-upload-pack", null)).kind()).isEqualTo(Kind.MALFORMED);
    }

    @Test
    void anUnknownPathIsMalformed() {
        assertThat(GitRequestClassifier.classify(request("GET", REPO + "/HEAD", null)).kind()).isEqualTo(Kind.MALFORMED);
    }

    @Test
    void theHandshakePredicateIgnoresTheServiceParameter() {
        assertThat(GitRequestClassifier.isInfoRefsHandshake(request("GET", REPO + "/info/refs", null))).isTrue();
        assertThat(GitRequestClassifier.isInfoRefsHandshake(request("POST", REPO + "/git-upload-pack", null))).isFalse();
    }
}
