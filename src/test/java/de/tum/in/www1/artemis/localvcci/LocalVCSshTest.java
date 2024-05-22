package de.tum.in.www1.artemis.localvcci;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_LOCALVC;
import static org.assertj.core.api.Assertions.assertThat;

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
import org.apache.sshd.server.SshServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.config.localvcci.ssh.HashUtils;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionTestRepository;
import de.tum.in.www1.artemis.repository.UserRepository;

@Profile(PROFILE_LOCALVC)
class LocalVCSshTest extends LocalVCIntegrationTest {

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
    void testInstructorConnectingToArtemisSshServer() {

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

    KeyPair setupKeyPairAndAddToUser() {
        KeyPairGenerator kpg;
        try {
            kpg = KeyPairGenerator.getInstance("RSA");
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        kpg.initialize(2048);
        KeyPair rsaKeyPair = kpg.genKeyPair();
        PublicKey publicKey = rsaKeyPair.getPublic();
        String publicKeyAsString = Base64.getEncoder().encodeToString(publicKey.getEncoded());

        User user = userRepository.getUser();

        String keyHash = HashUtils.getSha512Fingerprint(publicKey);
        userRepository.updateUserSshPublicKeyHash(user.getId(), keyHash, publicKeyAsString);
        user = userRepository.getUser();

        assertThat(user.getSshPublicKey()).isEqualTo(publicKeyAsString);
        return rsaKeyPair;
    }
}
