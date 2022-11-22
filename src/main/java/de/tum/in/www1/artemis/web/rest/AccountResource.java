package de.tum.in.www1.artemis.web.rest;

import java.util.Optional;

import javax.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.AccountService;
import de.tum.in.www1.artemis.service.dto.PasswordChangeDTO;
import de.tum.in.www1.artemis.service.dto.UserDTO;
import de.tum.in.www1.artemis.service.user.UserCreationService;
import de.tum.in.www1.artemis.service.user.UserService;
import de.tum.in.www1.artemis.web.rest.errors.*;

/**
 * REST controller for managing the current user's account.
 */
@RestController
@RequestMapping("api/")
public class AccountResource {

    private final UserRepository userRepository;

    private final UserService userService;

    private final UserCreationService userCreationService;

    private final AccountService accountService;

    public AccountResource(UserRepository userRepository, UserService userService, UserCreationService userCreationService, AccountService accountService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.userCreationService = userCreationService;
        this.accountService = accountService;
    }

    /**
     * {@code PUT /account} : update the current user information.
     *
     * @param userDTO the current user information.
     * @throws EmailAlreadyUsedException {@code 400 (Bad Request)} if the email is already used.
     * @throws RuntimeException          {@code 500 (Internal Server Error)} if the user login wasn't found.
     */
    @PutMapping("account")
    @PreAuthorize("hasRole('USER')")
    public void saveAccount(@Valid @RequestBody UserDTO userDTO) {
        if (accountService.isRegistrationDisabled()) {
            throw new AccessForbiddenException("Can't edit user information as user registration is disabled");
        }

        final String userLogin = userRepository.getUser().getLogin();
        Optional<User> existingUser = userRepository.findOneByEmailIgnoreCase(userDTO.getEmail());
        if (existingUser.isPresent() && (!existingUser.get().getLogin().equalsIgnoreCase(userLogin))) {
            throw new EmailAlreadyUsedException();
        }

        userCreationService.updateBasicInformationOfCurrentUser(userDTO.getFirstName(), userDTO.getLastName(), userDTO.getEmail(), userDTO.getLangKey(), userDTO.getImageUrl());
    }

    /**
     * {@code POST /account/change-password} : changes the current user's password.
     *
     * @param passwordChangeDto current and new password.
     * @throws PasswordViolatesRequirementsException {@code 400 (Bad Request)} if the new password does not meet the requirements.
     */
    @PostMapping("account/change-password")
    @PreAuthorize("hasRole('USER')")
    public void changePassword(@RequestBody PasswordChangeDTO passwordChangeDto) {
        User user = userRepository.getUser();
        if (!user.isInternal()) {
            throw new AccessForbiddenException("Only users with internally saved credentials can change their password.");
        }
        if (accountService.isPasswordLengthInvalid(passwordChangeDto.getNewPassword())) {
            throw new PasswordViolatesRequirementsException();
        }
        userService.changePassword(passwordChangeDto.getCurrentPassword(), passwordChangeDto.getNewPassword());
    }
}
