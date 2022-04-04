package de.tum.in.www1.artemis.service.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.SecurityConfiguration;

/**
 * This service is a simple delegate to break the circular structure we would get when defining
 * a) {@link SecurityConfiguration#passwordEncoder()}
 * b) The passwordEncoder in every other class using password hashing or matching
 */
@Service
public class PasswordService {

    private final PasswordEncoder passwordEncoder;

    public PasswordService(@Value("${artemis.bcrypt-salt-rounds}") int bcryptSaltRounds) {
        this.passwordEncoder = new BCryptPasswordEncoder(bcryptSaltRounds);
    }

    public String hashPassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public boolean checkPasswordMatch(CharSequence rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    public PasswordEncoder getPasswordEncoder() {
        return this.passwordEncoder;
    }
}
