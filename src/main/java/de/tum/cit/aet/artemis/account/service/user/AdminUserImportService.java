package de.tum.cit.aet.artemis.account.service.user;

import static de.tum.cit.aet.artemis.account.domain.User.IRIS_BOT_LOGIN;
import static de.tum.cit.aet.artemis.core.config.Constants.DEFAULT_LANGUAGE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.dto.StudentDTO;
import de.tum.cit.aet.artemis.core.dto.UserImportDTO;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class AdminUserImportService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserImportService.class);

    private final UserService userService;

    private final UserCreationService userCreationService;

    private final UserRepository userRepository;

    public AdminUserImportService(UserService userService, UserCreationService userCreationService, UserRepository userRepository) {
        this.userService = userService;
        this.userCreationService = userCreationService;
        this.userRepository = userRepository;
    }

    /**
     * Imports users into the admin user management. Existing users are resolved through the usual database/LDAP import
     * path. Missing users are only created as internal users when requested.
     *
     * @param userDtos            users to import
     * @param createInternalUsers whether unresolved users should be created as internal users
     * @return password-free users that could not be imported
     */
    public List<StudentDTO> importUsers(List<UserImportDTO> userDtos, boolean createInternalUsers) {
        if (!createInternalUsers) {
            return userService.importUsers(toStudentDTOs(userDtos));
        }

        List<StudentDTO> notImportedUsers = new ArrayList<>();
        for (var userDto : userDtos) {
            StudentDTO studentDTO = userDto.toStudentDTO();
            if (userService.importUsers(List.of(studentDTO)).isEmpty()) {
                continue;
            }
            try {
                createInternalUserFromImport(userDto);
            }
            catch (RuntimeException ex) {
                log.warn("Could not create internal user from CSV import for login '{}', email '{}': {}", userDto.login(), userDto.email(), ex.getMessage());
                notImportedUsers.add(studentDTO);
            }
        }
        return notImportedUsers;
    }

    private List<StudentDTO> toStudentDTOs(List<UserImportDTO> userDtos) {
        return userDtos.stream().map(UserImportDTO::toStudentDTO).toList();
    }

    private void createInternalUserFromImport(UserImportDTO userDto) {
        if (!StringUtils.hasText(userDto.login())) {
            throw new IllegalArgumentException("Login is required to create an internal user");
        }
        String login = userDto.login().toLowerCase(Locale.ROOT);
        if (IRIS_BOT_LOGIN.equals(login)) {
            throw new IllegalArgumentException("The login '" + IRIS_BOT_LOGIN + "' is reserved and cannot be used.");
        }

        String password = StringUtils.hasText(userDto.password()) ? userDto.password() : null;
        userService.checkUsernameAndPasswordValidityElseThrow(login, password);

        if (userRepository.findOneByLogin(login).isPresent()) {
            throw new IllegalStateException("Login already in use: " + login);
        }
        if (StringUtils.hasText(userDto.email()) && userRepository.findOneByEmailIgnoreCase(userDto.email()).isPresent()) {
            throw new IllegalStateException("Email already in use: " + userDto.email());
        }

        String email = StringUtils.hasText(userDto.email()) ? userDto.email().toLowerCase(Locale.ROOT) : null;
        User newUser = userCreationService.createUser(login, password, null, userDto.firstName(), userDto.lastName(), email, userDto.registrationNumber(), null, DEFAULT_LANGUAGE,
                true);
        userCreationService.activateUser(newUser);
    }
}
