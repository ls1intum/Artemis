package de.tum.cit.aet.artemis.localvc.service;

import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.jspecify.annotations.NonNull;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.localvc.service.ssh.SshConstants;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.exception.BuildOutsideTimeWindowException;
import de.tum.cit.aet.artemis.programming.exception.VersionControlException;

/**
 * Contains an onPostReceive method that is called by JGit after a push has been received (i.e. after the pushed files were successfully written to disk).
 */
public class LocalVCPostPushHook implements PostReceiveHook {

    private final LocalVCServletService localVCServletService;

    private final LocalVCPushProcessingService pushProcessingService;

    private final PasswordAuthenticationNoticeService passwordAuthenticationNoticeService;

    private final ProgrammingExerciseParticipation participation;

    private final ProgrammingExercise exercise;

    private final User user;

    private final Supplier<AuthenticationMechanism> authenticationMechanism;

    private final Consumer<String> pushedCommitRecorder;

    /**
     * Creates the hook of a push over SSH, which reads what the command cached in the session when it was authorized.
     *
     * @param audit collects the commit of the push for the access log entry of the command
     */
    public LocalVCPostPushHook(LocalVCServletService localVCServletService, LocalVCPushProcessingService pushProcessingService,
            PasswordAuthenticationNoticeService passwordAuthenticationNoticeService, ServerSession serverSession, @NonNull User user, GitOperationAudit audit) {
        this.localVCServletService = localVCServletService;
        this.pushProcessingService = pushProcessingService;
        this.passwordAuthenticationNoticeService = passwordAuthenticationNoticeService;
        this.participation = serverSession.getAttribute(SshConstants.PARTICIPATION_KEY);
        this.exercise = serverSession.getAttribute(SshConstants.REPOSITORY_EXERCISE_KEY);
        this.user = user;
        this.authenticationMechanism = () -> AuthenticationMechanism.SSH;
        this.pushedCommitRecorder = audit::onPushAccepted;
    }

    /**
     * Creates the hook of a push over HTTPS.
     *
     * @param authenticationMechanism reads how the push authenticated. JGit creates the hook before the push filter authorizes the request, so the value is only read when the hook
     *                                    runs.
     * @param pushedCommitRecorder    passes the commit hash of the accepted push to the audit of the request
     */
    public LocalVCPostPushHook(LocalVCServletService localVCServletService, LocalVCPushProcessingService pushProcessingService,
            PasswordAuthenticationNoticeService passwordAuthenticationNoticeService, @NonNull User user, Supplier<AuthenticationMechanism> authenticationMechanism,
            Consumer<String> pushedCommitRecorder) {
        this.localVCServletService = localVCServletService;
        this.pushProcessingService = pushProcessingService;
        this.passwordAuthenticationNoticeService = passwordAuthenticationNoticeService;
        this.user = user;
        this.authenticationMechanism = authenticationMechanism;
        this.pushedCommitRecorder = pushedCommitRecorder;
        // JGit constructs this hook before HTTPS authorization. Resolve the participation and exercise when processing the accepted push.
        this.participation = null;
        this.exercise = null;
    }

    /**
     * Called by JGit after a push has been received (i.e. after the pushed files were successfully written to disk).
     *
     * @param receivePack the process handling the current receive. Hooks may obtain details about the destination repository through this handle.
     * @param commands    unmodifiable set of successfully completed commands.
     */
    @Override
    public void onPostReceive(ReceivePack receivePack, Collection<ReceiveCommand> commands) {

        // Said before anything else, so that a push that updates nothing or is rejected below still tells the user.
        if (authenticationMechanism.get() == AuthenticationMechanism.PASSWORD) {
            receivePack.sendMessage(passwordAuthenticationNoticeService.getPasswordAuthenticationPushNotice());
        }

        // The PreReceiveHook already checked that there is exactly one command and rejects the push otherwise.
        Iterator<ReceiveCommand> iterator = commands.iterator();
        if (!iterator.hasNext()) {
            // E.g. no refs were updated during the push operation.
            // Note: This was already checked in the LocalVCPrePushHook but the request is then just delegated further and is finally stopped here.
            return;
        }

        ReceiveCommand command = iterator.next();

        // If there are multiple commits in the push, this will always retrieve the commit hash of the latest commit.
        String commitHash = command.getNewId().name();

        // The push is part of the repository now, so it is part of the access log, whatever becomes of it below: a push to another branch is no submission, and the processing of
        // a push can fail after the push itself went through.
        pushedCommitRecorder.accept(commitHash);

        String wrongBranchMessage = "Only pushes to the default branch will be graded. Your changes were saved nonetheless.";

        if (command.getType() != ReceiveCommand.Type.UPDATE && command.getType() != ReceiveCommand.Type.UPDATE_NONFASTFORWARD) {
            // The command can also be of type CREATE (e.g. when creating a new branch). This will never lead to a new submission.
            // Pushes for submissions must come from the default branch, which can only be updated and not created by the student.
            // Updates to other branches will be caught in the catch block below, returning an error message to the user.
            receivePack.sendError(wrongBranchMessage);
            return;
        }

        Repository repository = receivePack.getRepository();

        try {
            pushProcessingService.processNewPush(commitHash, repository, user, Optional.ofNullable(exercise), Optional.ofNullable(participation));
        }
        catch (LocalCIException e) {
            // Return an error message to the user.
            receivePack.sendError(
                    "Something went wrong while processing your push. Your changes were saved, but we could not test your submission. Please try again and if this issue persists, contact the course administrators.");
        }
        catch (BuildOutsideTimeWindowException e) {
            // The push is valid and kept, it is just not built, so this is a message and no error.
            receivePack.sendMessage(e.getUserMessage());
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
