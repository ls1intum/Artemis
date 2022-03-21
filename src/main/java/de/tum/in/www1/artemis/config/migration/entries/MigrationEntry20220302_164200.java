package de.tum.in.www1.artemis.config.migration.entries;

import java.util.List;

import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.user.LegacyPasswordService;
import de.tum.in.www1.artemis.service.user.PasswordService;

@Component
public class MigrationEntry20220302_164200 extends MigrationEntry {

    private final UserRepository userRepository;

    private final LegacyPasswordService legacyPasswordService;

    private final PasswordService passwordService;

    public MigrationEntry20220302_164200(UserRepository userRepository, LegacyPasswordService legacyPasswordService, PasswordService passwordService) {
        this.userRepository = userRepository;
        this.legacyPasswordService = legacyPasswordService;
        this.passwordService = passwordService;
    }

    @Override
    public void execute() {
        List<User> users = userRepository.findAll();
        Lists.partition(users, 100).forEach(list -> {
            list.forEach(this::processUser);
            userRepository.saveAll(list);
        });
    }

    private void processUser(User user) {
        String password = legacyPasswordService.decryptPassword(user);

        // Set internal state again due to an issue with setting the correct status during registration (PR #4806)
        // As the passwords of all external users were set to the encrypted empty string in Migration Entry 20211214_184200
        user.setInternal(!"".equals(password));

        if (user.isInternal()) {
            String hash = passwordService.hashPassword(password);
            user.setPassword(hash);
        }
        else {
            user.setPassword(null);
        }
    }

    /**
     * @return Author of the entry. Either full name or GitHub name.
     */
    @Override
    public String author() {
        return "julian-christl";
    }

    /**
     * Format YYYYMMDD_HHmmss
     *
     * @return Current time in given format
     */
    @Override
    public String date() {
        return "20220302_164200";
    }
}
