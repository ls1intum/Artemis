package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.FeatureKind;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsageCollector;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Counts git clone, pull and push operations for the feature usage analysis.
 * <p>
 * Git traffic is the most direct measure there is of how much programming exercises are actually used, and it is
 * invisible to everything else: these requests are served by a plain servlet at {@code /git/*} and never reach a Spring
 * MVC handler, which is also why they exhaust Micrometer's URI tag budget instead of producing anything useful there.
 * <p>
 * Only the single {@code POST} of an operation is counted. A clone or push is three HTTP requests, two handshakes on
 * {@code /info/refs} and one data transfer, so counting every request would inflate the numbers threefold and count
 * abandoned handshakes as usage.
 * <p>
 * The repository is reduced to one of four kinds, so the identifier stays bounded. Without that, every student
 * repository would become its own feature: the parsed value is the repository type for staff repositories but the
 * <i>username</i> for student ones. Auxiliary repositories are counted as {@code assignment}, because telling them apart
 * from a username needs a database lookup that has no business being on the git path.
 */
@Profile(PROFILE_LOCALVC)
@Lazy
@Service
public class LocalVCUsageTrackingService {

    private static final Logger log = LoggerFactory.getLogger(LocalVCUsageTrackingService.class);

    private static final String MODULE = "localvc";

    private static final String FETCH_OPERATION = "fetch";

    private static final String PUSH_OPERATION = "push";

    /** Everything that is not a staff repository, which is student repositories plus the rare auxiliary repository. */
    private static final String ASSIGNMENT_REPOSITORY = "assignment";

    /** Used when the request URI cannot be parsed, so a malformed request cannot create an unbounded identifier. */
    private static final String UNKNOWN_REPOSITORY = "unknown";

    private static final Map<String, String> STAFF_REPOSITORY_KINDS = Map.of(RepositoryType.TEMPLATE.toString(), "template", RepositoryType.SOLUTION.toString(), "solution",
            RepositoryType.TESTS.toString(), "tests");

    private final Optional<FeatureUsageCollector> featureUsageCollector;

    private final LocalVCServletService localVCServletService;

    public LocalVCUsageTrackingService(Optional<FeatureUsageCollector> featureUsageCollector, LocalVCServletService localVCServletService) {
        this.featureUsageCollector = featureUsageCollector;
        this.localVCServletService = localVCServletService;
    }

    /**
     * Counts one completed fetch (clone or pull).
     *
     * @param request    the git request that was served
     * @param durationMs how long serving it took
     * @param failed     whether it ended in an error
     */
    public void recordFetch(HttpServletRequest request, long durationMs, boolean failed) {
        record(request, FETCH_OPERATION, durationMs, failed);
    }

    /**
     * Counts one completed push.
     *
     * @param request    the git request that was served
     * @param durationMs how long serving it took
     * @param failed     whether it ended in an error
     */
    public void recordPush(HttpServletRequest request, long durationMs, boolean failed) {
        record(request, PUSH_OPERATION, durationMs, failed);
    }

    /**
     * Guarded as a whole, and not only in its parts, because both callers invoke this from a {@code finally} block around
     * the git transfer itself. Recording is synchronous on the git request thread, so anything thrown here would surface to
     * the client as a failed clone or push - and an exception thrown out of a {@code finally} also discards whatever the
     * transfer was already failing with. A usage counter must never be able to break the operation it measures, least of
     * all one a student is waiting on.
     */
    private void record(HttpServletRequest request, String operation, long durationMs, boolean failed) {
        try {
            if (featureUsageCollector.isEmpty() || !featureUsageCollector.get().isEnabled()) {
                return;
            }
            if (!HttpMethod.POST.name().equals(request.getMethod())) {
                return;
            }
            // The role of a git caller is not available here: LocalVC authenticates the request itself and never populates
            // the security context, so it is recorded as ANONYMOUS and the admin page shows no role for git features. The
            // interesting distinction, staff repository against student repository, is in the identifier instead.
            featureUsageCollector.get().recordUsage(FeatureKind.GIT, MODULE, operation + '/' + repositoryKind(request), Role.ANONYMOUS, failed, durationMs);
        }
        catch (Exception e) {
            log.warn("Failed to record git {} usage for {}", operation, request.getRequestURI(), e);
        }
    }

    private String repositoryKind(HttpServletRequest request) {
        try {
            String repositoryTypeOrUserName = localVCServletService.parseRepositoryUri(request).getRepositoryTypeOrUserName();
            return STAFF_REPOSITORY_KINDS.getOrDefault(repositoryTypeOrUserName, ASSIGNMENT_REPOSITORY);
        }
        catch (Exception e) {
            log.debug("Could not determine the repository kind of {} for usage tracking", request.getRequestURI(), e);
            return UNKNOWN_REPOSITORY;
        }
    }
}
