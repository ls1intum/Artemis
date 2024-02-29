package de.tum.in.www1.artemis.config.localvcci.ssh;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.sshd.common.util.threads.CloseableExecutorService;
import org.apache.sshd.git.GitLocationResolver;
import org.apache.sshd.git.pack.GitPackCommand;

public class SshGitCommand extends GitPackCommand {

    /**
     * @param rootDirResolver Resolver for GIT root directory
     * @param command         Command to execute
     * @param executorService An {@link CloseableExecutorService} to be used when
     *                            {@code start(ChannelSession, Environment)}-ing execution. If {@code null} an ad-hoc
     *                            single-threaded service is created and used.
     */
    public SshGitCommand(GitLocationResolver rootDirResolver, String command, CloseableExecutorService executorService) {
        super(rootDirResolver, command, executorService);
    }

    @Override
    protected Path resolveRootDirectory(String command, String[] args) throws IOException {
        GitLocationResolver resolver = getGitLocationResolver();
        // we deviate a bit from the API here and resolve the repo directly and avoid dealing with the root directory
        return resolver.resolveRootDirectory(command, args, getServerSession(), getFileSystem());
    }

}
