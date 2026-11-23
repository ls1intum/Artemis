package de.tum.cit.aet.artemis.localvc.service.ssh;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;

import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.common.util.MapEntryUtils;
import org.apache.sshd.common.util.threads.CloseableExecutorService;
import org.apache.sshd.git.GitLocationResolver;
import org.apache.sshd.git.pack.GitPackCommand;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.channel.ChannelSession;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.transport.GitProtocolConstants;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.util.FS;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.HttpStatusException;
import de.tum.cit.aet.artemis.localvc.service.AccessLogSubject;
import de.tum.cit.aet.artemis.localvc.service.GitOperationAudit;
import de.tum.cit.aet.artemis.localvc.service.LocalVCAccessLogRecorderService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCFetchPreUploadHook;
import de.tum.cit.aet.artemis.localvc.service.LocalVCPostPushHook;
import de.tum.cit.aet.artemis.localvc.service.LocalVCPrePushHook;
import de.tum.cit.aet.artemis.localvc.service.LocalVCPushProcessingService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCReceivePackLimits;
import de.tum.cit.aet.artemis.localvc.service.LocalVCServletService;
import de.tum.cit.aet.artemis.localvc.service.PasswordAuthenticationNoticeService;
import de.tum.cit.aet.artemis.localvc.service.QuarantinedReceivePack;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Custom Git command handler that integrates Artemis-specific
 * hooks into SSH-based Git operations for UploadPack and ReceivePack.
 */
public class SshGitCommand extends GitPackCommand {

    private final LocalVCServletService localVCServletService;

    private final LocalVCAccessLogRecorderService accessLogRecorder;

    private final LocalVCPushProcessingService pushProcessingService;

    private final PasswordAuthenticationNoticeService passwordAuthenticationNoticeService;

    /**
     * Constructs a new {@link SshGitCommand}.
     *
     * @param rootDirResolver                     Resolver for GIT root directory
     * @param command                             Git command string to execute
     * @param executorService                     Optional executor service for command execution
     * @param localVCServletService               LocalVC service providing the push hooks with their checks
     * @param accessLogRecorder                   writes the access log of fetch operations
     * @param pushProcessingService               handles what a push leads to
     * @param passwordAuthenticationNoticeService tells users who push with a password to switch, which an ssh push never is
     */
    public SshGitCommand(GitLocationResolver rootDirResolver, String command, CloseableExecutorService executorService, LocalVCServletService localVCServletService,
            LocalVCAccessLogRecorderService accessLogRecorder, LocalVCPushProcessingService pushProcessingService,
            PasswordAuthenticationNoticeService passwordAuthenticationNoticeService) {
        super(rootDirResolver, command, executorService);
        this.localVCServletService = localVCServletService;
        this.accessLogRecorder = accessLogRecorder;
        this.pushProcessingService = pushProcessingService;
        this.passwordAuthenticationNoticeService = passwordAuthenticationNoticeService;
    }

    /**
     * Resolves the root directory of the Git repository.
     * This bypasses default API behavior to directly obtain the repo path.
     */
    @Override
    protected Path resolveRootDirectory(String command, String[] args) throws IOException {
        return getGitLocationResolver().resolveRootDirectory(command, args, getServerSession(), getFileSystem());
    }

    /**
     * Starts the command on the shared pool. A command that finds the pool and its queue full is refused with a message to the client. The exception is not passed on, because
     * the server would close the whole session for it and with it the other commands of the client.
     *
     * @param channel the channel of the command
     * @param env     the environment of the command
     * @throws IOException if the command cannot be started for another reason
     */
    @Override
    public void start(ChannelSession channel, Environment env) throws IOException {
        try {
            super.start(channel, env);
        }
        catch (RejectedExecutionException | IOException e) {
            if (!(e instanceof RejectedExecutionException) && !(e.getCause() instanceof RejectedExecutionException)) {
                throw (IOException) e;
            }
            String message = "The server is busy. Please try again later.";
            try {
                if (getErrorStream() != null) {
                    getErrorStream().write((message + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
                }
            }
            catch (IOException ignored) {
                // The client may already have disconnected
            }
            onExit(-1, message);
        }
    }

    /**
     * Executes the Git command by dispatching either upload-pack or receive-pack logic,
     * and integrates Artemis-specific hooks for fetch and push operations.
     */
    @Override
    public void run() {
        String command = getCommand();

        try {
            // Split command string into argument list, handling space and quotes
            List<String> argsList = parseDelimitedString(command, " ", true);

            // Sanitize arguments by removing surrounding quotes
            argsList.replaceAll(arg -> {
                if ((arg.startsWith("'") && arg.endsWith("'")) || (arg.startsWith("\"") && arg.endsWith("\""))) {
                    return arg.substring(1, arg.length() - 1);
                }
                return arg;
            });

            // Convert to array for downstream compatibility
            String[] args = argsList.toArray(String[]::new);

            // Basic validity check
            if (args.length != 2) {
                throw new IllegalArgumentException("Invalid git command line (no arguments): " + command);
            }

            // Locate the Git repository root
            Path rootDir;
            User user;
            ProgrammingExercise exercise;
            AccessLogSubject accessLogSubject;
            // Written to by the hooks of this command, and turned into its access log entry when the command is over
            GitOperationAudit audit = new GitOperationAudit();
            LocalVCFetchPreUploadHook fetchHook = null;
            LocalVCPostPushHook pushHook = null;
            // Authorization writes command state to the shared session. Capture it atomically with resolution, before opening the repository or waiting for a mutation lease.
            synchronized (getServerSession()) {
                rootDir = resolveRootDirectory(command, args);
                user = getServerSession().getAttribute(SshConstants.USER_KEY);
                exercise = getServerSession().getAttribute(SshConstants.REPOSITORY_EXERCISE_KEY);
                accessLogSubject = getServerSession().getAttribute(SshConstants.ACCESS_LOG_SUBJECT_KEY);
                if (RemoteConfig.DEFAULT_UPLOAD_PACK.equals(args[0])) {
                    fetchHook = new LocalVCFetchPreUploadHook(audit);
                }
                else if (RemoteConfig.DEFAULT_RECEIVE_PACK.equals(args[0])) {
                    pushHook = new LocalVCPostPushHook(localVCServletService, pushProcessingService, passwordAuthenticationNoticeService, getServerSession(), user, audit);
                }
            }
            RepositoryCache.FileKey key = RepositoryCache.FileKey.lenient(rootDir.toFile(), FS.DETECTED);

            // Open the Git repository
            try (Repository repository = key.open(true)) {
                String subCommand = args[0];

                try {
                    // Dispatch based on subcommand: upload-pack or receive-pack
                    switch (subCommand) {
                        case RemoteConfig.DEFAULT_UPLOAD_PACK -> {
                            // Prepare UploadPack handler
                            UploadPack uploadPack = LocalVCReceivePackLimits.apply(new UploadPack(repository));

                            // Extract protocol version from environment variables (if present)
                            Environment environment = getEnvironment();
                            Map<String, String> envVars = environment.getEnv();
                            String protocol = MapEntryUtils.isEmpty(envVars) ? null : envVars.get(GitProtocolConstants.PROTOCOL_ENVIRONMENT_VARIABLE);

                            if (GenericUtils.isNotBlank(protocol)) {
                                uploadPack.setExtraParameters(Set.of(protocol));
                            }

                            // Register pre-upload hook for Artemis-specific logic
                            uploadPack.setPreUploadHook(fetchHook);

                            // Begin upload-pack operation
                            uploadPack.upload(getInputStream(), getOutputStream(), getErrorStream());
                        }
                        case RemoteConfig.DEFAULT_RECEIVE_PACK -> {
                            try (var _ = pushProcessingService.claimProgrammingExerciseMutation(repository, exercise)) {
                                LocalVCPrePushHook prePushHook = new LocalVCPrePushHook(localVCServletService, user, audit);
                                // The objects of the push are stored in a place of their own, and they reach the repository only if the push is accepted.
                                ReceivePack receivePack = LocalVCReceivePackLimits.apply(QuarantinedReceivePack.create(repository, prePushHook::examineBeforeObjects));

                                receivePack.setPreReceiveHook(prePushHook);
                                receivePack.setPostReceiveHook(pushHook);

                                receivePack.receive(getInputStream(), getOutputStream(), getErrorStream());
                            }
                            catch (HttpStatusException e) {
                                // A push that is refused, for example because the repository is being changed by something else, was attempted, so it is a failed push and not a
                                // write that had nothing new. Nothing changes if the push was already known to the audit.
                                audit.onPushReceived(LocalVCFetchPreUploadHook.headCommitOf(repository));
                                throw e;
                            }
                        }
                        default -> throw new IllegalArgumentException("Unknown git command: " + command);
                    }
                }
                finally {
                    // The command is over, so everything about it is known, and it is one access whatever it moved.
                    if (RemoteConfig.DEFAULT_UPLOAD_PACK.equals(subCommand)) {
                        accessLogRecorder.recordFetchSSH(accessLogSubject, audit, repository);
                    }
                    else if (RemoteConfig.DEFAULT_RECEIVE_PACK.equals(subCommand)) {
                        accessLogRecorder.recordPushSSH(accessLogSubject, audit, repository);
                    }
                }
            }

            // Notify SSH server of success
            onExit(0);
        }
        catch (HttpStatusException e) {
            String message = "Push rejected: " + e.getMessage() + " Please retry the push later.";
            try {
                if (getErrorStream() != null) {
                    getErrorStream().write((message + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
                }
            }
            catch (IOException ignored) {
                // The client may already have disconnected; the mutation lease was still released by try-with-resources.
            }
            onExit(-1, message);
        }
        catch (Throwable t) {
            // Notify SSH server of failure with the exception type
            onExit(-1, t.getClass().getSimpleName());
        }
        finally {
            // The thread belongs to a pool that serves other commands. The processing of a push sets an authentication for the thread, which must not stay for the next command.
            SecurityContextHolder.clearContext();
        }
    }
}
