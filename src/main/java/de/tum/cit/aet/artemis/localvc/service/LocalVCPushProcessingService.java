package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCInternalException;
import de.tum.cit.aet.artemis.programming.domain.Commit;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;
import de.tum.cit.aet.artemis.programming.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.programming.exception.VersionControlException;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.AuxiliaryRepositoryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTestCaseChangedService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionMessagingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionService;

/**
 * Turns a push to a local VC repository into what follows from it: a new exercise version, a submission and a queued build, and the guard that keeps a push to a staff
 * repository from running at the same time as another change to the exercise.
 */
@Lazy
@Service
@Profile(PROFILE_LOCALVC)
public class LocalVCPushProcessingService {

    private static final Logger log = LoggerFactory.getLogger(LocalVCPushProcessingService.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final AuxiliaryRepositoryService auxiliaryRepositoryService;

    private final ContinuousIntegrationTriggerService ciTriggerService;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final ProgrammingSubmissionMessagingService programmingSubmissionMessagingService;

    private final ProgrammingExerciseTestCaseChangedService programmingExerciseTestCaseChangedService;

    private final ExerciseVersionService exerciseVersionService;

    private final Optional<HyperionExerciseMutationApi> hyperionMutationApi;

    // TODO As soon as only LocalVC is supported, this Optional can be removed
    private final Optional<VcsAccessLogService> vcsAccessLogService;

    private final LocalVCRepositoryResolverService repositoryResolver;

    public LocalVCPushProcessingService(ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, AuxiliaryRepositoryService auxiliaryRepositoryService,
            ContinuousIntegrationTriggerService ciTriggerService, ProgrammingSubmissionService programmingSubmissionService,
            ProgrammingSubmissionMessagingService programmingSubmissionMessagingService, ProgrammingExerciseTestCaseChangedService programmingExerciseTestCaseChangedService,
            ExerciseVersionService exerciseVersionService, Optional<HyperionExerciseMutationApi> hyperionMutationApi, Optional<VcsAccessLogService> vcsAccessLogService,
            LocalVCRepositoryResolverService repositoryResolver) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.auxiliaryRepositoryService = auxiliaryRepositoryService;
        this.ciTriggerService = ciTriggerService;
        this.programmingSubmissionService = programmingSubmissionService;
        this.programmingSubmissionMessagingService = programmingSubmissionMessagingService;
        this.programmingExerciseTestCaseChangedService = programmingExerciseTestCaseChangedService;
        this.exerciseVersionService = exerciseVersionService;
        this.hyperionMutationApi = hyperionMutationApi;
        this.vcsAccessLogService = vcsAccessLogService;
        this.repositoryResolver = repositoryResolver;
    }

    /**
     * Claims the existing per-exercise mutation slot when a push targets a versionable staff repository. Student repositories receive a no-op lease.
     *
     * @param repository the repository targeted by receive-pack
     * @param exercise   the already-authorized exercise
     * @return a lease held by the transport until receive-pack and synchronous post-receive processing complete
     */
    public ProgrammingExerciseMutationGuardService.MutationLease claimProgrammingExerciseMutation(Repository repository, ProgrammingExercise exercise) {
        return claimProgrammingExerciseMutation(repository, exercise.getId());
    }

    /**
     * Protects a push using the already-authorized exercise id; HTTP authentication supplies a projection, not an entity.
     *
     * @param repository repository receiving the push
     * @param exerciseId exercise authorized for this request
     * @return mutation lease for staff repositories, otherwise a no-op lease
     */
    public ProgrammingExerciseMutationGuardService.MutationLease claimProgrammingExerciseMutation(Repository repository, long exerciseId) {
        LocalVCRepositoryUri repositoryUri = repositoryResolver.parseRepositoryUri(repository.getDirectory().toPath());
        RepositoryType repositoryType = getRepositoryType(repositoryUri.getRepositoryTypeOrUserName(), exerciseId);
        if (exerciseVersionService.isRepositoryTypeVersionable(repositoryType) && hyperionMutationApi.map(HyperionExerciseMutationApi::isCoordinationEnabled).orElse(false)
                && !programmingExerciseRepository.existsByIdAndExerciseGroupIsNotNull(exerciseId)) {
            HyperionExerciseMutationApi api = hyperionMutationApi.orElseThrow();
            String token = api.claimExternalMutationSlot(exerciseId);
            return new ProgrammingExerciseMutationGuardService.MutationLease(() -> api.clearExternalMutationSlot(exerciseId, token));
        }
        return new ProgrammingExerciseMutationGuardService.MutationLease(() -> {
        }, false);
    }

    /**
     * Create a submission, trigger the respective build, and process the results.
     * This method can be called with some values, to avoid loading them again from the database
     *
     * @param commitHash          the hash of the last commit.
     * @param repository          the remote repository which was pushed to.
     * @param user                the user who pushed the commit, used for logging and access control.
     * @param cachedExercise      the exercise which is potentially already loaded
     * @param cachedParticipation the participation which is potentially already loaded
     * @param vcsAccessLog        the vcsAccessLog which is potentially already loaded
     * @throws ContinuousIntegrationException if something goes wrong with the CI configuration.
     * @throws VersionControlException        if the commit belongs to the wrong branch (i.e. not the default branch of the participation).
     */
    public void processNewPush(String commitHash, Repository repository, User user, Optional<ProgrammingExercise> cachedExercise,
            Optional<ProgrammingExerciseParticipation> cachedParticipation, Optional<VcsAccessLog> vcsAccessLog) {
        // A git push knows the id it pushed, so it is also the commit that triggered this call
        processNewPush(commitHash, repository, user, cachedExercise, cachedParticipation, vcsAccessLog, commitHash);
    }

    /**
     * Process a new push, identifying the commit that triggered it.
     * <p>
     * The online editor commits through {@code RepositoryService} and reaches this method with no pushed hash, because the
     * hash the build is triggered for is resolved later. The id of the commit the request actually created still has to be
     * known here, so the resulting new commit alert can be attributed to the client that made that commit and to no other.
     *
     * @param commitHash           the hash of the last commit, may be null for a commit from the online editor
     * @param repository           the remote repository which was pushed to
     * @param user                 the user who pushed the commit
     * @param cachedExercise       the exercise which is potentially already loaded
     * @param cachedParticipation  the participation which is potentially already loaded
     * @param vcsAccessLog         the vcsAccessLog which is potentially already loaded
     * @param triggeringCommitHash the id of the commit this request created, or null when the caller does not know it
     */
    public void processNewPush(String commitHash, Repository repository, User user, Optional<ProgrammingExercise> cachedExercise,
            Optional<ProgrammingExerciseParticipation> cachedParticipation, Optional<VcsAccessLog> vcsAccessLog, @Nullable String triggeringCommitHash) {
        long timeNanoStart = System.nanoTime();

        Path repositoryFolderPath = repository.getDirectory().toPath();

        LocalVCRepositoryUri localVCRepositoryUri = getLocalVCRepositoryUri(repositoryFolderPath);

        String repositoryTypeOrUserName = localVCRepositoryUri.getRepositoryTypeOrUserName();
        String projectKey = localVCRepositoryUri.getProjectKey();
        ProgrammingExercise exercise = cachedExercise.orElseGet(() -> getProgrammingExercise(projectKey));
        ProgrammingExerciseParticipation participation;
        RepositoryType repositoryType = getRepositoryTypeWithoutAuxiliary(repositoryTypeOrUserName);

        try {
            participation = cachedParticipation.orElseGet(() -> programmingExerciseParticipationService
                    .fetchParticipationWithSubmissionsByRepository(localVCRepositoryUri.getRepositoryTypeOrUserName(), localVCRepositoryUri.toString(), exercise));
        }
        catch (EntityNotFoundException e) {
            repositoryType = getRepositoryType(repositoryTypeOrUserName, exercise);
            if (repositoryType.equals(RepositoryType.AUXILIARY) || repositoryType.equals(RepositoryType.TESTS)) {
                participation = retrieveSolutionParticipation(exercise);
            }
            else {
                throw new VersionControlException("Could not find participation for repository", e);
            }
        }

        try {
            if (exerciseVersionService.isRepositoryTypeVersionable(repositoryType)) {
                Long triggeringAuxiliaryRepositoryId = repositoryType == RepositoryType.AUXILIARY
                        ? auxiliaryRepositoryService.findAuxiliaryRepositoryIdOfExercise(repositoryTypeOrUserName, exercise).orElse(null)
                        : null;
                if (hyperionMutationApi.map(HyperionExerciseMutationApi::isCoordinationEnabled).orElse(false) && !exercise.isExamExercise()) {
                    exerciseVersionService.createExerciseVersionSynchronously(exercise, user, repositoryType, triggeringAuxiliaryRepositoryId, triggeringCommitHash);
                }
                else {
                    exerciseVersionService.createExerciseVersion(exercise, user, repositoryType, triggeringAuxiliaryRepositoryId, triggeringCommitHash);
                }
            }

            if (repositoryType.equals(RepositoryType.TESTS)) {
                processNewPushToTestOrAuxRepository(exercise, commitHash, (SolutionProgrammingExerciseParticipation) participation, repositoryType);
                return;
            }

            if (repositoryType.equals(RepositoryType.AUXILIARY)) {
                // Don't provide a commit hash because we want the latest test repo commit to be used
                processNewPushToTestOrAuxRepository(exercise, null, (SolutionProgrammingExerciseParticipation) participation, repositoryType);
                return;
            }

            if (commitHash == null) {
                commitHash = repositoryResolver.getLatestCommitHash(repository);
            }

            Commit commit = extractCommitInfo(commitHash, repository);

            // Process push to any repository other than the test repository. The repository type is passed on so the build
            // trigger can reuse the hash this push already carries instead of reading it back off the repository.
            processNewPushToRepository(participation, commit, user, repositoryType);

            // For push the correct commitHash is only available here, therefore the preliminary value is overwritten
            String finalCommitHash = commitHash;
            if (vcsAccessLog.isPresent()) {
                vcsAccessLog.get().setCommitHash(finalCommitHash);
                vcsAccessLogService.ifPresent(service -> service.saveVcsAccesslog(vcsAccessLog.get()));
            }
            else {
                var finalParticipation = participation;
                vcsAccessLogService.ifPresent(service -> service.updateCommitHash(finalParticipation, finalCommitHash));
            }
        }
        catch (GitAPIException | IOException e) {
            // This catch clause does not catch exceptions that happen during runBuildJob() as that method is called asynchronously.
            // For exceptions happening inside runBuildJob(), the user is notified. See the addBuildJobToQueue() method in the LocalCIBuildJobManagementService for that.
            throw new VersionControlException(
                    "Could not process new push to repository " + localVCRepositoryUri.getURI() + " and commit " + commitHash + ". No build job was queued.", e);
        }

        log.debug("New push processed to repository {} for commit {} in {}. A build job was queued.", localVCRepositoryUri.getURI(), commitHash,
                TimeLogUtil.formatDurationFrom(timeNanoStart));
    }

    private ProgrammingExerciseParticipation retrieveSolutionParticipation(ProgrammingExercise exercise) {
        return programmingExerciseParticipationService.retrieveSolutionParticipation(exercise);
    }

    private ProgrammingExercise getProgrammingExercise(String projectKey) {
        ProgrammingExercise exercise;
        try {
            exercise = programmingExerciseRepository.findOneByProjectKeyOrThrow(projectKey, false);
        }
        catch (EntityNotFoundException e) {
            throw new VersionControlException("Could not find programming exercise for project key " + projectKey, e);
        }
        return exercise;
    }

    private LocalVCRepositoryUri getLocalVCRepositoryUri(Path repositoryFolderPath) {
        try {
            return repositoryResolver.parseRepositoryUri(repositoryFolderPath);
        }
        catch (LocalVCInternalException e) {
            // This means something is misconfigured.
            throw new VersionControlException("Could not create valid repository URI from path " + repositoryFolderPath, e);
        }
    }

    /**
     * Process a new push to the test repository.
     * Build and test the solution repository to make sure all tests are still passing.
     *
     * @param exercise       the exercise for which the push was made.
     * @param commitHash     the hash of the commit used as the last commit to the test repository.
     * @param repositoryType type of repository that has been pushed to
     * @throws VersionControlException if something unexpected goes wrong when creating the submission or triggering the build.
     */
    private void processNewPushToTestOrAuxRepository(ProgrammingExercise exercise, String commitHash, SolutionProgrammingExerciseParticipation solutionParticipation,
            RepositoryType repositoryType) throws VersionControlException {
        // Create a new submission for the solution repository.
        ProgrammingSubmission submission = getProgrammingSubmission(exercise, commitHash);

        programmingSubmissionMessagingService.notifyUserAboutSubmission(submission, exercise.getId());

        if (repositoryType.equals(RepositoryType.TESTS)) {
            try {
                // Set a flag to inform the instructor that the student results are now outdated.
                programmingExerciseTestCaseChangedService.setTestCasesChanged(exercise.getId(), true);
            }
            catch (EntityNotFoundException e) {
                throw new VersionControlException("Could not set test cases changed flag", e);
            }
        }

        // Trigger the build for the solution repository.
        // The template repository will be built, once the result for the solution repository is available. See LocalCIResultProcessingService.
        ciTriggerService.triggerBuild(solutionParticipation, commitHash, repositoryType);
    }

    private ProgrammingSubmission getProgrammingSubmission(ProgrammingExercise exercise, String commitHash) {
        ProgrammingSubmission submission;
        try {
            submission = programmingSubmissionService.createSolutionParticipationSubmissionWithTypeTest(exercise.getId(), commitHash);
        }
        catch (EntityNotFoundException | IllegalStateException e) {
            throw new VersionControlException("Could not create submission for solution participation", e);
        }
        return submission;
    }

    private RepositoryType getRepositoryType(String repositoryTypeOrUserName, ProgrammingExercise exercise) {
        return getRepositoryType(repositoryTypeOrUserName, exercise.getId());
    }

    private RepositoryType getRepositoryType(String repositoryTypeOrUserName, long exerciseId) {
        RepositoryType type = getRepositoryTypeWithoutAuxiliary(repositoryTypeOrUserName);
        if (type == RepositoryType.USER && auxiliaryRepositoryService.isAuxiliaryRepositoryOfExercise(repositoryTypeOrUserName, exerciseId)) {
            return RepositoryType.AUXILIARY;
        }
        return type;
    }

    private RepositoryType getRepositoryTypeWithoutAuxiliary(String repositoryTypeOrUserName) {
        if (repositoryTypeOrUserName.equals(RepositoryType.TEMPLATE.toString())) {
            return RepositoryType.TEMPLATE;
        }
        else if (repositoryTypeOrUserName.equals(RepositoryType.SOLUTION.toString())) {
            return RepositoryType.SOLUTION;
        }
        else if (repositoryTypeOrUserName.equals(RepositoryType.TESTS.toString())) {
            return RepositoryType.TESTS;
        }
        return RepositoryType.USER;
    }

    /**
     * TODO: this could be done asynchronously to shorten the duration of the push operation
     * Process a new push to a student's repository or to the template or solution repository of the exercise.
     *
     * @param participation the participation for which the push was made
     * @param commit        the commit that was pushed
     * @param user          the user who pushed the commit, used for logging and access control
     * @throws VersionControlException if the commit belongs to the wrong branch (i.e. not the default branch of the participation)
     */
    private void processNewPushToRepository(ProgrammingExerciseParticipation participation, Commit commit, User user, RepositoryType pushedRepositoryType) {
        // The 'user' is not properly logged into Artemis, this leads to an issue when accessing custom repository methods.
        // Therefore, a mock auth object has to be created.
        SecurityUtils.setAuthorizationObject();
        ProgrammingSubmission submission;
        try {
            submission = programmingSubmissionService.processNewProgrammingSubmission(participation, commit, user, pushedRepositoryType);
        }
        catch (EntityNotFoundException | IllegalStateException | IllegalArgumentException e) {
            throw new VersionControlException("Could not process submission for participation: " + e.getMessage(), e);
        }

        // Remove unnecessary information from the new submission.
        submission.getParticipation().setSubmissions(null);
        programmingSubmissionMessagingService.notifyUserAboutSubmission(submission, participation.getExercise().getId());
    }

    private Commit extractCommitInfo(String commitHash, Repository repository) throws IOException, GitAPIException, VersionControlException {
        RevCommit revCommit;
        String branch = null;

        ObjectId objectId = repository.resolve(commitHash);

        if (objectId == null) {
            throw new VersionControlException("Could not resolve commit hash " + commitHash + " in repository");
        }

        revCommit = repository.parseCommit(objectId);

        // Get the branch name.
        try (Git git = new Git(repository)) {
            // Look in the 'refs/heads' namespace for a ref that points to the commit.
            // The returned map contains at most one entry where the key is the commit id and the value denotes the branch which points to it.
            Map<ObjectId, String> objectIdBranchNameMap = git.nameRev().addPrefix("refs/heads").add(objectId).call();
            if (!objectIdBranchNameMap.isEmpty()) {
                branch = objectIdBranchNameMap.get(objectId);
            }
        }

        if (revCommit == null || branch == null) {
            throw new VersionControlException("Something went wrong retrieving the revCommit or the branch.");
        }

        var author = revCommit.getAuthorIdent();
        return new Commit(commitHash, author.getName(), revCommit.getFullMessage(), author.getEmailAddress(), branch);
    }
}
