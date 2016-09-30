package de.tum.in.www1.exerciseapp.security;

import org.jasypt.encryption.pbe.PBEStringEncryptor;
import org.jasypt.exceptions.EncryptionInitializationException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Created by Josias Montag on 23.09.16.
 *
 * PBEPasswordEncoder for Spring 4, based on org.jasypt.spring.security3.PBEPasswordEncoder;
 *
 */
public class PBEPasswordEncoder implements PasswordEncoder {

    public PBEPasswordEncoder() {
        super();
    }

    private PBEStringEncryptor pbeStringEncryptor = null;

    public void setPbeStringEncryptor(final PBEStringEncryptor pbeStringEncryptor) {
        this.pbeStringEncryptor = pbeStringEncryptor;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return this.pbeStringEncryptor.encrypt(rawPassword.toString());
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        checkInitialization();
        String decPassword = null;

        decPassword = this.pbeStringEncryptor.decrypt(encodedPassword);

        if ((decPassword == null) || (rawPassword == null)) {
            return (decPassword == rawPassword.toString());
        }
        return decPassword.equals(rawPassword.toString());
    }


    private synchronized void checkInitialization() {
        if (this.pbeStringEncryptor == null) {
            throw new EncryptionInitializationException(
                "PBE Password encoder not initialized: PBE " +
                    "string encryptor is null");
        }
    }
}




