package de.tum.cit.aet.artemis.programming.icl.ssh;

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
import de.tum.cit.aet.artemis.service.connectors.localvc.LocalVCPostPushHook;
import de.tum.cit.aet.artemis.service.connectors.localvc.LocalVCPrePushHook;
import de.tum.cit.aet.artemis.service.connectors.localvc.LocalVCServletService;

public class SshGitCommand extends GitPackCommand {

    private final LocalVCServletService localVCServletService;

    /**
     * @param rootDirResolver       Resolver for GIT root directory
     * @param command               Command to execute
     * @param executorService       An {@link CloseableExecutorService} to be used when
     *                                  {@code start(ChannelSession, Environment)}-ing execution. If {@code null} an ad-hoc
     *                                  single-threaded service is created and used.
     * @param localVCServletService the service to be passed for pre and post hooks
     */
    public SshGitCommand(GitLocationResolver rootDirResolver, String command, CloseableExecutorService executorService, LocalVCServletService localVCServletService) {
        super(rootDirResolver, command, executorService);
        this.localVCServletService = localVCServletService;
    }

    @Override
    protected Path resolveRootDirectory(String command, String[] args) throws IOException {
        GitLocationResolver resolver = getGitLocationResolver();
        // we deviate a bit from the API here and resolve the repo directly and avoid dealing with the root directory
        return resolver.resolveRootDirectory(command, args, getServerSession(), getFileSystem());
    }

    @Override
    public void run() {
        String command = getCommand();
        try {
            List<String> argsList = parseDelimitedString(command, " ", true);
            String[] args = argsList.toArray(String[]::new);
            for (int i = 0; i < args.length; i++) {
                String argVal = args[i];
                if (argVal.startsWith("'") && argVal.endsWith("'")) {
                    args[i] = argVal.substring(1, argVal.length() - 1);
                    argVal = args[i];
                }
                if (argVal.startsWith("\"") && argVal.endsWith("\"")) {
                    args[i] = argVal.substring(1, argVal.length() - 1);
                }
            }

            if (args.length != 2) {
                throw new IllegalArgumentException("Invalid git command line (no arguments): " + command);
            }

            Path rootDir = resolveRootDirectory(command, args);
            RepositoryCache.FileKey key = RepositoryCache.FileKey.lenient(rootDir.toFile(), FS.DETECTED);
            try (Repository repository = key.open(true /* must exist */)) {
                User user = getServerSession().getAttribute(SshConstants.USER_KEY);

                String subCommand = args[0];
                if (RemoteConfig.DEFAULT_UPLOAD_PACK.equals(subCommand)) {
                    UploadPack uploadPack = new UploadPack(repository);
                    Environment environment = getEnvironment();
                    Map<String, String> envVars = environment.getEnv();
                    String protocol = MapEntryUtils.isEmpty(envVars) ? null : envVars.get(GitProtocolConstants.PROTOCOL_ENVIRONMENT_VARIABLE);
                    if (GenericUtils.isNotBlank(protocol)) {
                        uploadPack.setExtraParameters(Collections.singleton(protocol));
                    }
                    uploadPack.upload(getInputStream(), getOutputStream(), getErrorStream());
                }
                else if (RemoteConfig.DEFAULT_RECEIVE_PACK.equals(subCommand)) {
                    var receivePack = new ReceivePack(repository);
                    receivePack.setPreReceiveHook(new LocalVCPrePushHook(localVCServletService, user));
                    receivePack.setPostReceiveHook(new LocalVCPostPushHook(localVCServletService));
                    receivePack.receive(getInputStream(), getOutputStream(), getErrorStream());
                }
                else {
                    throw new IllegalArgumentException("Unknown git command: " + command);
                }
            }

            onExit(0);
        }
        catch (Throwable t) {
            onExit(-1, t.getClass().getSimpleName());
        }
    }

}
