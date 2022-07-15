package de.tum.in.www1.artemis.config.migration.entries;

import java.util.List;

import org.jasypt.exceptions.EncryptionOperationNotPossibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.user.LegacyPasswordService;

/**
 * This migration separates the users into internal and external users and sets the newly created attribute isInternal in {@link User}
 */
@Component
public class MigrationEntry20211214_184200 extends MigrationEntry {

    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationEntry20211214_184200.class);

    private final UserRepository userRepository;

    private final LegacyPasswordService passwordService;

    public MigrationEntry20211214_184200(UserRepository userRepository, LegacyPasswordService passwordService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
    }

    /**
     * Retrieves all users from the database and executes the processing method in batches of 100 users to prevent database timeouts
     */
    @Override
    public void execute() {
        SecurityUtils.setAuthorizationObject();
        int listSize = 100;
        // false is the default value so if they were already set to true, this migration probably runs on a fresh system
        List<User> users = userRepository.findAllByInternal(false);
        LOGGER.info("Found {} users to process with `User.isInternal=false`.", users.size());
        int remainder = users.size() % listSize;
        int listCount = users.size() / listSize;
        for (int i = 0; i < listCount; i++) {
            List<User> sublist = users.subList(i * listSize, (i + 1) * listSize);
            processUsers(sublist);
        }
        if (remainder > 0) {
            List<User> sublist = users.subList(listCount * listSize, (listCount * listSize) + remainder);
            processUsers(sublist);
        }
    }

    /**
     * Sets a user internal if the password is decryptable and is not empty. Otherwise, the user is external.
     * If the user is external, the password will be set to an empty encrypted password as a default. Additionally, password reset fields get set to default.
     *
     * @param userList a batch of at max 100 users to be processed
     */
    private void processUsers(List<User> userList) {
        userList = userList.stream().peek(user -> {
            // This user is either already external or a user with a default `isInternal` value
            String encryptedPassword = user.getPassword();
            // Users without a password or with a Bcrypt password that are set to isInternal=false have to be old users => Cleanup
            // Keep in mind that we already don't have any proper internal users at this stage
            if (encryptedPassword == null || encryptedPassword.matches("^\\$2[abxy]\\$\\d{2}\\$.*$")) {
                user.setInternal(false);
                user.setPassword(passwordService.encryptPassword(""));
            }
            else {
                try {
                    String decryptedPassword = passwordService.decryptPassword(user);
                    user.setInternal(!decryptedPassword.isEmpty());
                }
                catch (EncryptionOperationNotPossibleException e) {
                    // Broken encryption, fall back to cleanup (see above)
                    user.setInternal(false);
                    user.setPassword(passwordService.encryptPassword(""));
                }
            }
            if (!user.isInternal()) {
                user.setResetDate(null);
                user.setResetKey(null);
            }
        }).toList();

        userRepository.saveAll(userList);
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
        return "20211214_184200";
    }
}
