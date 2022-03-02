package de.tum.in.www1.artemis.security;

import org.jasypt.encryption.pbe.PBEStringEncryptor;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * PBEPasswordEncoder for Spring, based on org.jasypt.spring.security3.PBEPasswordEncoder;
 */
@Deprecated
public class PBEPasswordEncoder implements PasswordEncoder {

    private final PBEStringEncryptor pbeStringEncryptor;

    public PBEPasswordEncoder(final PBEStringEncryptor pbeStringEncryptor) {
        this.pbeStringEncryptor = pbeStringEncryptor;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return this.pbeStringEncryptor.encrypt(rawPassword.toString());
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        String decPassword = this.pbeStringEncryptor.decrypt(encodedPassword);

        if (decPassword == null || rawPassword == null) {
            return (decPassword == rawPassword);
        }
        return decPassword.equals(rawPassword.toString());
    }
}
