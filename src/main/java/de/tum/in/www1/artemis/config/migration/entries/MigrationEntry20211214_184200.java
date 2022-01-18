package de.tum.in.www1.artemis.config.migration.entries;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.user.PasswordService;

@Component
public class MigrationEntry20211214_184200 extends MigrationEntry {

    private final UserRepository userRepository;

    private final PasswordService passwordService;

    public MigrationEntry20211214_184200(UserRepository userRepository, PasswordService passwordService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
    }

    @Override
    public void execute() {
        int listSize = 100;
        List<User> users = userRepository.findAll();
        // Cut list in parts to prevent any timeouts
        int remainder = users.size() % listSize;
        int listCount = (int) Math.floor(users.size() / 100f);
        for (int i = 0; i < listCount - 1; i++) {
            List<User> sublist = users.subList(i * listSize, (i + 1) * listSize);
            processUsers(sublist);
        }
        if (remainder > 0) {
            List<User> sublist = users.subList(listCount * listSize, (listCount * listSize) + remainder);
            processUsers(sublist);
        }
    }

    private void processUsers(List<User> userList) {
        userList = userList.stream().peek(user -> {
            String password = passwordService.decryptPassword(user);
            user.setInternal(!StringUtils.isEmpty(password));
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
