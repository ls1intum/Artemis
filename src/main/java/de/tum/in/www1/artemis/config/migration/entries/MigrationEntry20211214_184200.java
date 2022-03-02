package de.tum.in.www1.artemis.config.migration.entries;

import java.util.List;

import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.user.LegacyPasswordService;

/**
 * This migration separates the users into internal and external users and sets the newly created attribute isInternal in {@link User}
 */
@Component
public class MigrationEntry20211214_184200 extends MigrationEntry {

    private final UserRepository userRepository;

    private final LegacyPasswordService passwordService;

    public MigrationEntry20211214_184200(UserRepository userRepository, LegacyPasswordService passwordService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
    }

    /**
     * Retrieves all users from the database and executes the processing method in batches of a 100 users to prevent database timeouts
     */
    @Override
    public void execute() {
        int listSize = 100;
        List<User> users = userRepository.findAll();
        int remainder = users.size() % listSize;
        int listCount = (int) Math.floor(users.size() / 100f);
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
            String encryptedPassword = user.getPassword();
            if (encryptedPassword == null || encryptedPassword.matches("^\\$2[abxy]\\$\\d{2}\\$.*$")) {
                user.setInternal(false);
                user.setPassword(passwordService.encryptPassword(""));
            }
            else {
                String decryptedPassword = passwordService.decryptPassword(user);
                user.setInternal(!decryptedPassword.isEmpty());
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
