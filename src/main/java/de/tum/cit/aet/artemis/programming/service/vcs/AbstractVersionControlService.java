package de.tum.cit.aet.artemis.programming.service.vcs;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.JGitText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.core.exception.VersionControlException;
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

    @Override
    public VcsRepositoryUri copyRepository(String sourceProjectKey, String sourceRepositoryName, String sourceBranch, String targetProjectKey, String targetRepositoryName,
            Integer attempt) throws VersionControlException {
        sourceRepositoryName = sourceRepositoryName.toLowerCase();
        targetRepositoryName = targetRepositoryName.toLowerCase();
        String targetProjectKeyLowerCase = targetProjectKey.toLowerCase();
        if (attempt != null && attempt > 0 && !targetRepositoryName.contains("practice-")) {
            targetProjectKeyLowerCase = targetProjectKeyLowerCase + attempt;
        }
        final String targetRepoSlug = targetProjectKeyLowerCase + "-" + targetRepositoryName;
        // get the remote url of the source repo
        final var sourceRepoUri = getCloneRepositoryUri(sourceProjectKey, sourceRepositoryName);
        // get the remote url of the target repo
        final var targetRepoUri = getCloneRepositoryUri(targetProjectKey, targetRepoSlug);
        Repository targetRepo = null;
        try {
            // create the new target repo
            createRepository(targetProjectKey, targetRepoSlug);
            // clone the source repo to the target directory
            targetRepo = gitService.getOrCheckoutRepositoryIntoTargetDirectory(sourceRepoUri, targetRepoUri, true);
            // copy by pushing the source's content to the target's repo
            gitService.pushSourceToTargetRepo(targetRepo, targetRepoUri, sourceBranch);
        }
        catch (GitAPIException | VersionControlException ex) {
            if (isReadFullyShortReadOfBlockException(ex)) {
                // NOTE: we ignore this particular error: it sometimes happens when pushing code that includes binary files, however the push operation typically worked correctly
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
}
