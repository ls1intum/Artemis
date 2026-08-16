package de.tum.cit.aet.artemis.localci.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;

/**
 * Issues and checks the credential a build agent uses to clone the repositories of one specific build job.
 * <p>
 * The token replaces the installation-wide build agent password on the https path. It is minted when the job is
 * queued, travels with the job, and is accepted only for the repositories that job declares and only while the job is
 * in the distributed processing list. Nothing here is time based: the build timeout already removes a job from that
 * list, so an expiry would only duplicate a bound the system already enforces, and would add a clock to disagree over.
 * <p>
 * The token is not a secret from the cluster. Any node that joins can read the build job queue, so this does not
 * defend against a hostile cluster member; the cluster password and the build agent network allowlist do. What it does
 * remove is the long lived shared secret: a token recovered from a build host is useless once that job ends, opens at
 * most that job's few repositories, and only from the address the owning agent is connected from.
 */
@Service
@Profile(PROFILE_LOCALCI)
@Lazy
public class BuildJobCloneTokenService {

    /**
     * Marks the credential in a log line or a git configuration as a build job token rather than a user's password or
     * a VCS access token, which use the {@code vcpat-} prefix.
     */
    public static final String CLONE_TOKEN_PREFIX = "bjct-";

    /**
     * 64 random bytes. Unlike the VCS access tokens this is never stored in a {@code varchar(50)} column, so it is not
     * bound by their length.
     */
    private static final int TOKEN_BYTES = 64;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Creates a token for a single build job.
     *
     * @return a fresh random token, prefixed so it is recognisable in a log
     */
    public String generateCloneToken() {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        return CLONE_TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Checks a presented credential against the token of a build job.
     *
     * @param buildJob       the build job whose token to compare against, may be null
     * @param presentedToken the credential presented in the request, may be null
     * @return whether the job carries a token and the presented value matches it
     */
    public boolean tokenMatches(@Nullable BuildJobQueueItem buildJob, @Nullable String presentedToken) {
        if (buildJob == null || buildJob.cloneToken() == null || presentedToken == null) {
            return false;
        }
        // Time-constant, like the other credential comparisons on this path
        return MessageDigest.isEqual(buildJob.cloneToken().getBytes(StandardCharsets.UTF_8), presentedToken.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Collects every repository a build job legitimately reads: the participant's assignment repository, the tests,
     * the solution when the exercise checks it out, and any auxiliary repositories.
     * <p>
     * A token is accepted for these and nothing else, which is what turns "this caller is a build agent" into "this
     * caller is the build of this participation".
     *
     * @param buildJob the build job, may be null
     * @return the repository URIs of that job, empty if it has none
     */
    public Set<String> getRepositoryUris(@Nullable BuildJobQueueItem buildJob) {
        if (buildJob == null || buildJob.repositoryInfo() == null) {
            return Set.of();
        }
        RepositoryInfo repositoryInfo = buildJob.repositoryInfo();
        Set<String> repositoryUris = new HashSet<>();
        addIfPresent(repositoryUris, repositoryInfo.assignmentRepositoryUri());
        addIfPresent(repositoryUris, repositoryInfo.testRepositoryUri());
        addIfPresent(repositoryUris, repositoryInfo.solutionRepositoryUri());
        if (repositoryInfo.auxiliaryRepositoryUris() != null) {
            for (String auxiliaryRepositoryUri : repositoryInfo.auxiliaryRepositoryUris()) {
                addIfPresent(repositoryUris, auxiliaryRepositoryUri);
            }
        }
        return repositoryUris;
    }

    private static void addIfPresent(Set<String> repositoryUris, @Nullable String repositoryUri) {
        if (repositoryUri != null && !repositoryUri.isBlank()) {
            repositoryUris.add(repositoryUri);
        }
    }
}
