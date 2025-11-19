package de.tum.cit.aet.artemis.programming.service.localvc;

import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;

import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.jspecify.annotations.NonNull;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.LocalCIException;
import de.tum.cit.aet.artemis.core.exception.VersionControlException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;
import de.tum.cit.aet.artemis.programming.service.localvc.ssh.SshConstants;

/**
 * Contains an onPostReceive method that is called by JGit after a push has been received (i.e. after the pushed files were successfully written to disk).
 */
public class LocalVCPostPushHook implements PostReceiveHook {

    private final LocalVCServletService localVCServletService;

    private final ProgrammingExerciseParticipation participation;

    private final ProgrammingExercise exercise;

    private final VcsAccessLog vcsAccessLog;

    private final User user;

    public LocalVCPostPushHook(LocalVCServletService localVCServletService, ServerSession serverSession, @NonNull User user) {
        this.localVCServletService = localVCServletService;
        this.participation = serverSession.getAttribute(SshConstants.PARTICIPATION_KEY);
        this.exercise = serverSession.getAttribute(SshConstants.REPOSITORY_EXERCISE_KEY);
        this.vcsAccessLog = serverSession.getAttribute(SshConstants.VCS_ACCESS_LOG_KEY);
        this.user = user;
    }

    public LocalVCPostPushHook(LocalVCServletService localVCServletService, @NonNull User user) {
        this.localVCServletService = localVCServletService;
        this.user = user;
        // For HTTPs we are unable to store the attributes in the session or request unfortunately
        this.participation = null;
        this.exercise = null;
        this.vcsAccessLog = null;
    }

    /**
     * Called by JGit after a push has been received (i.e. after the pushed files were successfully written to disk).
     *
     * @param receivePack the process handling the current receive. Hooks may obtain details about the destination repository through this handle.
     * @param commands    unmodifiable set of successfully completed commands.
     */
    @Override
    public void onPostReceive(ReceivePack receivePack, Collection<ReceiveCommand> commands) {

        // The PreReceiveHook already checked that there is exactly one command and rejects the push otherwise.
        Iterator<ReceiveCommand> iterator = commands.iterator();
        if (!iterator.hasNext()) {
            // E.g. no refs were updated during the push operation.
            // Note: This was already checked in the LocalVCPrePushHook but the request is then just delegated further and is finally stopped here.
            return;
        }

        ReceiveCommand command = iterator.next();

        String wrongBranchMessage = "Only pushes to the default branch will be graded. Your changes were saved nonetheless.";

        if (command.getType() != ReceiveCommand.Type.UPDATE && command.getType() != ReceiveCommand.Type.UPDATE_NONFASTFORWARD) {
            // The command can also be of type CREATE (e.g. when creating a new branch). This will never lead to a new submission.
            // Pushes for submissions must come from the default branch, which can only be updated and not created by the student.
            // Updates to other branches will be caught in the catch block below, returning an error message to the user.
            receivePack.sendError(wrongBranchMessage);
            return;
        }

        // If there are multiple commits in the push, this will always retrieve the commit hash of the latest commit.
        String commitHash = command.getNewId().name();

        Repository repository = receivePack.getRepository();

        try {
            localVCServletService.processNewPush(commitHash, repository, user, Optional.ofNullable(exercise), Optional.ofNullable(participation),
                    Optional.ofNullable(vcsAccessLog));
        }
        catch (LocalCIException e) {
            // Return an error message to the user.
            receivePack.sendError(
                    "Something went wrong while processing your push. Your changes were saved, but we could not test your submission. Please try again and if this issue persists, contact the course administrators.");
        }
        catch (VersionControlException e) {
            boolean isBranchingAllowed = localVCServletService.isBranchingAllowedForRepository(repository);
            if (isBranchingAllowed) {
                String message = "Your push will not be shown in Artemis, because you are currently pushing changes on a custom (non-default) branch. Your changes are still saved correctly.";
                receivePack.sendMessage(message);
            }
            else {
                receivePack.sendError(wrongBranchMessage);
            }
        }
    }
}
