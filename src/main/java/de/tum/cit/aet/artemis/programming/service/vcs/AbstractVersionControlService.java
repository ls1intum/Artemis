package de.tum.cit.aet.artemis.programming.service.vcs;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.JGitText;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import de.tum.cit.aet.artemis.core.exception.VersionControlException;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.UriService;

public abstract class AbstractVersionControlService implements VersionControlService {

    private static final Logger log = LoggerFactory.getLogger(AbstractVersionControlService.class);

    @Value("${server.url}")
    protected String ARTEMIS_SERVER_URL;

    @Value("${artemis.version-control.default-branch:main}")
    protected String defaultBranch;

    protected final GitService gitService;

    protected final UriService uriService;

    protected final ProgrammingExerciseStudentParticipationRepository studentParticipationRepository;

    protected final ProgrammingExerciseRepository programmingExerciseRepository;

    protected final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    protected final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    public AbstractVersionControlService(GitService gitService, UriService uriService, ProgrammingExerciseStudentParticipationRepository studentParticipationRepository,
            ProgrammingExerciseRepository programmingExerciseRepository, TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        this.gitService = gitService;
        this.uriService = uriService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
    }

    /**
     * Adds a webhook for the specified repository to the given notification URL.
     *
     * @param repositoryUri   The repository to which the webhook should get added to
     * @param notificationUrl The URL the hook should notify
     * @param webHookName     Any arbitrary name for the webhook
     */
    protected abstract void addWebHook(VcsRepositoryUri repositoryUri, String notificationUrl, String webHookName);

    /**
     * Adds an authenticated webhook for the specified repository to the given notification URL.
     *
     * @param repositoryUri   The repository to which the webhook should get added to
     * @param notificationUrl The URL the hook should notify
     * @param webHookName     Any arbitrary name for the webhook
     * @param secretToken     A secret token that authenticates the webhook against the system behind the notification URL
     */
    protected abstract void addAuthenticatedWebHook(VcsRepositoryUri repositoryUri, String notificationUrl, String webHookName, String secretToken);

    @Override
    public void addWebHooksForExercise(ProgrammingExercise exercise) {
        final var artemisTemplateHookPath = ARTEMIS_SERVER_URL + "/api/public/programming-submissions/" + exercise.getTemplateParticipation().getId();
        final var artemisSolutionHookPath = ARTEMIS_SERVER_URL + "/api/public/programming-submissions/" + exercise.getSolutionParticipation().getId();
        final var artemisTestsHookPath = ARTEMIS_SERVER_URL + "/api/public/programming-exercises/test-cases-changed/" + exercise.getId();
        // first add web hooks from the version control service to Artemis, so that Artemis is notified and can create ProgrammingSubmission when instructors push their template or
        // solution code
        addWebHook(exercise.getVcsTemplateRepositoryUri(), artemisTemplateHookPath, "Artemis WebHook");
        addWebHook(exercise.getVcsSolutionRepositoryUri(), artemisSolutionHookPath, "Artemis WebHook");
        addWebHook(exercise.getVcsTestRepositoryUri(), artemisTestsHookPath, "Artemis WebHook");
    }

    @Override
    public void addWebHookForParticipation(ProgrammingExerciseParticipation participation) {
        if (!participation.getInitializationState().hasCompletedState(InitializationState.INITIALIZED)) {
            // first add a web hook from the version control service to Artemis, so that Artemis is notified can create a ProgrammingSubmission when students push their code
            addWebHook(participation.getVcsRepositoryUri(), ARTEMIS_SERVER_URL + "/api/public/programming-submissions/" + participation.getId(), "Artemis WebHook");
        }
    }

    @Override
    public VcsRepositoryUri copyRepository(String sourceProjectKey, String sourceRepositoryName, String sourceBranch, String targetProjectKey, String targetRepositoryName)
            throws VersionControlException {
        sourceRepositoryName = sourceRepositoryName.toLowerCase();
        targetRepositoryName = targetRepositoryName.toLowerCase();
        final String targetRepoSlug = targetProjectKey.toLowerCase() + "-" + targetRepositoryName;
        // get the remote url of the source repo
        final var sourceRepoUri = getCloneRepositoryUri(sourceProjectKey, sourceRepositoryName);
        // get the remote url of the target repo
        final var targetRepoUri = getCloneRepositoryUri(targetProjectKey, targetRepoSlug);
        Repository targetRepo = null;
        try {
            // create the new target repo
            createRepository(targetProjectKey, targetRepoSlug, null);
            // clone the source repo to the target directory
            targetRepo = gitService.getOrCheckoutRepositoryIntoTargetDirectory(sourceRepoUri, targetRepoUri, true);
            // copy by pushing the source's content to the target's repo
            gitService.pushSourceToTargetRepo(targetRepo, targetRepoUri, sourceBranch);
        }
        catch (GitAPIException | VersionControlException ex) {
            if (isReadFullyShortReadOfBlockException(ex)) {
                // NOTE: we ignore this particular error: it sometimes happens when pushing code that includes binary files, however the push operation typically worked correctly
                // TODO: verify that the push operation actually worked correctly, e.g. by comparing the number of commits in the source and target repo
                log.warn("TransportException/EOFException with 'Short read of block' when copying repository {} to {}. Will ignore it", sourceRepoUri, targetRepoUri);
                return targetRepoUri;
            }
            Path localPath = gitService.getDefaultLocalPathOfRepo(targetRepoUri);
            // clean up in case of an error
            try {
                if (targetRepo != null) {
                    // delete the target repo if an error occurs
                    gitService.deleteLocalRepository(targetRepo);
                }
                else {
                    // or delete the folder if it exists
                    FileUtils.deleteDirectory(localPath.toFile());
                }
            }
            catch (IOException ioException) {
                // ignore
                log.error("Could not delete directory of the failed cloned repository in: {}", localPath);
            }
            throw new VersionControlException("Could not copy repository " + sourceRepositoryName + " to the target repository " + targetRepositoryName, ex);
        }

        return targetRepoUri;
    }

    /**
     * checks for a specific exception that we would like to ignore
     *
     * @param ex the exception
     * @return whether we found the specific one or not
     */
    public static boolean isReadFullyShortReadOfBlockException(Throwable ex) {
        return ex instanceof org.eclipse.jgit.api.errors.TransportException transportException
                && transportException.getCause() instanceof org.eclipse.jgit.errors.TransportException innerTransportException
                && innerTransportException.getCause() instanceof java.io.EOFException eofException && eofException.getMessage().equals(JGitText.get().shortReadOfBlock)
                && Objects.equals(eofException.getStackTrace()[0].getClassName(), "org.eclipse.jgit.util.IO")
                && Objects.equals(eofException.getStackTrace()[0].getMethodName(), "readFully");
    }

    @Override
    public String getOrRetrieveBranchOfParticipation(ProgrammingExerciseParticipation participation) {
        if (participation instanceof ProgrammingExerciseStudentParticipation studentParticipation) {
            return getOrRetrieveBranchOfStudentParticipation(studentParticipation);
        }
        else {
            return getOrRetrieveBranchOfExercise(participation.getProgrammingExercise());
        }
    }

    @Override
    public String getOrRetrieveBranchOfStudentParticipation(ProgrammingExerciseStudentParticipation participation) {
        if (participation.getBranch() == null) {
            String branch = getDefaultBranchOfRepository(participation.getVcsRepositoryUri());
            participation.setBranch(branch);
            studentParticipationRepository.save(participation);
        }

        return participation.getBranch();
    }

    @Override
    public String getOrRetrieveBranchOfExercise(ProgrammingExercise programmingExercise) {
        programmingExerciseBuildConfigRepository.loadAndSetBuildConfig(programmingExercise);

        if (programmingExercise.getBuildConfig().getBranch() == null) {
            if (!Hibernate.isInitialized(programmingExercise.getTemplateParticipation())) {
                programmingExercise.setTemplateParticipation(templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseIdElseThrow(programmingExercise.getId()));
            }
            String branch = getDefaultBranchOfRepository(programmingExercise.getVcsTemplateRepositoryUri());
            programmingExercise.getBuildConfig().setBranch(branch);
            programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());
        }

        return programmingExercise.getBuildConfig().getBranch();
    }

    @Override
    public String getDefaultBranchOfArtemis() {
        return defaultBranch;
    }

    /**
     * Determines if a user should get read-only or also write permissions for their repository.
     *
     * @param programmingExercise The programming exercise the repository belongs to.
     * @return The permissions the user should receive for a repository.
     */
    protected VersionControlRepositoryPermission determineRepositoryPermissions(final ProgrammingExercise programmingExercise) {
        // NOTE: null values are interpreted as offline IDE is allowed
        if (Boolean.FALSE.equals(programmingExercise.isAllowOfflineIde())) {
            return VersionControlRepositoryPermission.REPO_READ;
        }
        else {
            return VersionControlRepositoryPermission.REPO_WRITE;
        }
    }
}
