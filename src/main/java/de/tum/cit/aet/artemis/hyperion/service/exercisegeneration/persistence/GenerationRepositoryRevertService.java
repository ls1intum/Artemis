package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/** Restores captured repository heads without overwriting later instructor commits. */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationRepositoryRevertService {

    private static final Logger log = LoggerFactory.getLogger(GenerationRepositoryRevertService.class);

    private static final RepositoryType[] REVERT_ORDER = { RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS };

    private final GitService gitService;

    private final TempFileUtilService tempFileUtilService;

    public GenerationRepositoryRevertService(GitService gitService, TempFileUtilService tempFileUtilService) {
        this.gitService = gitService;
        this.tempFileUtilService = tempFileUtilService;
    }

    /**
     * Resets repositories in persistence order and stops at the first failure or lost mutation slot.
     *
     * @param exercise              the exercise to restore
     * @param baseline              the captured repository heads and their expected generated successors
     * @param repositoryBranch      the branch used during persistence
     * @param stillOwnsMutationSlot the ownership check before each reset
     * @return whether every captured repository was restored, with the completed resets
     */
    public RepositoryRevertResult revert(ProgrammingExercise exercise, ExerciseGenerationBaseline baseline, String repositoryBranch, BooleanSupplier stillOwnsMutationSlot) {
        List<RepositoryType> reverted = new ArrayList<>();
        for (RepositoryType repositoryType : REVERT_ORDER) {
            String head = baseline.headFor(repositoryType);
            LocalVCRepositoryUri uri = exercise.getRepositoryURI(repositoryType);
            if (head == null || uri == null) {
                continue;
            }
            if (!stillOwnsMutationSlot.getAsBoolean()) {
                log.error("Stopped reverting generated changes for exercise {} because this node lost the exercise mutation slot before resetting {}", exercise.getId(),
                        repositoryType);
                return new RepositoryRevertResult(false, List.copyOf(reverted));
            }
            if (!revertRepository(exercise.getId(), repositoryType, uri, head, baseline.expectedCurrentHeadFor(repositoryType), repositoryBranch)) {
                return new RepositoryRevertResult(false, List.copyOf(reverted));
            }
            reverted.add(repositoryType);
        }
        return new RepositoryRevertResult(true, List.copyOf(reverted));
    }

    /**
     * Captures restored heads for the guarded metadata synchronization, including repositories not reset by this baseline.
     *
     * @param exercise         the restored exercise
     * @param repositoryBranch the branch restored by this revert
     * @param baseline         the captured pre-run heads
     * @return the repository heads expected by metadata synchronization
     */
    public Map<RepositoryType, String> captureRepositoryHeads(ProgrammingExercise exercise, String repositoryBranch, ExerciseGenerationBaseline baseline) {
        Map<RepositoryType, String> heads = new EnumMap<>(RepositoryType.class);
        for (RepositoryType repositoryType : REVERT_ORDER) {
            LocalVCRepositoryUri uri = exercise.getRepositoryURI(repositoryType);
            if (uri == null) {
                continue;
            }
            String head = baseline.headFor(repositoryType);
            if (head == null) {
                head = gitService.getLastCommitHash(uri, repositoryBranch);
            }
            if (head == null) {
                throw new IllegalStateException("Could not resolve the " + repositoryType + " repository HEAD after reverting it");
            }
            heads.put(repositoryType, head);
        }
        return Map.copyOf(heads);
    }

    private boolean revertRepository(long exerciseId, RepositoryType repositoryType, LocalVCRepositoryUri uri, String head, String expectedCurrentHead, String repositoryBranch) {
        Repository repository = null;
        Path temporaryCheckout = null;
        try {
            temporaryCheckout = tempFileUtilService.createTempDirectory("hyperion-revert-");
            repository = gitService.getOrCheckoutRepositoryOnBranch(uri, temporaryCheckout.resolve("repository"), repositoryBranch);
            if (repository == null) {
                throw new IllegalStateException("Could not check out the repository to revert it");
            }
            resetToBaseline(repository, uri, head, expectedCurrentHead, repositoryBranch);
            refreshCachedCheckout(uri, repositoryBranch);
            log.info("Reverted the {} repository of exercise {} back to its pre-generated commit {}", repositoryType, exerciseId, head);
            return true;
        }
        catch (Exception e) {
            log.error("Failed to revert the {} repository of exercise {} back to {}; the exercise may be inconsistent", repositoryType, exerciseId, head, e);
            return false;
        }
        finally {
            if (repository != null) {
                repository.closeBeforeDelete();
            }
            if (temporaryCheckout != null && !FileUtils.deleteQuietly(temporaryCheckout.toFile())) {
                log.warn("Could not delete temporary Hyperion generation-revert checkout {}", temporaryCheckout);
            }
        }
    }

    private void resetToBaseline(Repository repository, LocalVCRepositoryUri uri, String head, String expectedCurrentHead, String repositoryBranch) throws GitAPIException {
        String currentHead = gitService.getLastCommitHash(uri, repositoryBranch);
        if (head.equals(currentHead)) {
            return;
        }
        if (expectedCurrentHead == null) {
            throw new IllegalStateException("No post-run HEAD was captured for this repository; refusing to force-push without clobber protection");
        }
        if (!expectedCurrentHead.equals(currentHead)) {
            throw new IllegalStateException("Current repository HEAD " + currentHead + " differs from the generated commit " + expectedCurrentHead);
        }
        gitService.resetToCommitAndForcePush(repository, head, expectedCurrentHead, repositoryBranch);
    }

    private void refreshCachedCheckout(LocalVCRepositoryUri uri, String repositoryBranch) {
        try {
            Repository cachedRepository = gitService.getOrCheckoutRepository(uri, false, repositoryBranch, false);
            if (cachedRepository != null) {
                gitService.fetchAll(cachedRepository);
                gitService.reset(cachedRepository, "origin/" + repositoryBranch);
            }
        }
        catch (Exception e) {
            log.warn("Could not refresh the cached repository after reverting {}", uri, e);
            gitService.deleteLocalRepository(uri);
        }
    }

    public record RepositoryRevertResult(boolean fullyReverted, List<RepositoryType> revertedRepositories) {
    }
}
