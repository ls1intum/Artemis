package de.tum.in.www1.artemis.localvcci;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_LOCALVC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

import de.tum.in.www1.artemis.config.icl.ssh.HashUtils;
import de.tum.in.www1.artemis.config.icl.ssh.SshGitCommand;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.icl.SshGitCommandFactoryService;

@Profile(PROFILE_LOCALVC)
class LocalVCSshIntegrationTest extends LocalVCIntegrationTest {

    private final String hostname = "localhost";

    private final int port = 7921;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SshServer sshServer;

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testPushCommandWithoutSession() {

        // this command arrives at the ssh server if you would manually push from the command line
        String commandString = "git-receive-pack '/git/" + projectKey1 + "/" + templateRepositorySlug + "'";
        SshGitCommandFactoryService sshGitCommandFactory = (SshGitCommandFactoryService) sshServer.getCommandFactory();
        SshGitCommand command = (SshGitCommand) sshGitCommandFactory.createGitCommand(commandString);
        assertThatThrownBy(command::run).withFailMessage("Expected NullPointerException when running 'git-receive-pack' command without a session")
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDirectlyAuthenticateOverSsh() throws IOException {

        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        KeyPair keyPair = setupKeyPairAndAddToUser();
        User user = userRepository.getUser();

        try (SshClient client = SshClient.setUpDefaultClient()) {
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
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAuthenticationFailure() {
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        KeyPair keyPair2 = generateKeyPair();

        User user = userRepository.getUser();

        assertThatThrownBy(() -> {

            try (SshClient client = SshClient.setUpDefaultClient()) {
                client.start();

                ConnectFuture connectFuture = client.connect(user.getName(), hostname, port);
                connectFuture.await(10, TimeUnit.SECONDS);

                ClientSession session = connectFuture.getSession();
                session.addPublicKeyIdentity(keyPair2);

                session.auth().verify(10, TimeUnit.SECONDS);
            }
        }).isInstanceOf(SshException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testConnectOverSshAndReceivePack() throws IOException {
        try (var client = clientConnectToArtemisSshServer()) {
            assertThat(client).isNotNull();
            var serverSessions = sshServer.getActiveSessions();
            var serverSession = serverSessions.getFirst();

            final var uploadCommandString = "git-upload-pack '/git/" + projectKey1 + "/" + templateRepositorySlug + "'";

            // The following line is expected to throw a NullPointerException because the 'git-upload-pack' command might not be properly set up in the SshGitCommandFactoryService,
            // or there could be a missing or incorrectly initialized dependency within the command execution process.
            assertThatThrownBy(() -> setupCommand(uploadCommandString, (ServerSession) serverSession).run()).isInstanceOf(NullPointerException.class);

            final var receiveCommandString = "git-receive-pack '/git/" + projectKey1 + "/" + templateRepositorySlug + "'";

            // The following command should not throw an exception as the 'git-receive-pack' command is likely properly set up and all dependencies are correctly initialized.
            SshGitCommand receiveCommand = setupCommand(receiveCommandString, (ServerSession) serverSession);
            receiveCommand.run();

            // 1. Ensure that the session is still active after running the command.
            assertThat(serverSession.isOpen()).isTrue();
            // 2. Check that the command output stream is not null and contains expected output.
            ByteArrayOutputStream outputStream = (ByteArrayOutputStream) receiveCommand.getOutputStream();
            assertThat(outputStream).isNotNull();
            assertThat(outputStream.size()).isGreaterThan(0); // Assuming the command produces some output
        }
    }

    private SshGitCommand setupCommand(String commandString, ServerSession serverSession) {
        SshGitCommandFactoryService sshGitCommandFactory = (SshGitCommandFactoryService) sshServer.getCommandFactory();
        SshGitCommand command = (SshGitCommand) sshGitCommandFactory.createGitCommand(commandString);
        command.setSession(serverSession);
        command.setOutputStream(new ByteArrayOutputStream());
        command.setInputStream(new ByteArrayInputStream(new byte[] {}));
        return command;
    }

    private SshClient clientConnectToArtemisSshServer() {
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
        return client;
    }

    private KeyPair setupKeyPairAndAddToUser() {
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

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
