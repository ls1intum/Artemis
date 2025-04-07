package de.tum.cit.aet.artemis.programming.icl.util;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.programming.domain.UserSshPublicKey;
import de.tum.cit.aet.artemis.programming.dto.UserSshPublicKeyDTO;
import de.tum.cit.aet.artemis.programming.repository.UserSshPublicKeyRepository;

@Service
@Profile(SPRING_PROFILE_TEST)
public class SshSettingsTestService {

    @Autowired
    private UserTestRepository userTestRepository;

    @Autowired
    protected RequestUtilService request;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private UserSshPublicKeyRepository userSshPublicKeyRepository;

    private final String requestPrefix = "/api/programming/ssh-settings/";

    private static final String sshKey1 = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIJxKWdvcbNTWl4vBjsijoY5HN5dpjxU40huy1PFpdd2o keyComment1 many comments";

    private static final String sshKey2 = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIEbgjoSpKnry5yuMiWh/uwhMG2Jq5Sh8Uw9vz+39or2i";

    User student;

    public void setup(String testPrefix) throws Exception {
        userUtilService.addUsers(testPrefix, 1, 1, 1, 1);
        student = userTestRepository.getUserByLoginElseThrow(testPrefix + "student1");
        student.setInternal(true);
        userTestRepository.save(student);
    }

    public void tearDown(String testPrefix) throws IOException {
        userSshPublicKeyRepository.deleteAll();
        userTestRepository.deleteAll(userTestRepository.searchAllByLoginOrName(Pageable.unpaged(), testPrefix));
    }

    // Test
    public void getUserSshPublicKeys() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        User user = userTestRepository.getUser();

        var validKey = createNewValidSSHKey(user, sshKey1);
        request.postWithResponseBody(requestPrefix + "public-key", validKey, String.class, HttpStatus.OK);

        List<UserSshPublicKey> response = request.getList(requestPrefix + "public-keys", HttpStatus.OK, UserSshPublicKey.class);
        assertThat(response.size()).isEqualTo(1);
        UserSshPublicKey userKey = response.getFirst();

        request.get(requestPrefix + "public-key/" + userKey.getId(), HttpStatus.OK, UserSshPublicKey.class);
    }

    // Test
    public void addUserSshPublicKey() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        User user = userTestRepository.getUser();

        var validKey = createNewValidSSHKey(user, sshKey1);
        request.postWithResponseBody(requestPrefix + "public-key", validKey, String.class, HttpStatus.OK);

        var storedUserKey = userSshPublicKeyRepository.findAllByUserId(user.getId()).getFirst();
        assertThat(storedUserKey).isNotNull();
        assertThat(storedUserKey.getPublicKey()).isEqualTo(validKey.getPublicKey());
    }

    // Test
    public void addUserSshPublicKeyWithOutLabel() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        User user = userTestRepository.getUser();

        var validKey = createNewValidSSHKey(user, sshKey1);
        validKey.setLabel(null);
        request.postWithResponseBody(requestPrefix + "public-key", validKey, String.class, HttpStatus.OK);

        var validKey2 = createNewValidSSHKey(user, sshKey2);
        validKey.setLabel("");
        request.postWithResponseBody(requestPrefix + "public-key", validKey2, String.class, HttpStatus.OK);

        var storedUserKeys = userSshPublicKeyRepository.findAllByUserId(user.getId());
        assertThat(storedUserKeys.size()).isEqualTo(2);
    }

    // Test
    public void failToAddPublicSSHkeyTwice() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        User user = userTestRepository.getUser();

        var validKey = createNewValidSSHKey(user, sshKey1);

        request.postWithResponseBody(requestPrefix + "public-key", validKey, String.class, HttpStatus.OK);
        request.postWithResponseBody(requestPrefix + "public-key", validKey, String.class, HttpStatus.BAD_REQUEST);
    }

    // Test
    public void failToAddOrDeleteWithInvalidKeyId() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        User user = userTestRepository.getUser();

        var validKey = createNewValidSSHKey(user, sshKey1);
        request.postWithResponseBody(requestPrefix + "public-key", validKey, String.class, HttpStatus.OK);

        request.delete(requestPrefix + "public-key/3443", HttpStatus.FORBIDDEN);
        request.get(requestPrefix + "public-key/43443", HttpStatus.FORBIDDEN, UserSshPublicKeyDTO.class);
    }

    // Test
    public void failToAddInvalidPublicSSHkey() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);

        User user = userTestRepository.getUser();
        var userKey = createNewValidSSHKey(user, sshKey1);
        userKey.setPublicKey("Invalid Key");

        request.postWithResponseBody(requestPrefix + "public-key", userKey, String.class, HttpStatus.BAD_REQUEST);
    }

    // Test
    public void addAndDeleteSshPublicKey() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        User user = userTestRepository.getUser();

        var validKey = createNewValidSSHKey(user, sshKey1);

        request.postWithResponseBody(requestPrefix + "public-key", validKey, String.class, HttpStatus.OK);

        var storedUserKey = userSshPublicKeyRepository.findAllByUserId(user.getId()).getFirst();
        assertThat(storedUserKey).isNotNull();
        assertThat(storedUserKey.getPublicKey()).isEqualTo(validKey.getPublicKey());

        // deleting the key should work correctly
        request.delete(requestPrefix + "public-key/" + storedUserKey.getId(), HttpStatus.OK);
        assertThat(userSshPublicKeyRepository.findAllByUserId(user.getId())).isEmpty();
    }

    public static UserSshPublicKey createNewValidSSHKey(User user, String keyString) {
        UserSshPublicKey userSshPublicKey = new UserSshPublicKey();
        userSshPublicKey.setPublicKey(keyString);
        userSshPublicKey.setLabel("Key 1");
        userSshPublicKey.setUserId(user.getId());
        return userSshPublicKey;
    }
}
