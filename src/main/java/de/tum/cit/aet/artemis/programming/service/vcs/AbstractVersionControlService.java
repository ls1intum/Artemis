package de.tum.cit.aet.artemis.programming.service.vcs;

import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.core.exception.VersionControlException;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.UriService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

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
    public LocalVCRepositoryUri copyRepositoryWithoutHistory(String sourceProjectKey, String sourceRepositoryName, String sourceBranch, String targetProjectKey,
            String targetRepositoryName, Integer attempt) throws VersionControlException {
        return copyRepository(sourceProjectKey, sourceRepositoryName, sourceBranch, targetProjectKey, targetRepositoryName, attempt, false);
    }

    @Override
    public LocalVCRepositoryUri copyRepositoryWithHistory(String sourceProjectKey, String sourceRepositoryName, String sourceBranch, String targetProjectKey,
            String targetRepositoryName, Integer attempt) throws VersionControlException {
        return copyRepository(sourceProjectKey, sourceRepositoryName, sourceBranch, targetProjectKey, targetRepositoryName, attempt, true);
    }

    private LocalVCRepositoryUri copyRepository(String sourceProjectKey, String sourceRepositoryName, String sourceBranch, String targetProjectKey, String targetRepositoryName,
            Integer attempt, boolean withHistory) throws VersionControlException {
        sourceRepositoryName = sourceRepositoryName.toLowerCase();
        targetRepositoryName = targetRepositoryName.toLowerCase();
        String targetProjectKeyLowerCase = targetProjectKey.toLowerCase();
        if (attempt != null && attempt > 0 && !targetRepositoryName.contains("practice-")) {
            targetProjectKeyLowerCase = targetProjectKeyLowerCase + attempt;
        }
        final String targetRepoSlug = targetProjectKeyLowerCase + "-" + targetRepositoryName;
        final var sourceRepoUri = getCloneRepositoryUri(sourceProjectKey, sourceRepositoryName);
        final var targetRepoUri = getCloneRepositoryUri(targetProjectKey, targetRepoSlug);
        try (Repository targetRepo = withHistory ? gitService.copyBareRepositoryWithHistory(sourceRepoUri, targetRepoUri, sourceBranch)
                : gitService.copyBareRepositoryWithoutHistory(sourceRepoUri, targetRepoUri, sourceBranch)) {
            return targetRepo.getRemoteRepositoryUri(); // should be the same as targetRepoUri
        }
        catch (IOException | LargeObjectException ex) {
            Path localPath = gitService.getDefaultLocalCheckOutPathOfRepo(targetRepoUri);
            // clean up in case of an error
            try {
                // or delete the folder if it exists
                FileUtils.deleteDirectory(localPath.toFile());
            }
            catch (IOException ioException) {
                // ignore
                log.error("Could not delete directory of the failed cloned repository in: {}", localPath);
            }
            if (ex instanceof LargeObjectException) {
                throw new VersionControlException(
                        "Could not copy repository " + sourceRepositoryName + " to the target repository " + targetRepositoryName + " because a file in the repo is too large.",
                        ex);
            }
            throw new VersionControlException("Could not copy repository " + sourceRepositoryName + " to the target repository " + targetRepositoryName, ex);
        }
    }

    /**
     * checks for a specific exception that we would like to ignore
     *
     * @param ex the exception
     * @return whether we found the specific one or not
     */
    public static boolean isReadFullyShortReadOfBlockException(Throwable ex) {
        return ex instanceof TransportException transportException && transportException.getCause() instanceof org.eclipse.jgit.errors.TransportException innerTransportException
                && innerTransportException.getCause() instanceof EOFException eofException && eofException.getMessage().equals(JGitText.get().shortReadOfBlock)
                && Objects.equals(eofException.getStackTrace()[0].getClassName(), "org.eclipse.jgit.util.IO")
                && Objects.equals(eofException.getStackTrace()[0].getMethodName(), "readFully");
    }
}
