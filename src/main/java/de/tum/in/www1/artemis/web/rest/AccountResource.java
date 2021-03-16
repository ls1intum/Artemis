package de.tum.in.www1.artemis.web.rest;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.MailService;
import de.tum.in.www1.artemis.service.dto.PasswordChangeDTO;
import de.tum.in.www1.artemis.service.dto.UserDTO;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.service.user.UserCreationService;
import de.tum.in.www1.artemis.service.user.UserService;
import de.tum.in.www1.artemis.web.rest.errors.*;
import de.tum.in.www1.artemis.web.rest.vm.KeyAndPasswordVM;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;

/**
 * REST controller for managing the current user's account.
 */
@RestController
@RequestMapping("/api")
public class AccountResource {

    @Value("${artemis.user-management.registration.enabled:#{null}}")
    private Optional<Boolean> registrationEnabled;

    @Value("${artemis.user-management.registration.allowed-email-pattern:#{null}}")
    private Optional<Pattern> allowedEmailPattern;

    @Value("${info.saml2.enable-password:#{null}}")
    private Optional<Boolean> saml2EnablePassword;

    private static class AccountResourceException extends RuntimeException {

        private AccountResourceException(String message) {
            super(message);
        }
    }

    private final Logger log = LoggerFactory.getLogger(AccountResource.class);

    private final UserRepository userRepository;

    private final UserService userService;

    private final UserCreationService userCreationService;

    private final PasswordService passwordService;

    private final MailService mailService;

    public AccountResource(UserRepository userRepository, UserService userService, UserCreationService userCreationService, MailService mailService,
            PasswordService passwordService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.userCreationService = userCreationService;
        this.mailService = mailService;
        this.passwordService = passwordService;
    }

    /**
     * the registration is only enabled when the configuration artemis.user-management.registration.enabled is set to true.
     * A non existing entry or false mean that the registration is not enabled
     * @return whether the registration is enabled or not
     */
    private boolean isRegistrationDisabled() {
        return registrationEnabled.isEmpty() || Boolean.FALSE.equals(registrationEnabled.get());
    }

    /**
     * Returns true if saml2 app passwort is disabled, false otherwis.
     *
     * @return true if saml2 app passwort is disabled, false otherwise
     */
    private boolean isSAML2Disabled() {
        return !(saml2EnablePassword.isPresent() && Boolean.TRUE.equals(saml2EnablePassword.get()));
    }

    /**
     * {@code POST  /register} : register the user.
     *
     * @param managedUserVM the managed user View Model.
     * @throws InvalidPasswordException {@code 400 (Bad Request)} if the password is incorrect.
     * @throws EmailAlreadyUsedException {@code 400 (Bad Request)} if the email is already used.
     * @throws LoginAlreadyUsedException {@code 400 (Bad Request)} if the login is already used.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void registerAccount(@Valid @RequestBody ManagedUserVM managedUserVM) {
        if (isRegistrationDisabled()) {
            throw new AccessForbiddenException("User Registration is disabled");
        }
        if (isPasswordLengthInvalid(managedUserVM.getPassword())) {
            throw new InvalidPasswordException();
        }

        if (allowedEmailPattern.isPresent()) {
            Matcher emailMatcher = allowedEmailPattern.get().matcher(managedUserVM.getEmail());
            if (!emailMatcher.matches()) {
                throw new BadRequestAlertException("The provided email is invalid and does not follow the specified pattern", "Account", "emailInvalid");
            }
        }

        User user = userService.registerUser(managedUserVM, managedUserVM.getPassword());
        mailService.sendActivationEmail(user);
    }

    /**
     * {@code GET  /activate} : activate the registered user.
     *
     * @param key the activation key.
     * @throws RuntimeException {@code 500 (Internal Server Error)} if the user couldn't be activated.
     */
    @GetMapping("/activate")
    public void activateAccount(@RequestParam(value = "key") String key) {
        if (isRegistrationDisabled()) {
            throw new AccessForbiddenException("User Registration is disabled");
        }
        Optional<User> user = userService.activateRegistration(key);
        if (user.isEmpty()) {
            throw new InternalServerErrorException("No user was found for this activation key");
        }
    }

    /**
     * {@code GET  /authenticate} : check if the user is authenticated, and return its login.
     *
     * @param request the HTTP request.
     * @return the login if the user is authenticated.
     */
    @GetMapping("/authenticate")
    public String isAuthenticated(HttpServletRequest request) {
        log.debug("REST request to check if the current user is authenticated");
        return request.getRemoteUser();
    }

    /**
     * {@code GET  /account} : get the current user.
     *
     * @return the current user.
     * @throws EntityNotFoundException {@code 404 (User not found)} if the user couldn't be returned.
     */
    @GetMapping("/account")
    public UserDTO getAccount() {
        long start = System.currentTimeMillis();
        User user = userRepository.getUserWithGroupsAuthoritiesAndGuidedTourSettings();
        UserDTO userDTO = new UserDTO(user);
        log.info("GET /account " + user.getLogin() + " took " + (System.currentTimeMillis() - start) + "ms");
        return userDTO;
    }

    /**
     * GET /account/password : get the current users password.
     *
     * @return the ResponseEntity with status 200 (OK) and the current user password in body, or status 500 (Internal Server Error) if the user couldn't be returned
     */
    @GetMapping(value = "/account/password", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getPassword() {
        // This method is used to show the password for users that have been generated automatically based on LTI
        // It only allows to decrypt and return the password of internal users and only of the currently logged in user
        Map<String, String> body = new HashMap<>();
        body.put("password", passwordService.decryptPasswordOfCurrentUser());
        return new ResponseEntity<>(body, HttpStatus.OK);
    }

    /**
     * {@code PUT  /account} : update the current user information.
     *
     * @param userDTO the current user information.
     * @throws EmailAlreadyUsedException {@code 400 (Bad Request)} if the email is already used.
     * @throws RuntimeException {@code 500 (Internal Server Error)} if the user login wasn't found.
     */
    @PutMapping("/account")
    public void saveAccount(@Valid @RequestBody UserDTO userDTO) {
        if (isRegistrationDisabled()) {
            throw new AccessForbiddenException("User Registration is disabled");
        }
        final String userLogin = SecurityUtils.getCurrentUserLogin().orElseThrow(() -> new InternalServerErrorException("Current user login not found"));
        Optional<User> existingUser = userRepository.findOneByEmailIgnoreCase(userDTO.getEmail());
        if (existingUser.isPresent() && (!existingUser.get().getLogin().equalsIgnoreCase(userLogin))) {
            throw new EmailAlreadyUsedException();
        }
        Optional<User> user = userRepository.findOneByLogin(userLogin);
        if (user.isEmpty()) {
            throw new InternalServerErrorException("User could not be found");
        }
        userCreationService.updateBasicInformationOfCurrentUser(userDTO.getFirstName(), userDTO.getLastName(), userDTO.getEmail(), userDTO.getLangKey(), userDTO.getImageUrl());
    }

    /**
     * {@code POST  /account/change-password} : changes the current user's password.
     *
     * @param passwordChangeDto current and new password.
     * @throws InvalidPasswordException {@code 400 (Bad Request)} if the new password is incorrect.
     */
    @PostMapping(path = "/account/change-password")
    public void changePassword(@RequestBody PasswordChangeDTO passwordChangeDto) {
        if (isRegistrationDisabled() && isSAML2Disabled()) {
            throw new AccessForbiddenException("User Registration is disabled");
        }
        if (isPasswordLengthInvalid(passwordChangeDto.getNewPassword())) {
            throw new InvalidPasswordException();
        }
        userService.changePassword(passwordChangeDto.getCurrentPassword(), passwordChangeDto.getNewPassword());
    }

    /**
     * {@code POST   /account/reset-password/init} : Send an email to reset the password of the user.
     *
     * @param mail the mail of the user.
     */
    @PostMapping(path = "/account/reset-password/init")
    public void requestPasswordReset(@RequestBody String mail) {
        if (isRegistrationDisabled() && isSAML2Disabled()) {
            throw new AccessForbiddenException("User Registration is disabled");
        }
        Optional<User> user = userService.requestPasswordReset(mail);
        if (user.isPresent()) {
            mailService.sendPasswordResetMail(user.get());
        }
        else {
            // Pretend the request has been successful to prevent checking which emails really exist
            // but log that an invalid attempt has been made
            log.warn("Password reset requested for non existing mail '{}'", mail);
        }
    }

    /**
     * {@code POST   /account/reset-password/finish} : Finish to reset the password of the user.
     *
     * @param keyAndPassword the generated key and the new password.
     * @throws InvalidPasswordException {@code 400 (Bad Request)} if the password is incorrect.
     * @throws RuntimeException {@code 500 (Internal Server Error)} if the password could not be reset.
     */
    @PostMapping(path = "/account/reset-password/finish")
    public void finishPasswordReset(@RequestBody KeyAndPasswordVM keyAndPassword) {
        if (isRegistrationDisabled() && isSAML2Disabled()) {
            throw new AccessForbiddenException("User Registration is disabled");
        }
        if (isPasswordLengthInvalid(keyAndPassword.getNewPassword())) {
            throw new InvalidPasswordException();
        }
        Optional<User> user = userService.completePasswordReset(keyAndPassword.getNewPassword(), keyAndPassword.getKey());

        if (user.isEmpty()) {
            throw new AccountResourceException("No user was found for this reset key");
        }
    }

    private static boolean isPasswordLengthInvalid(String password) {
        return StringUtils.isEmpty(password) || password.length() < ManagedUserVM.PASSWORD_MIN_LENGTH || password.length() > ManagedUserVM.PASSWORD_MAX_LENGTH;
    }
}
