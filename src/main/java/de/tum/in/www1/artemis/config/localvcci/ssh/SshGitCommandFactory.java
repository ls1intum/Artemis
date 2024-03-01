package de.tum.in.www1.artemis.config.localvcci.ssh;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_LOCALVC;

import org.apache.sshd.git.pack.GitPackCommand;
import org.apache.sshd.git.pack.GitPackCommandFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile(PROFILE_LOCALVC)
@Service
public class SshGitCommandFactory extends GitPackCommandFactory {

    @Override
    public GitPackCommand createGitCommand(String command) {
        return new SshGitCommand(getGitLocationResolver(), command, resolveExecutorService(command));
    }
}
