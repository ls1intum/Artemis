package de.tum.in.www1.artemis.config.localvcci.ssh;

import org.apache.sshd.git.pack.GitPackCommand;
import org.apache.sshd.git.pack.GitPackCommandFactory;

public class SshGitCommandFactory extends GitPackCommandFactory {

    @Override
    public GitPackCommand createGitCommand(String command) {
        return new SshGitCommand(getGitLocationResolver(), command, resolveExecutorService(command));
    }
}
