package de.tum.cit.aet.artemis.programming.icl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.UnresolvedAddressException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.apache.sshd.common.session.helpers.AbstractSession;
import org.apache.sshd.server.session.ServerSession;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.UserSshPublicKey;
import de.tum.cit.aet.artemis.programming.service.localvc.SshGitCommandFactoryService;
import de.tum.cit.aet.artemis.programming.service.localvc.ssh.HashUtils;
import de.tum.cit.aet.artemis.programming.service.localvc.ssh.SshConstants;
import de.tum.cit.aet.artemis.programming.service.localvc.ssh.SshGitCommand;

class LocalVCSshIntegrationTest extends LocalVCIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(LocalVCSshIntegrationTest.class);

    private static final String TEST_PREFIX = "localvcsshint";

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Value("${server.url}")
    private String artemisServerUrl;

    @Value("${artemis.version-control.ssh-port}")
    private int sshPort;

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
    void testDirectlyAuthenticateOverSsh() throws IOException, GeneralSecurityException {

        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        KeyPair keyPair = setupKeyPairAndAddToUser();
        User user = userTestRepository.getUser();

        try (SshClient client = SshClient.setUpDefaultClient()) {
            client.start();

            ClientSession session = connect(client, user, keyPair);
            assertThat(session.isAuthenticated()).isTrue();
        }
    }

    @NonNull
    private ClientSession connect(SshClient client, User user, KeyPair keyPair) throws IOException {
        try {
            // avoid using any local host config which could confuse the test
            client.setHostConfigEntryResolver(HostConfigEntryResolver.EMPTY);

            URI uri = URI.create(artemisServerUrl);
            String host = uri.getHost();
            log.info("Connecting to SSH server at {}:{} for user {}", host, sshPort, user.getName());
            ConnectFuture connectFuture = client.connect(user.getName(), host, sshPort);
            connectFuture.await(10, TimeUnit.SECONDS);

            ClientSession session = connectFuture.getSession();
            session.addPublicKeyIdentity(keyPair);

            session.auth().verify(10, TimeUnit.SECONDS);
            return session;
        }
        catch (IOException | UnresolvedAddressException e) {
            log.error("Error during SSH connection", e);
            // rethrow the exception to indicate failure to the caller for assertions
            throw e;
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAuthenticationFailureBecauseKeyHasExpired() throws IOException, GeneralSecurityException {

        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        KeyPair keyPair = setupKeyPairAndAddToUser();
        User user = userTestRepository.getUser();
        var userKey = userSshPublicKeyRepository.findAllByUserId(user.getId()).getFirst();
        userKey.setExpiryDate(ZonedDateTime.now().minusMonths(1L));
        userSshPublicKeyRepository.save(userKey);

        assertThatThrownBy(() -> {

            try (SshClient client = SshClient.setUpDefaultClient()) {
                client.start();
                connect(client, user, keyPair);
            }
        }).isInstanceOf(SshException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAuthenticationFailure() {
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        KeyPair keyPair = generateKeyPair();

        User user = userTestRepository.getUser();

        assertThatThrownBy(() -> {

            try (SshClient client = SshClient.setUpDefaultClient()) {
                client.start();
                connect(client, user, keyPair);
            }
        }).isInstanceOf(SshException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testConnectOverSshAndReceivePack() throws IOException, GeneralSecurityException {
        try (var client = clientConnectToArtemisSshServer()) {
            assertThat(client).isNotNull();
            var user = userTestRepository.getUser();
            var serverSession = getCurrentServerSession(user);

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

            // 3. Verify cacheAttributesInSshSession stored data in the session during receive-pack execution
            var cachedAccessLog = ((ServerSession) serverSession).getAttribute(SshConstants.VCS_ACCESS_LOG_KEY);
            assertThat(cachedAccessLog).as("cacheAttributesInSshSession should cache access log for template repo").isNotNull();
            assertThat(cachedAccessLog.getAuthenticationMechanism()).isEqualTo(AuthenticationMechanism.SSH);
            assertThat(cachedAccessLog.getUser().getLogin()).isEqualTo(user.getLogin());
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSshGitCommand_testsRepo_handlesEmptyParticipationGracefully() throws IOException, GeneralSecurityException {
        try (var client = clientConnectToArtemisSshServer()) {
            assertThat(client).isNotNull();
            var user = userTestRepository.getUser();
            var serverSession = getCurrentServerSession(user);

            // Execute git-upload-pack for tests repository — tests repo has no student participation
            // This verifies cacheAttributesInSshSession handles the empty Optional gracefully
            final var uploadCommandString = "git-upload-pack '/git/" + projectKey1 + "/" + testsRepositorySlug + "'";

            // The git-upload-pack command may throw NullPointerException from upload-pack IO setup,
            // but cacheAttributesInSshSession must NOT throw when participation is empty
            try {
                SshGitCommand command = setupCommand(uploadCommandString, (ServerSession) serverSession);
                command.run();
            }
            catch (NullPointerException e) {
                // NPE expected from upload-pack IO setup (e.g. getErrorStream() returning null), not from cacheAttributesInSshSession
                var stackTrace = Arrays.stream(e.getStackTrace()).map(StackTraceElement::getClassName).toList();
                assertThat(stackTrace).as("NPE should not originate from LocalVCServletService").noneMatch(cls -> cls.contains("LocalVCServletService"));
            }

            // Verify the session survived without crashing
            assertThat(serverSession.isOpen()).isTrue();

            // When participation is absent, cacheAttributesInSshSession should NOT cache anything
            var cachedAccessLog = ((ServerSession) serverSession).getAttribute(SshConstants.VCS_ACCESS_LOG_KEY);
            assertThat(cachedAccessLog).as("No VCS access log should be cached when participation is absent").isNull();
            var cachedParticipation = ((ServerSession) serverSession).getAttribute(SshConstants.PARTICIPATION_KEY);
            assertThat(cachedParticipation).as("No participation should be cached for tests repo").isNull();
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

    /**
     * Note: Don't count unattached sessions as a potential result from previous tests.
     * See {@link org.apache.sshd.server.SshServer#getActiveSessions}
     * and {@link org.apache.sshd.common.session.helpers.AbstractSession#getSession}.
     */
    private SshClient clientConnectToArtemisSshServer() throws GeneralSecurityException, IOException {
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        KeyPair keyPair = setupKeyPairAndAddToUser();
        User user = userTestRepository.getUser();

        // Capture baseline session count BEFORE connecting, scoped to the test user to avoid flakiness under parallel runs
        var baselineServerSessions = sshServer.getActiveSessions();
        long baselineAttachedSessionCount = baselineServerSessions.stream().filter(session -> user.getName().equals(session.getUsername())).count();

        SshClient client = SshClient.setUpDefaultClient();
        client.start();

        ClientSession clientSession = connect(client, user, keyPair);

        // Get current session count AFTER connecting, scoped to the test user
        var currentServerSessions = sshServer.getActiveSessions();
        var attachedServerSessions = currentServerSessions.stream().filter(session -> user.getName().equals(session.getUsername())).count();
        assertThat(clientSession.isAuthenticated()).isTrue();
        assertThat(attachedServerSessions).as("There are more server sessions activated than expected.").isEqualTo(baselineAttachedSessionCount + 1);
        return client;
    }

    private AbstractSession getCurrentServerSession(User user) {
        var serverSessions = sshServer.getActiveSessions();
        // parallel tests might create additional sessions, we need to be specific
        var serverSession = serverSessions.stream().filter(session -> user.getName().equals(session.getUsername())).findFirst();

        return serverSession.orElseThrow(() -> new IllegalStateException("No server session found for user " + user.getName()));
    }

    private KeyPair setupKeyPairAndAddToUser() throws GeneralSecurityException, IOException {

        User user = userTestRepository.getUser();
        userSshPublicKeyRepository.deleteAll();

        KeyPair rsaKeyPair = generateKeyPair();
        String sshPublicKey = writePublicKeyToString(rsaKeyPair.getPublic(), user.getLogin() + "@host");

        AuthorizedKeyEntry keyEntry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(sshPublicKey);
        // Extract the PublicKey object
        PublicKey publicKey = keyEntry.resolvePublicKey(null, null, null);
        String keyHash = HashUtils.getSha512Fingerprint(publicKey);

        var userPublicSshKey = createNewPublicKey(keyHash, sshPublicKey, user);
        userSshPublicKeyRepository.save(userPublicSshKey);
        var fetchedKey = userSshPublicKeyRepository.findAllByUserId(user.getId());
        assertThat(fetchedKey).isNotEmpty();
        assertThat(fetchedKey.getFirst().getPublicKey()).isEqualTo(sshPublicKey);
        return rsaKeyPair;
    }

    private String writePublicKeyToString(PublicKey publicKey, String comment) throws IOException, GeneralSecurityException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            // Assuming you have an instance of a class with the writePublicKey method
            OpenSSHKeyPairResourceWriter writer = new OpenSSHKeyPairResourceWriter();
            writer.writePublicKey(publicKey, comment, outputStream);
            return outputStream.toString();
        }
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            return keyGen.generateKeyPair();
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static UserSshPublicKey createNewPublicKey(String keyHash, String publicKey, User user) {
        UserSshPublicKey userSshPublicKey = new UserSshPublicKey();
        userSshPublicKey.setLabel("Key 1");
        userSshPublicKey.setPublicKey(publicKey);
        userSshPublicKey.setKeyHash(keyHash);
        userSshPublicKey.setUserId(user.getId());
        userSshPublicKey.setCreationDate(ZonedDateTime.now());

        return userSshPublicKey;
    }
}
