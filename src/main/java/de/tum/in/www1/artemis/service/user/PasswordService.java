package de.tum.in.www1.artemis.service.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {

    @Value("${artemis.bcrypt-salt-rounds}")
    int bcryptSaltRounds;

    public String hashPassword(String rawPassword) {
        String salt = BCrypt.gensalt(bcryptSaltRounds);
        return BCrypt.hashpw(rawPassword, salt);
    }

    public boolean checkPasswordMatch(CharSequence rawPassword, String encodedPassword) {
        return BCrypt.checkpw(rawPassword.toString(), encodedPassword);
    }
}
