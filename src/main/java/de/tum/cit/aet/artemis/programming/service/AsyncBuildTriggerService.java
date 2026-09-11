package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

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

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    public AsyncBuildTriggerService(Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService,
            ProgrammingExerciseRepository programmingExerciseRepository) {
        this.continuousIntegrationTriggerService = continuousIntegrationTriggerService;
        this.programmingExerciseRepository = programmingExerciseRepository;
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
        // A push carries no logged-in user, so this stands in. When the trigger does come from a request, the
        // caller's context now reaches this thread and is kept instead.
        SecurityUtils.setAuthorizationObject();
        try {
            attachExerciseDetailsNeededByTheTrigger(participation);
            continuousIntegrationTriggerService.orElseThrow().triggerBuild(participation, commitHash, triggeredByPushTo);
        }
        catch (Exception e) {
            // Mirrors the previous inline behaviour, which swallowed a failed trigger rather than failing the push.
            log.error("Could not queue a build for participation {} and commit {}", participation.getId(), commitHash, e);
        }
    }

    /**
     * Loads the build config and the auxiliary repositories onto the exercise before the trigger reads them.
     * <p>
     * Both are per-exercise values that the trigger otherwise resolves with a query each, on every push, for the same
     * handful of exercises. Their loaders return the association when it is already initialized, so one load here
     * replaces both queries. Nothing is retained between pushes, so there is nothing to invalidate when an instructor
     * changes the exercise: the next push reads it again.
     *
     * @param participation the participation whose exercise should carry the details the trigger needs
     */
    private void attachExerciseDetailsNeededByTheTrigger(ProgrammingExerciseParticipation participation) {
        ProgrammingExercise exercise = participation.getProgrammingExercise();
        if (exercise == null) {
            return;
        }
        boolean buildConfigLoaded = exercise.getBuildConfig() != null && Hibernate.isInitialized(exercise.getBuildConfig());
        boolean auxiliaryRepositoriesLoaded = Hibernate.isInitialized(exercise.getAuxiliaryRepositories());
        if (buildConfigLoaded && auxiliaryRepositoriesLoaded) {
            return;
        }
        programmingExerciseRepository.findWithBuildConfigAndAuxiliaryRepositoriesById(exercise.getId()).ifPresent(loaded -> {
            exercise.setBuildConfig(loaded.getBuildConfig());
            exercise.setAuxiliaryRepositories(loaded.getAuxiliaryRepositories());
        });
    }
}
