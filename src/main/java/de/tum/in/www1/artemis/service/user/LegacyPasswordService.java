package de.tum.in.www1.artemis.service.user;

import java.util.Optional;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.PBEPasswordEncoder;

@Service
@Deprecated
public class LegacyPasswordService {

    @Value("${artemis.encryption-password}")
    private String encryptionPassword;

    private final UserRepository userRepository;

    private PBEPasswordEncoder passwordEncoder;

    private StandardPBEStringEncryptor encryptor;

    public LegacyPasswordService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Get the encoder for password encryption
     *
     * @return existing password encoder or newly created password encryptor
     */
    private PBEPasswordEncoder passwordEncoder() {
        if (passwordEncoder != null) {
            return passwordEncoder;
        }
        passwordEncoder = new PBEPasswordEncoder(encryptor());
        return passwordEncoder;
    }

    /**
     * Get the password encryptor with MD5 and DES encryption algorithm
     *
     * @return existing encryptor or newly created encryptor
     */
    private StandardPBEStringEncryptor encryptor() {
        if (encryptor != null) {
            return encryptor;
        }
        encryptor = new StandardPBEStringEncryptor();
        encryptor.setAlgorithm("PBEWithMD5AndDES");
        encryptor.setPassword(encryptionPassword);
        return encryptor;
    }

    /**
     * Get decrypted password for given user
     *
     * @param user the user
     * @return decrypted password or empty string
     */
    public String decryptPassword(User user) {
        return encryptor().decrypt(user.getPassword());
    }

    public String decryptPassword(String encodedPassword) {
        return encryptor().decrypt(encodedPassword);
    }

    public String encryptPassword(String rawPassword) {
        return encryptor().encrypt(rawPassword);
    }

    public String encodePassword(CharSequence rawPassword) {
        return passwordEncoder().encode(rawPassword);
    }

    public boolean checkPasswordMatch(CharSequence rawPassword, String encodedPassword) {
        return passwordEncoder().matches(rawPassword, encodedPassword);
    }

    /**
     * Get decrypted password for given user login
     *
     * @param login of a user
     * @return decrypted password or empty string
     */
    public Optional<String> decryptPasswordByLogin(String login) {
        return userRepository.findOneByLogin(login).map(user -> encryptor().decrypt(user.getPassword()));
    }
}
