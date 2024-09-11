package de.tum.cit.aet.artemis.service.icl;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import org.apache.sshd.git.pack.GitPackCommand;
import org.apache.sshd.git.pack.GitPackCommandFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.connectors.localvc.LocalVCServletService;
import de.tum.cit.aet.artemis.programming.icl.ssh.SshGitCommand;

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
