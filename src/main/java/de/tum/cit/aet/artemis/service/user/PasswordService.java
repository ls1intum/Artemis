package de.tum.cit.aet.artemis.service.user;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.config.SecurityConfiguration;

/**
 * This service is a simple delegate to break the circular structure we would get when defining
 * a) {@link SecurityConfiguration#passwordEncoder()}
 * b) The passwordEncoder in every other class using password hashing or matching
 */
@Profile(PROFILE_CORE)
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
