package de.tum.in.www1.artemis.config.migration.entries;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.user.PasswordService;

@Component
public class MigrationEntry20211214_184200 extends MigrationEntry {

    private final UserRepository userRepository;

    private final PasswordService passwordService;

    @Value("${artemis.lti.user-group-name-edx:#{null}}")
    private String USER_GROUP_NAME_EDX;

    @Value("${artemis.lti.user-group-name-u4i:#{null}}")
    private String USER_GROUP_NAME_U4I;

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
            setInternalStatus(sublist);
        }
        if (remainder > 0) {
            List<User> sublist = users.subList(listCount * listSize, (listCount * listSize) + remainder);
            setInternalStatus(sublist);
        }
    }

    private void setInternalStatus(List<User> userList) {
        userList = userList.stream().peek(user -> {
            String password = passwordService.decryptPassword(user);
            user.setInternal(!password.isEmpty());
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
