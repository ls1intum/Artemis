package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Queues continuous integration builds off the request thread.
 * <p>
 * Queueing a build is not something a git push has to wait for. The objects are already written when the post-receive
 * hook runs, and nothing in the push response depends on the build job existing yet. Measured during an exam-scale run,
 * the queueing step accounted for effectively the whole latency of a push: about 15 to 35 milliseconds when
 * uncontended, but seconds once many students pushed at once.
 * <p>
 * This is a separate bean rather than an {@code @Async} method on the calling service because Spring implements
 * {@code @Async} with a proxy: a call from one method of a bean to another method of the same bean does not pass
 * through that proxy and would silently run synchronously.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class AsyncBuildTriggerService {

    private static final Logger log = LoggerFactory.getLogger(AsyncBuildTriggerService.class);

    private final Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService;

    public AsyncBuildTriggerService(Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService) {
        this.continuousIntegrationTriggerService = continuousIntegrationTriggerService;
    }

    /**
     * Queues a build for the given participation and commit.
     * <p>
     * The participation is passed as it was loaded on the calling thread. It is detached either way, so this thread can
     * read exactly what the caller could and no more.
     *
     * @param participation     the participation whose repository should be built
     * @param commitHash        the commit that triggered the build
     * @param triggeredByPushTo the type of repository that was pushed to, null if the caller does not know it
     */
    @Async("buildTriggerExecutor")
    public void triggerBuild(ProgrammingExerciseParticipation participation, String commitHash, RepositoryType triggeredByPushTo) {
        // The pushing user is not logged into Artemis, so custom repository methods need an authorization object on this
        // thread as well; the calling thread's one does not carry over.
        SecurityUtils.setAuthorizationObject();
        try {
            continuousIntegrationTriggerService.orElseThrow().triggerBuild(participation, commitHash, triggeredByPushTo);
        }
        catch (Exception e) {
            // Mirrors the previous inline behaviour, which swallowed a failed trigger rather than failing the push.
            log.error("Could not queue a build for participation {} and commit {}", participation.getId(), commitHash, e);
        }
    }
}
