package de.tum.cit.aet.artemis.localvc.service.vcs;

import java.io.EOFException;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.localvc.service.BareGitRepositoryService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.exception.GitException;
import de.tum.cit.aet.artemis.programming.exception.VersionControlException;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.UriService;

public abstract class AbstractVersionControlService implements VersionControlService {

    private static final Logger log = LoggerFactory.getLogger(AbstractVersionControlService.class);

    protected final BareGitRepositoryService bareGitRepositoryService;

    protected final UriService uriService;

    protected final ProgrammingExerciseStudentParticipationRepository studentParticipationRepository;

    protected final ProgrammingExerciseRepository programmingExerciseRepository;

    protected final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    protected final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    public AbstractVersionControlService(BareGitRepositoryService bareGitRepositoryService, UriService uriService,
            ProgrammingExerciseStudentParticipationRepository studentParticipationRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        this.bareGitRepositoryService = bareGitRepositoryService;
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
        sourceRepositoryName = sourceRepositoryName.toLowerCase(Locale.ROOT);
        targetRepositoryName = targetRepositoryName.toLowerCase(Locale.ROOT);
        String targetProjectKeyLowerCase = targetProjectKey.toLowerCase(Locale.ROOT);
        if (attempt != null && attempt > 0 && !targetRepositoryName.contains("practice-")) {
            targetProjectKeyLowerCase = targetProjectKeyLowerCase + attempt;
        }
        final String targetRepoSlug = targetProjectKeyLowerCase + "-" + targetRepositoryName;
        final var sourceRepoUri = getCloneRepositoryUri(sourceProjectKey, sourceRepositoryName);
        final var targetRepoUri = getCloneRepositoryUri(targetProjectKey, targetRepoSlug);
        if (repositoryExists(targetRepoUri)) {
            boolean targetRepositoryHealthy;
            try {
                targetRepositoryHealthy = bareGitRepositoryService.isBareRepositoryHealthy(targetRepoUri);
            }
            catch (GitException ex) {
                // The health of the pre-existing repository could not be determined (e.g. a transient I/O error):
                // abort the copy instead of risking the deletion of a healthy repository.
                throw new VersionControlException("Could not check the health of the target repository " + targetRepoSlug + " before copying", ex);
            }
            if (targetRepositoryHealthy) {
                // The repository has already been copied, either by an earlier call that got this far or by a request that is
                // starting the same participation concurrently. Both would produce the same content, and overwriting it would
                // throw away whatever has been pushed to it since, so the repository that is there is the answer.
                log.debug("Target repository {} already exists and is usable, keeping it instead of copying again", targetRepoUri);
                return targetRepoUri;
            }
            // Self-healing: a previous failed copy or a partially failed deletion left a broken target repository behind
            // (unborn or corrupt, without any branch, so no student data can be lost). Copying onto it would fail forever,
            // so it has to go. A copy that is still running is never mistaken for such a leftover: it is built next to the
            // target path and published with a single atomic rename, so the target path only ever holds a finished
            // repository.
            //
            // It is moved aside rather than deleted where it lies, because the health check above and the repair are not
            // one step: two requests can both find the same leftover broken. Only one of them can win the rename, so
            // neither can delete what the other has since put there. The loser copies like any other request and settles
            // it at publication time, which keeps whichever repository was published first.
            log.warn("Target repository {} exists but is unborn or corrupt; moving it aside so the copy can recreate it", targetRepoUri);
            if (!quarantineBrokenRepository(targetRepoUri)) {
                log.debug("Target repository {} was already repaired by a concurrent request", targetRepoUri);
            }
        }
        // A failed copy needs no cleanup here: the repository is built next to the target path and only moved there once it is complete, so a copy that failed leaves the
        // target path as it found it. Deleting it would be actively harmful, since it may hold the repository that a concurrent copy of the same participation published.
        try (Repository targetRepo = withHistory ? bareGitRepositoryService.copyBareRepositoryWithHistory(sourceRepoUri, targetRepoUri, sourceBranch)
                : bareGitRepositoryService.copyBareRepositoryWithoutHistory(sourceRepoUri, targetRepoUri, sourceBranch)) {
            return targetRepo.getRemoteRepositoryUri(); // should be the same as targetRepoUri
        }
        catch (IOException | RuntimeException ex) {
            if (ex instanceof LargeObjectException) {
                throw new VersionControlException(
                        "Could not copy repository " + sourceRepositoryName + " to the target repository " + targetRepositoryName + " because a file in the repo is too large.",
                        ex);
            }
            throw new VersionControlException("Could not copy repository " + sourceRepositoryName + " to the target repository " + targetRepositoryName, ex);
        }
    }

    /**
     * Checks if a repository already exists before a copy operation starts. In contrast to {@link #isValidGitRepository}, this is a pure existence check:
     * a corrupt pre-existing repository must still count as existing here, so that the copy inspects its health and repairs it rather than copying onto it.
     *
     * @param repositoryUri the repository URI to check
     * @return true if the repository exists, false otherwise
     */
    protected abstract boolean repositoryExists(LocalVCRepositoryUri repositoryUri);

    /**
     * Moves a broken (unborn or corrupt) repository out of the way in one atomic step and discards it, so that a copy can recreate it at its path.
     * <p>
     * Claiming the repository and removing it are the same step on purpose. Two requests can both find the same leftover broken, and deleting it where it lies would let the
     * slower one delete whatever the faster one has published at that path since. A rename has exactly one winner, and it takes the broken repository with it, so the loser
     * finds the path free or taken by a finished repository and never removes either.
     *
     * @param repositoryUri the repository to move aside
     * @return true if this call moved the broken repository aside, false if it was already gone, i.e. a concurrent request repaired the path first
     * @throws VersionControlException if the repository is there but could not be moved aside, since the copy could not have succeeded either way
     */
    protected abstract boolean quarantineBrokenRepository(LocalVCRepositoryUri repositoryUri);

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
