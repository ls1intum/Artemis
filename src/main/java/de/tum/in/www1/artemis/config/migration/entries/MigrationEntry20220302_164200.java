package de.tum.in.www1.artemis.config.migration.entries;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.user.LegacyPasswordService;
import de.tum.in.www1.artemis.service.user.PasswordService;

/**
 * This migration does the following for all internal users:
 * 1) Decrypt the password in the database using the legacy password service (which will not be used anymore in the future)
 * 2) Hashes the password with the new BCryptPasswordEncoder
 */
@Component
public class MigrationEntry20220302_164200 extends MigrationEntry {

    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationEntry20220302_164200.class);

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
        LOGGER.info("Found {} users in total.", users.size());
        LOGGER.info("Found {} internal users in total for the password migration (which are likely to be processed).", users.stream().filter(User::isInternal).count());
        Lists.partition(users, 100).forEach(userList -> {
            LOGGER.info("Process (next) 100 users for the migration in one batch...");
            userList.forEach(this::processUser);
            userRepository.saveAll(userList);
        });
    }

    private void processUser(User user) {
        if (user.getPassword() == null) {
            // If the password is null at this point, it has to be a proper external user we don't need to handle anymore.
            // Just to be sure, we set the correct internal state
            user.setInternal(false);
            return;
        }
        // The admin gets created beforehand if the account doesn't exist yet. This would be captured here.
        if (user.getPassword().matches("^\\$2[abxy]\\$\\d{2}\\$.*$")) {
            // If the password is a Bcrypt password at this point, it has to be a proper internal user we don't need to handle anymore.
            // Just to be sure, we set the correct internal state
            user.setInternal(true);
            return;
        }

        LOGGER.info("Process internal user {} for password migration", user.getLogin());
        // In a previous migration we encrypted all relevant password so we don't need a fallback here
        String password = legacyPasswordService.decryptPassword(user);

        if (!user.isInternal()) {
            // Set internal state again due to an issue with setting the correct status during registration (PR #4806)
            // As the passwords of all external users were set to the encrypted empty string in Migration Entry 20211214_184200
            user.setInternal(!"".equals(password));
        }

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
