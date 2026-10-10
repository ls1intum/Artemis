package de.tum.cit.aet.artemis.localvc.service;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpMethod;

import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * Classifies an incoming smart-HTTP git request. This is the single source of truth for which requests are the
 * reference-advertisement handshake versus the data-transfer service, and for mapping them to a {@link RepositoryActionType}.
 * <p>
 * Only the four smart-HTTP combinations are valid, because the dumb HTTP protocol is disabled on the git servlet. Every
 * other request is {@link Kind#MALFORMED} and must not reach repository resolution.
 */
final class GitRequestClassifier {

    private static final String INFO_REFS_SUFFIX = "/info/refs";

    private static final String UPLOAD_PACK_SUFFIX = "/git-upload-pack";

    private static final String RECEIVE_PACK_SUFFIX = "/git-receive-pack";

    private static final String UPLOAD_PACK_SERVICE = "git-upload-pack";

    private static final String RECEIVE_PACK_SERVICE = "git-receive-pack";

    private GitRequestClassifier() {
    }

    enum Kind {
        HANDSHAKE, SERVICE, MALFORMED
    }

    /**
     * @param action the repository action the request performs, or {@code null} when the request is {@link Kind#MALFORMED}
     * @param kind   the kind of git request
     */
    record GitRequestTarget(RepositoryActionType action, Kind kind) {
    }

    /**
     * Whether the request is the reference-advertisement handshake ({@code GET .../info/refs}). This is the only request
     * that consumes the authentication rate limit, and it is the shared predicate the admission filter and the servlet
     * service both use, rather than repeating the path check.
     *
     * @param request the incoming git request
     * @return {@code true} if the request is the {@code GET .../info/refs} handshake
     */
    static boolean isInfoRefsHandshake(HttpServletRequest request) {
        return HttpMethod.GET.matches(request.getMethod()) && request.getRequestURI().endsWith(INFO_REFS_SUFFIX);
    }

    /**
     * Classifies the request into one of the four valid smart-HTTP combinations, or {@link Kind#MALFORMED}.
     *
     * @param request the incoming git request
     * @return the classified target
     */
    static GitRequestTarget classify(HttpServletRequest request) {
        if (isInfoRefsHandshake(request)) {
            String service = request.getParameter("service");
            if (UPLOAD_PACK_SERVICE.equals(service)) {
                return new GitRequestTarget(RepositoryActionType.READ, Kind.HANDSHAKE);
            }
            if (RECEIVE_PACK_SERVICE.equals(service)) {
                return new GitRequestTarget(RepositoryActionType.WRITE, Kind.HANDSHAKE);
            }
            return new GitRequestTarget(null, Kind.MALFORMED);
        }
        if (HttpMethod.POST.matches(request.getMethod())) {
            String uri = request.getRequestURI();
            if (uri.endsWith(UPLOAD_PACK_SUFFIX)) {
                return new GitRequestTarget(RepositoryActionType.READ, Kind.SERVICE);
            }
            if (uri.endsWith(RECEIVE_PACK_SUFFIX)) {
                return new GitRequestTarget(RepositoryActionType.WRITE, Kind.SERVICE);
            }
        }
        return new GitRequestTarget(null, Kind.MALFORMED);
    }
}
