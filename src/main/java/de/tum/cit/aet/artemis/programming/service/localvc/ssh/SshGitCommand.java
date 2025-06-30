package de.tum.cit.aet.artemis.programming.service.localvc.ssh;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.common.util.MapEntryUtils;
import org.apache.sshd.common.util.threads.CloseableExecutorService;
import org.apache.sshd.git.GitLocationResolver;
import org.apache.sshd.git.pack.GitPackCommand;
import org.apache.sshd.server.Environment;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.transport.GitProtocolConstants;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.util.FS;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCFetchPreUploadHookSSH;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCPostPushHook;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCPrePushHook;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCServletService;

/**
 * Custom Git command handler that integrates Artemis-specific
 * hooks into SSH-based Git operations for UploadPack and ReceivePack.
 */
public class SshGitCommand extends GitPackCommand {

    private final LocalVCServletService localVCServletService;

    /**
     * Constructs a new {@link SshGitCommand}.
     *
     * @param rootDirResolver       Resolver for GIT root directory
     * @param command               Git command string to execute
     * @param executorService       Optional executor service for command execution
     * @param localVCServletService LocalVC service providing pre/post push/fetch hooks
     */
    public SshGitCommand(GitLocationResolver rootDirResolver, String command, CloseableExecutorService executorService, LocalVCServletService localVCServletService) {
        super(rootDirResolver, command, executorService);
        this.localVCServletService = localVCServletService;
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
            Path rootDir = resolveRootDirectory(command, args);
            RepositoryCache.FileKey key = RepositoryCache.FileKey.lenient(rootDir.toFile(), FS.DETECTED);

            // Open the Git repository
            try (Repository repository = key.open(true)) {
                // Retrieve the authenticated user from the SSH session
                User user = getServerSession().getAttribute(SshConstants.USER_KEY);
                String subCommand = args[0];

                // Dispatch based on subcommand: upload-pack or receive-pack
                switch (subCommand) {
                    case RemoteConfig.DEFAULT_UPLOAD_PACK -> {
                        // Prepare UploadPack handler
                        UploadPack uploadPack = new UploadPack(repository);

                        // Extract protocol version from environment variables (if present)
                        Environment environment = getEnvironment();
                        Map<String, String> envVars = environment.getEnv();
                        String protocol = MapEntryUtils.isEmpty(envVars) ? null : envVars.get(GitProtocolConstants.PROTOCOL_ENVIRONMENT_VARIABLE);

                        if (GenericUtils.isNotBlank(protocol)) {
                            uploadPack.setExtraParameters(Collections.singleton(protocol));
                        }

                        // Register pre-upload hook for Artemis-specific logic
                        uploadPack.setPreUploadHook(new LocalVCFetchPreUploadHookSSH(localVCServletService, getServerSession()));

                        // Begin upload-pack operation
                        uploadPack.upload(getInputStream(), getOutputStream(), getErrorStream());
                    }
                    case RemoteConfig.DEFAULT_RECEIVE_PACK -> {
                        // Prepare ReceivePack handler
                        ReceivePack receivePack = new ReceivePack(repository);

                        // Register pre- and post-receive hooks for Artemis push handling
                        receivePack.setPreReceiveHook(new LocalVCPrePushHook(localVCServletService, user));
                        receivePack.setPostReceiveHook(new LocalVCPostPushHook(localVCServletService, getServerSession(), user));

                        // Begin receive-pack operation
                        receivePack.receive(getInputStream(), getOutputStream(), getErrorStream());
                    }
                    default -> throw new IllegalArgumentException("Unknown git command: " + command);
                }
            }

            // Notify SSH server of success
            onExit(0);
        }
        catch (Throwable t) {
            // Notify SSH server of failure with the exception type
            onExit(-1, t.getClass().getSimpleName());
        }
    }
}
