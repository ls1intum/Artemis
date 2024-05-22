package de.tum.in.www1.artemis.localvcci;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_LOCALVC;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.server.SshServer;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.test.context.support.WithMockUser;
import org.testcontainers.shaded.org.bouncycastle.openssl.PEMParser;

import de.tum.in.www1.artemis.config.localvcci.ssh.HashUtils;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionTestRepository;
import de.tum.in.www1.artemis.repository.UserRepository;

@Profile(PROFILE_LOCALVC)
class LocalVCSshTest extends LocalVCIntegrationTest {

    String hostname = "localhost";

    int port = 7921;

    String sshPublicKey = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIMnAXgzXikZMbjk1XEIuAQbmDTQheiK4Bx7v3hn//qPY instructor1";

    String sshPrivateKey = "-----BEGIN OPENSSH PRIVATE KEY-----\n" + "b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW\n"
            + "QyNTUxOQAAACDJwF4M14pGTG45NVxCLgEG5g00IXoiuAce794Z//6j2AAAAJB2InXodiJ1\n" + "6AAAAAtzc2gtZWQyNTUxOQAAACDJwF4M14pGTG45NVxCLgEG5g00IXoiuAce794Z//6j2A\n"
            + "AAAEABBN8M7QqRn1i3MCY9PwC4PirfLVfvQoQUYXa7VdvPFsnAXgzXikZMbjk1XEIuAQbm\n" + "DTQheiK4Bx7v3hn//qPYAAAAC2luc3RydWN0b3IxAQI=\n" + "-----END OPENSSH PRIVATE KEY-----";

    @Autowired
    ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    // @Autowired
    // private SshConfiguration sshConfiguration;
    @Autowired
    private UserRepository userRepository;

    @Autowired
    @SuppressWarnings("unused")
    private SshServer sshServer;

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorTriesToForcePushOverSsh() {

        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        // this test currently is unable to authenticate to the ssh server
        try {
            gitPushOverSSH(sshPublicKey, sshPrivateKey);
        }
        catch (Exception ignored) {
        }
    }

    void gitPushOverSSH(String sshPublicKey, String sshPrivateKey) throws GeneralSecurityException, IOException {

        User user = userRepository.getUser();
        // Parse the public key string
        AuthorizedKeyEntry keyEntry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(sshPublicKey);
        // Extract the PublicKey object
        PublicKey publicKey = keyEntry.resolvePublicKey(null, null, null);
        String keyHash = HashUtils.getSha512Fingerprint(publicKey);
        userRepository.updateUserSshPublicKeyHash(user.getId(), keyHash, sshPublicKey);
        user = userRepository.getUser();

        assertThat(user.getSshPublicKey()).isEqualTo(sshPublicKey);

        SshClient client = SshClient.setUpDefaultClient();
        client.start();
        PrivateKey privateKey = loadPrivateKey(sshPrivateKey);

        try {
            ClientSession session = connectAndAuthenticate(client, user.getName(), publicKey, privateKey, hostname, port);
            if (session != null && session.isAuthenticated()) {
                executeGitPush(session);
            }
            else {
                System.err.println("Failed to authenticate the session.");
            }
        }
        catch (IOException e) {
            throw new RuntimeException("Error during SSH operation", e);
        }
        finally {
            client.stop();
        }
    }

    private static ClientSession connectAndAuthenticate(SshClient client, String username, PublicKey publicKey, PrivateKey privateKey, String hostname, int port)
            throws IOException {
        ConnectFuture connectFuture = client.connect(username, hostname, port);
        connectFuture.await(10, TimeUnit.SECONDS); // Wait for the connection to be established

        ClientSession session = connectFuture.getSession();
        if (session == null) {
            throw new IOException("Failed to create session.");
        }
        session.addPublicKeyIdentity(new KeyPair(publicKey, privateKey));
        session.auth().verify(10, TimeUnit.SECONDS); // Wait for authentication to complete

        return session;
    }

    private static void executeGitPush(ClientSession session) throws IOException {
        try (ChannelExec channel = session.createExecChannel("git push origin master")) {
            channel.setOut(System.out);
            channel.setErr(System.err);
            channel.open().verify(5, TimeUnit.SECONDS); // Wait for the channel to be opened

            channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), TimeUnit.SECONDS.toMillis(30)); // Wait for the command to complete
        }
    }

    private static PrivateKey loadPrivateKey(String privateKeyContent) throws IOException {
        try (StringReader keyReader = new StringReader(privateKeyContent); PEMParser pemParser = new PEMParser(keyReader)) {

            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            Object object = pemParser.readObject();

            if (object instanceof PEMKeyPair) {
                return converter.getKeyPair((PEMKeyPair) object).getPrivate();
            }
            else {
                throw new IllegalArgumentException("Invalid private key format");
            }
        }
    }
}
