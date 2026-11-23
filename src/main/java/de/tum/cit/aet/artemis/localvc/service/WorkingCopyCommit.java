package de.tum.cit.aet.artemis.localvc.service;

import org.eclipse.jgit.api.errors.GitAPIException;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.programming.domain.Repository;

/**
 * Creates the commit of the online editor. The changes are staged, checked and committed while the lock of the working copy is held. Without the lock, a second request for the
 * same working copy could stage its changes between the check and the commit, and the commit would contain content that was not checked.
 */
public final class WorkingCopyCommit {

    private WorkingCopyCommit() {
        // utility class
    }

    /**
     * Stages all changes of a working copy, checks the staged content against the limits for the files of a repository, commits it and pushes the commit.
     *
     * @param gitService               the service that stages, commits and pushes
     * @param bareGitRepositoryService the service that opens the accepted destination and supplies its quota
     * @param repository               the working copy
     * @param message                  the commit message
     * @param user                     the user who has committed the changes
     * @return the id of the commit that was created
     * @throws GitAPIException if staging, committing or pushing fails
     */
    public static String commitChanges(GitService gitService, BareGitRepositoryService bareGitRepositoryService, Repository repository, String message, User user)
            throws GitAPIException {
        return WorkingCopyLocks.shared().withLock(AbstractGitService.workingCopyPath(repository), () -> {
            gitService.stageAllChanges(repository);
            try (Repository destination = bareGitRepositoryService.getBareRepository(repository.getRemoteRepositoryUri(), false)) {
                StagedContentValidator.validate(repository, destination, bareGitRepositoryService.getMaxRepositorySizeBytes(repository));
            }
            return gitService.commitAndPush(repository, message, true, user);
        });
    }
}
