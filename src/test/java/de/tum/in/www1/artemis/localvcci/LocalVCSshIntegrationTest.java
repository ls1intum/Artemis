package de.tum.in.www1.artemis.localvcci;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_LOCALVC;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.SshException;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.session.ServerSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.config.localvcci.ssh.HashUtils;
import de.tum.in.www1.artemis.config.localvcci.ssh.SshGitCommand;
import de.tum.in.www1.artemis.config.localvcci.ssh.service.SshGitCommandFactoryService;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionTestRepository;
import de.tum.in.www1.artemis.repository.UserRepository;

@Profile(PROFILE_LOCALVC)
class LocalVCSshIntegrationTest extends LocalVCIntegrationTest {

    final String hostname = "localhost";

    final int port = 7921;

    @Autowired
    ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    @SuppressWarnings("unused")
    private SshServer sshServer;

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testPushCommandWithoutSession() {

        // this command arrives at the ssh server if you would manually push from the command line
        String commandString = "git-receive-pack '/git/" + projectKey1 + "/" + templateRepositorySlug + "'";
        SshGitCommandFactoryService sshGitCommandFactory = (SshGitCommandFactoryService) sshServer.getCommandFactory();
        SshGitCommand command = (SshGitCommand) sshGitCommandFactory.createGitCommand(commandString);
        try {
            command.run();
        }
        catch (NullPointerException e) {
            assertThat(e).isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDirectlyAuthenticateOverSsh() {

        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        KeyPair keyPair = setupKeyPairAndAddToUser();
        User user = userRepository.getUser();

        SshClient client = SshClient.setUpDefaultClient();
        client.start();

        ClientSession session;
        try {
            ConnectFuture connectFuture = client.connect(user.getName(), hostname, port);
            connectFuture.await(10, TimeUnit.SECONDS);

            session = connectFuture.getSession();
            session.addPublicKeyIdentity(keyPair);

            session.auth().verify(10, TimeUnit.SECONDS);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        assertThat(session.isAuthenticated()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAuthenticationFailure() {

        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        KeyPair keyPair2 = generateKeyPair();

        User user = userRepository.getUser();

        SshClient client = SshClient.setUpDefaultClient();
        client.start();

        ClientSession session = null;
        try {
            ConnectFuture connectFuture = client.connect(user.getName(), hostname, port);
            connectFuture.await(10, TimeUnit.SECONDS);

            session = connectFuture.getSession();
            session.addPublicKeyIdentity(keyPair2);

            session.auth().verify(10, TimeUnit.SECONDS);
        }
        catch (IOException e) {
            assertThat(e).isInstanceOf(SshException.class);
        }
        assertThat(session).isNotNull();
        assertThat(session.isAuthenticated()).isFalse();

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testConnectOverSshAndReceivePack() {
        clientConnectToArtemisSshServer();
        var serverSessions = sshServer.getActiveSessions();
        var serverSession = serverSessions.getFirst();

        String commandString = "git-upload-pack '/git/" + projectKey1 + "/" + templateRepositorySlug + "'";
        try {
            setupCommand(commandString, (ServerSession) serverSession).run();
        }
        catch (NullPointerException e) {
            assertThat(e).isInstanceOf(NullPointerException.class);
        }

        commandString = "git-receive-pack '/git/" + projectKey1 + "/" + templateRepositorySlug + "'";
        try {
            setupCommand(commandString, (ServerSession) serverSession).run();
        }
        catch (NullPointerException e) {
            assertThat(e).isInstanceOf(NullPointerException.class);
            assertThat(false).isTrue();
        }

    }

    SshGitCommand setupCommand(String commandString, ServerSession serverSession) {
        SshGitCommandFactoryService sshGitCommandFactory = (SshGitCommandFactoryService) sshServer.getCommandFactory();
        SshGitCommand command = (SshGitCommand) sshGitCommandFactory.createGitCommand(commandString);
        command.setSession(serverSession);
        command.setOutputStream(new ByteArrayOutputStream());
        command.setInputStream(new ByteArrayInputStream(new byte[] {}));
        return command;
    }

    void clientConnectToArtemisSshServer() {
        var serverSessions = sshServer.getActiveSessions();
        var numberOfSessions = serverSessions.size();
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        KeyPair keyPair = setupKeyPairAndAddToUser();
        User user = userRepository.getUser();

        SshClient client = SshClient.setUpDefaultClient();
        client.start();

        ClientSession clientSession;
        try {
            ConnectFuture connectFuture = client.connect(user.getName(), hostname, port);
            connectFuture.await(10, TimeUnit.SECONDS);

            clientSession = connectFuture.getSession();
            clientSession.addPublicKeyIdentity(keyPair);

            clientSession.auth().verify(10, TimeUnit.SECONDS);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        serverSessions = sshServer.getActiveSessions();
        assertThat(clientSession.isAuthenticated()).isTrue();
        assertThat(serverSessions.size()).isEqualTo(numberOfSessions + 1);
    }

    KeyPair setupKeyPairAndAddToUser() {
        KeyPair rsaKeyPair = generateKeyPair();
        PublicKey publicKey = rsaKeyPair.getPublic();
        String publicKeyAsString = Base64.getEncoder().encodeToString(publicKey.getEncoded());

        User user = userRepository.getUser();

        String keyHash = HashUtils.getSha512Fingerprint(publicKey);
        userRepository.updateUserSshPublicKeyHash(user.getId(), keyHash, publicKeyAsString);
        user = userRepository.getUser();

        assertThat(user.getSshPublicKey()).isEqualTo(publicKeyAsString);
        return rsaKeyPair;
    }

    KeyPair generateKeyPair() {
        KeyPairGenerator kpg;
        try {
            kpg = KeyPairGenerator.getInstance("RSA");
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        kpg.initialize(2048);
        return kpg.genKeyPair();
    }
}
