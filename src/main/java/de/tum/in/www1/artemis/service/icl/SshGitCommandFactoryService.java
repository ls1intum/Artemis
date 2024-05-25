package de.tum.in.www1.artemis.service.icl;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_LOCALVC;

import org.apache.sshd.git.pack.GitPackCommand;
import org.apache.sshd.git.pack.GitPackCommandFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.icl.ssh.SshGitCommand;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCServletService;

@Profile(PROFILE_LOCALVC)
@Service
public class SshGitCommandFactoryService extends GitPackCommandFactory {

    public final LocalVCServletService localVCServletService;

    public SshGitCommandFactoryService(LocalVCServletService localVCServletService) {
        this.localVCServletService = localVCServletService;
    }

    @Override
    public GitPackCommand createGitCommand(String command) {
        return new SshGitCommand(getGitLocationResolver(), command, resolveExecutorService(command), localVCServletService);
    }
}
