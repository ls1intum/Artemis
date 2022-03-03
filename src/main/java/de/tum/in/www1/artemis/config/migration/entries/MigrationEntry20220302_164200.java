package de.tum.in.www1.artemis.config.migration.entries;

import java.util.List;

import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
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
        SecurityUtils.setAuthorizationObject();
        int listSize = 100;
        List<User> users = userRepository.findAllByInternal(true);
        // Cut list in parts to prevent any timeouts
        int remainder = users.size() % listSize;
        int listCount = (int) Math.floor(users.size() / 100f);
        for (int i = 0; i < listCount - 1; i++) {
            List<User> sublist = users.subList(i * listSize, (i + 1) * listSize);
            processInternalUsers(sublist);
        }
        if (remainder > 0) {
            List<User> sublist = users.subList(listCount * listSize, (listCount * listSize) + remainder);
            processInternalUsers(sublist);
        }

        processExternalUsers();
    }

    private void processInternalUsers(List<User> userList) {
        userList = userList.stream().peek(user -> {
            String password = legacyPasswordService.decryptPassword(user);
            String hash = passwordService.hashPassword(password);
            user.setPassword(hash);
        }).toList();

        userRepository.saveAll(userList);
    }

    private void processExternalUsers() {
        List<User> userList = userRepository.findAllByInternal(false);
        userList = userList.stream().peek(user -> {
            user.setPassword(null);
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
        return "20220302_164200";
    }
}
