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
     * @param gitService             the service that stages, commits and pushes
     * @param repository             the working copy
     * @param maxRepositorySizeBytes the size that the files of the repository may add up to
     * @param message                the commit message
     * @param user                   the user who has committed the changes
     * @return the id of the commit that was created
     * @throws GitAPIException if staging, committing or pushing fails
     */
    public static String commitChanges(GitService gitService, Repository repository, long maxRepositorySizeBytes, String message, User user) throws GitAPIException {
        return WorkingCopyLocks.shared().withLock(repository.getLocalPath(), () -> {
            gitService.stageAllChanges(repository);
            StagedContentValidator.validate(repository, maxRepositorySizeBytes);
            return gitService.commitAndPush(repository, message, true, user);
        });
    }
}
