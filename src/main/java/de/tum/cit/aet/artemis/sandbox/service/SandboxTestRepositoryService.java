package de.tum.cit.aet.artemis.sandbox.service;

import static de.tum.cit.aet.artemis.sandbox.service.SandboxConfigurationService.ENTITY_NAME;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.localvc.service.BareGitRepositoryService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCPushProcessingService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.sandbox.config.SandboxEnabled;
import de.tum.cit.aet.artemis.sandbox.domain.PolicyFile;
import de.tum.cit.aet.artemis.sandbox.dto.SandboxConfigurationDTO;
import de.tum.cit.aet.artemis.sandbox.util.TestRepositoryCheckout;
import de.tum.cit.aet.artemis.sandbox.util.TestRepositoryFiles;

/**
 * Changes the exercise-tests repository of a programming exercise for the sandbox: in a private checkout, as one commit authored by the acting user, pushed
 * with a lease and processed like any other push. The commit is visible in the repository history and can be reverted there.
 */
@Conditional(SandboxEnabled.class)
@Lazy
@Service
public class SandboxTestRepositoryService {

    private static final Logger log = LoggerFactory.getLogger(SandboxTestRepositoryService.class);

    /** Marks the commits of this service whose push processing it runs itself, so a retry can tell an unprocessed change of its own from any other commit. */
    private static final String COMMIT_TRAILER = "Changed-by: Artemis Sandbox";

    /**
     * Records what a commit of this service did. Activating looks up the last deactivation by it, to restore the annotations as they were before.
     */
    public static final String ACTION_TRAILER = "Sandbox-Action: ";

    /**
     * The sandbox commits whose push processing has not completed, by exercise id and commit hash. An entry is added before the commit is pushed and removed only
     * after the whole processing has returned, so a retry also resumes a processing that failed after it had already saved the solution submission.
     */
    private static final String PENDING_PROCESSING_MAP = "sandboxPendingProcessing";

    /** How long a retry can still resume a failed processing from the pending entry; afterwards only the submission check below applies. */
    private static final Duration PENDING_PROCESSING_TIME_TO_LIVE = Duration.ofDays(7);

    private final GitService gitService;

    private final BareGitRepositoryService bareGitRepositoryService;

    private final TempFileUtilService tempFileUtilService;

    private final Optional<LocalVCPushProcessingService> localVCPushProcessingService;

    private final SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final DistributedDataProvider distributedDataProvider;

    private final SandboxConfigurationService configurationService;

    public SandboxTestRepositoryService(GitService gitService, BareGitRepositoryService bareGitRepositoryService, TempFileUtilService tempFileUtilService,
            Optional<LocalVCPushProcessingService> localVCPushProcessingService, SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository,
            ProgrammingSubmissionRepository programmingSubmissionRepository, DistributedDataProvider distributedDataProvider, SandboxConfigurationService configurationService) {
        this.gitService = gitService;
        this.bareGitRepositoryService = bareGitRepositoryService;
        this.tempFileUtilService = tempFileUtilService;
        this.localVCPushProcessingService = localVCPushProcessingService;
        this.solutionParticipationRepository = solutionParticipationRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.distributedDataProvider = distributedDataProvider;
        this.configurationService = configurationService;
    }

    /**
     * Applies a change in a private checkout of the tests repository and pushes it. The shared working copy that the online editor uses is never touched,
     * so neither side can reset or commit the other's files. The push is leased on the commit the checkout started from: if someone pushed in between, it is
     * rejected, the request fails and nothing is overwritten.
     *
     * After the push, the push processing runs like after any other push: it triggers the template and solution builds, which then use the changed sandbox.
     *
     * @param exercise      the programming exercise
     * @param user          the acting user, who authors the commit
     * @param commitMessage the message of the commit
     * @param action        what the change does, recorded in the commit so a later activation can find the last deactivation
     * @param change        changes the files of the checkout
     * @return the configuration after the change
     */
    public SandboxConfigurationDTO changeTestRepository(ProgrammingExercise exercise, User user, String commitMessage, String action, Consumer<Repository> change) {
        LocalVCRepositoryUri testRepositoryUri = configurationService.testRepositoryUri(exercise);
        String branch = configurationService.repositoryBranch(exercise);
        Repository repository = null;
        Path temporaryCheckout = null;
        try {
            temporaryCheckout = tempFileUtilService.createTempDirectory("sandbox-");
            repository = gitService.getOrCheckoutRepositoryOnBranch(testRepositoryUri, temporaryCheckout.resolve("repository"), branch);
            String checkedOutHead = gitService.getLocalHeadHash(repository);
            rejectSymbolicLinks(exercise, repository);
            change.accept(repository);
            boolean changed = gitService.stageAllChanges(repository);
            requireStagedPolicyFiles(repository);
            if (changed) {
                String trailers = "\n\n" + ACTION_TRAILER + action + "\n" + COMMIT_TRAILER;
                String commitHash = gitService.commitStagedChanges(repository, commitMessage + trailers, user);
                pushAndProcess(exercise, user, repository, branch, commitHash, checkedOutHead);
            }
            else if (checkedOutHead != null) {
                resumeProcessing(exercise, user, repository, branch, checkedOutHead);
            }
            return configurationService.getConfiguration(repository.getLocalPath());
        }
        catch (GitAPIException | IOException | UncheckedIOException e) {
            // Writing a file of the checkout throws UncheckedIOException; like a failed push, it leaves nothing pushed.
            log.error("Could not apply the sandbox change to the tests repository of exercise {}", exercise.getId(), e);
            throw new InternalServerErrorAlertException("Could not push the sandbox change to the tests repository.", ENTITY_NAME, "sandboxPushFailed");
        }
        finally {
            if (repository != null) {
                repository.closeBeforeDelete();
            }
            if (temporaryCheckout != null && !FileUtils.deleteQuietly(temporaryCheckout.toFile())) {
                log.warn("Could not delete the temporary sandbox checkout {}", temporaryCheckout);
            }
        }
    }

    /**
     * Pushes the commit and runs the push processing. The change is recorded as pending first, so a retry can tell that its processing has not completed if
     * the push succeeds but the processing fails.
     */
    private void pushAndProcess(ProgrammingExercise exercise, User user, Repository repository, String branch, String commitHash, String checkedOutHead)
            throws GitAPIException, IOException {
        if (localVCPushProcessingService.isPresent()) {
            pendingProcessing().put(pendingProcessingKey(exercise, commitHash), commitHash);
        }
        pushWithLease(exercise, repository, commitHash, branch, checkedOutHead);
        processPushToTestRepository(exercise, configurationService.testRepositoryUri(exercise), branch, commitHash, user);
    }

    /**
     * A retry after the processing of an earlier change failed: the change is already pushed, so the processing is resumed at the head, which contains it.
     */
    private void resumeProcessing(ProgrammingExercise exercise, User user, Repository repository, String branch, String head) throws IOException {
        Optional<String> unprocessed = unprocessedSandboxCommit(exercise, repository, head);
        if (unprocessed.isPresent()) {
            processPushToTestRepository(exercise, configurationService.testRepositoryUri(exercise), branch, head, user);
            pendingProcessing().remove(pendingProcessingKey(exercise, unprocessed.get()));
        }
    }

    /**
     * Pushes the commit if the branch still points to the commit the checkout started from.
     *
     * @throws ConflictException if someone pushed to the branch meanwhile, which rejected the lease; the caller can reload and try again
     */
    private void pushWithLease(ProgrammingExercise exercise, Repository repository, String commitHash, String branch, String checkedOutHead) throws GitAPIException, IOException {
        try {
            gitService.pushCommitWithLease(repository, commitHash, branch, checkedOutHead);
        }
        catch (GitAPIException e) {
            try (Repository serverRepository = bareGitRepositoryService.getBareRepository(configurationService.testRepositoryUri(exercise), branch, false)) {
                ObjectId head = serverRepository.resolve(Constants.R_HEADS + branch);
                if (head != null && head.name().equals(commitHash)) {
                    // The push reached the server and failed only afterwards, e.g. while reading its answer, so the change is saved.
                    log.warn("The push of {} to the tests repository of exercise {} failed after the server took it", commitHash, exercise.getId(), e);
                    return;
                }
                if (head != null && !head.name().equals(checkedOutHead)) {
                    log.info("The tests repository of exercise {} changed while a sandbox change was prepared, so it was not pushed", exercise.getId(), e);
                    throw new ConflictException("Someone changed the tests repository while this change was prepared, so it was not saved. Reload and try again.", ENTITY_NAME,
                            "sandboxConcurrentChange");
                }
            }
            throw e;
        }
    }

    /**
     * Runs the processing that follows every push to a tests repository, like a push through git or the online editor: it creates an exercise version,
     * marks the existing results as outdated and triggers the template and solution builds, which now run with the changed sandbox. It needs the server-side
     * repository, because it derives the exercise and the repository type from its location.
     */
    private void processPushToTestRepository(ProgrammingExercise exercise, LocalVCRepositoryUri testRepositoryUri, String branch, String commitHash, User user) {
        localVCPushProcessingService.ifPresent(pushProcessingService -> {
            try (Repository serverRepository = bareGitRepositoryService.getBareRepository(testRepositoryUri, branch, false)) {
                pushProcessingService.processNewPush(commitHash, serverRepository, user, Optional.empty(), Optional.empty());
                // Only now is the processing complete: the submission is saved, the results are marked as outdated and the builds are triggered.
                pendingProcessing().remove(pendingProcessingKey(exercise, commitHash));
            }
            catch (RuntimeException e) {
                // The change itself is pushed. Retrying the same action resumes the processing, see unprocessedSandboxCommit.
                log.error("Could not process the sandbox commit {} of the tests repository {}", commitHash, testRepositoryUri, e);
                throw new InternalServerErrorAlertException("The sandbox change was saved, but the builds could not be started. Please retry.", ENTITY_NAME,
                        "sandboxProcessingFailed");
            }
        });
    }

    /**
     * A sandbox commit is unprocessed if its processing has not completed: either it is still recorded as pending, or, as the head, the solution
     * participation has no submission for it. The pending entry covers a processing that failed after it had saved the submission, e.g. while triggering the
     * builds. The submission check covers a pending entry that is gone, e.g. after all nodes restarted, as long as the processing failed before saving the
     * submission. Only the latest sandbox commit counts: once its processing completed, it built every earlier one. If others pushed after it, the
     * processing of the head builds it, too, so it is resumed unless the head was processed.
     *
     * @return the latest sandbox commit up to the head, if its processing and the processing of the head have not completed
     */
    private Optional<String> unprocessedSandboxCommit(ProgrammingExercise exercise, Repository repository, String head) throws IOException {
        boolean headProcessed = solutionParticipationRepository.findByProgrammingExerciseId(exercise.getId())
                .map(solutionParticipation -> programmingSubmissionRepository.existsByParticipationIdAndCommitHash(solutionParticipation.getId(), head)).orElse(true);
        try (RevWalk revWalk = new RevWalk(repository)) {
            revWalk.markStart(revWalk.parseCommit(repository.resolve(head)));
            for (RevCommit commit : revWalk) {
                if (commit.getFullMessage().contains(COMMIT_TRAILER)) {
                    boolean isHead = commit.name().equals(head);
                    boolean pending = pendingProcessing().get(pendingProcessingKey(exercise, commit.name())) != null;
                    return (pending && (isHead || !headProcessed)) || (isHead && !headProcessed) ? Optional.of(commit.name()) : Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The key of a commit's pending entry. Keying by exercise and commit keeps the entries of different commits apart: the entry of a commit whose push was
     * rejected, e.g. because another instructor pushed first, can never replace the entry of the commit that was pushed.
     */
    private static String pendingProcessingKey(ProgrammingExercise exercise, String commitHash) {
        return exercise.getId() + ":" + commitHash;
    }

    /**
     * The map is requested on each use rather than in the constructor, so creating the service never contacts the distributed data provider. Every provider
     * returns a cheap view of the named map, so there is nothing worth keeping in a field.
     */
    private DistributedMap<String, String> pendingProcessing() {
        return distributedDataProvider.getExpiringMap(PENDING_PROCESSING_MAP, PENDING_PROCESSING_TIME_TO_LIVE);
    }

    /**
     * The sandbox reads and rewrites files of the checkout, and both follow symbolic links. A link committed to the tests repository could
     * therefore make a change read or modify a file outside the checkout with the server's permissions, so a repository with links is not changed at all.
     * A checkout without symbolic links writes a committed link as a plain file that holds its target, and rewriting that file would change where the link
     * points, so the links are also read from the index.
     */
    private static void rejectSymbolicLinks(ProgrammingExercise exercise, Repository repository) throws IOException {
        Set<String> linkSet = new TreeSet<>(TestRepositoryCheckout.findSymbolicLinks(repository.getLocalPath()));
        DirCache index = repository.readDirCache();
        for (int i = 0; i < index.getEntryCount(); i++) {
            DirCacheEntry entry = index.getEntry(i);
            if (FileMode.SYMLINK.equals(entry.getRawMode())) {
                linkSet.add(entry.getPathString());
            }
        }
        List<String> links = List.copyOf(linkSet);
        if (!links.isEmpty()) {
            log.warn("Refusing to change the tests repository of exercise {}, because it contains symbolic links: {}", exercise.getId(), links);
            throw new BadRequestAlertException("The tests repository contains symbolic links, which the sandbox does not follow: " + String.join(", ", links), ENTITY_NAME,
                    "sandboxSymbolicLinks");
        }
    }

    /**
     * Rejects a change whose policy files would not be part of the commit. Staging skips a file that Git ignores, for example through a {@code .gitignore}
     * entry for {@code SecurityPolicy.yaml}: the checkout would then contain the policy and look configured, while the pushed tests reference a missing one.
     *
     * @throws BadRequestAlertException if a policy file the tests reference exists in the checkout but is not staged
     */
    private static void requireStagedPolicyFiles(Repository repository) throws IOException {
        Path root = repository.getLocalPath();
        DirCache index = repository.readDirCache();
        for (PolicyFile policyFile : TestRepositoryCheckout.evaluate(root).policyFiles()) {
            Optional<String> path = TestRepositoryFiles.validatePolicyPath(policyFile.policyPath());
            if (path.isPresent() && Files.exists(root.resolve(path.get()), LinkOption.NOFOLLOW_LINKS) && index.findEntry(path.get()) < 0) {
                throw new BadRequestAlertException("The policy file " + path.get() + " is ignored by Git, for example by an entry in .gitignore, so it would not be"
                        + " committed. Remove the entry and try again.", ENTITY_NAME, "sandboxPolicyFileIgnored");
            }
        }
    }
}
