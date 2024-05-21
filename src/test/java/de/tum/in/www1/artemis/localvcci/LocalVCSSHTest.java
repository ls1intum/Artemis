package de.tum.in.www1.artemis.localvcci;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.sshd.server.SshServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.config.localvcci.ssh.SshConfiguration;
import de.tum.in.www1.artemis.config.localvcci.ssh.SshGitCommand;
import de.tum.in.www1.artemis.config.localvcci.ssh.service.SshGitCommandFactoryService;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionTestRepository;

public class LocalVCSSHTest extends LocalVCIntegrationTest {

    @Autowired
    ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    private SshConfiguration sshConfiguration;

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorTriesToForcePushOverHttp() throws Exception {
        SshServer sshServer = sshConfiguration.sshServer();

        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        // this command arrives at the ssh server if you would manually push from the command line
        String commandString = "git-receive-pack '/git/" + projectKey1 + "/" + templateRepositorySlug + "'";
        SshGitCommandFactoryService sfd = (SshGitCommandFactoryService) sshServer.getCommandFactory();
        SshGitCommand command = (SshGitCommand) sfd.createGitCommand(commandString);
        // command.setSession(....);
        // TODO set serverSession in command and authenticate

        try {
            command.run();
        }
        catch (Exception e) {
            assertThat(e).isInstanceOf(NullPointerException.class);
        }
    }

}
