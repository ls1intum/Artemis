package de.tum.in.www1.artemis.web.rest.publicc;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.security.annotations.EnforceNothing;
import de.tum.in.www1.artemis.service.AccountService;
import de.tum.in.www1.artemis.service.MailService;
import de.tum.in.www1.artemis.service.dto.UserDTO;
import de.tum.in.www1.artemis.service.user.UserService;
import de.tum.in.www1.artemis.web.rest.errors.*;
import de.tum.in.www1.artemis.web.rest.vm.KeyAndPasswordVM;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;

/**
 * REST controller for public endpoints regarding the current user's account.
 */
@RestController
@RequestMapping("api/public/")
public class PublicAccountResource {

    private final Logger log = LoggerFactory.getLogger(PublicAccountResource.class);

    @Value("${artemis.user-management.registration.allowed-email-pattern:#{null}}")
    private Optional<Pattern> allowedEmailPattern;

    private final AccountService accountService;

    private final UserService userService;

    private final MailService mailService;

    private final UserRepository userRepository;

    public PublicAccountResource(AccountService accountService, UserService userService, MailService mailService, UserRepository userRepository) {
        this.accountService = accountService;
        this.userService = userService;
        this.mailService = mailService;
        this.userRepository = userRepository;
    }

    /**
     * {@code POST /register} : register the user.
     *
     * @param managedUserVM the managed user View Model.
     * @throws PasswordViolatesRequirementsException  {@code 400 (Bad Request)} if the password does not meet the requirements.
     * @throws EmailAlreadyUsedException {@code 400 (Bad Request)} if the email is already used.
     * @throws LoginAlreadyUsedException {@code 400 (Bad Request)} if the login is already used.
     */
    @PostMapping("register")
    @EnforceNothing
    @ResponseStatus(HttpStatus.CREATED)
    public void registerAccount(@Valid @RequestBody ManagedUserVM managedUserVM) {

        if (accountService.isRegistrationDisabled()) {
            throw new AccessForbiddenException("User Registration is disabled");
        }
        if (accountService.isPasswordLengthInvalid(managedUserVM.getPassword())) {
            throw new PasswordViolatesRequirementsException();
        }

        SecurityUtils.checkUsernameAndPasswordValidity(managedUserVM.getLogin(), managedUserVM.getPassword());

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
     * {@code GET /activate} : activate the registered user.
     *
     * @param key the activation key.
     * @throws RuntimeException {@code 500 (Internal Server Error)} if the user couldn't be activated.
     */
    @GetMapping("activate")
    @EnforceNothing
    public void activateAccount(@RequestParam("key") String key) {
        if (accountService.isRegistrationDisabled()) {
            throw new AccessForbiddenException("User Registration is disabled");
        }
        Optional<User> user = userService.activateRegistration(key);
        if (user.isEmpty()) {
            throw new InternalServerErrorException("No user was found for this activation key");
        }
    }

    /**
     * {@code GET /authenticate} : check if the user is authenticated, and return its login.
     *
     * @param request the HTTP request.
     * @return the login if the user is authenticated.
     */
    @GetMapping("authenticate")
    @EnforceNothing
    public ResponseEntity<String> authenticatedLogin(HttpServletRequest request) {
        log.debug("REST request to check if the current user is authenticated");
        return ResponseEntity.ok(request.getRemoteUser());
    }

    /**
     * {@code GET /account} : get the current user.
     *
     * @return the current user, empty if not logged in.
     * @throws EntityNotFoundException {@code 404 (User not found)} if the user couldn't be returned.
     */
    @GetMapping("account")
    @EnforceNothing
    public ResponseEntity<UserDTO> getAccount() {
        long start = System.currentTimeMillis();

        Optional<User> userOptional = Optional.empty();

        Optional<String> loginOptional = SecurityUtils.getCurrentUserLogin();
        if (loginOptional.isPresent()) {
            userOptional = userRepository.findOneWithGroupsAuthoritiesAndGuidedTourSettingsByLogin(loginOptional.get());
        }

        if (userOptional.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        User user = userOptional.get();
        user.setVisibleRegistrationNumber();
        UserDTO userDTO = new UserDTO(user);
        log.info("GET /account {} took {}ms", user.getLogin(), System.currentTimeMillis() - start);
        return ResponseEntity.ok(userDTO);
    }

    /**
     * {@code POST /account/change-language} : changes the current user's language key.
     *
     * @param languageKey languageKey to change to.
     * @throws BadRequestAlertException {@code 400 (Bad Request)} if the language key is not 'en' or 'de'.
     */
    @PostMapping("account/change-language")
    @EnforceNothing
    public void changeLanguageKey(@RequestBody String languageKey) {
        User user = userRepository.getUser();
        String langKey = languageKey.replaceAll("\"", "").toLowerCase().trim();
        if (!"en".equals(langKey) && !"de".equals(langKey)) {
            throw new BadRequestAlertException("Language key %s not supported!".formatted(languageKey), "Account", "invalidLanguageKey");
        }
        userService.updateUserLanguageKey(user.getId(), langKey);
    }

    /**
     * {@code POST /account/reset-password/init} : Send an email to reset the password of the user.
     *
     * @param mailUsername string containing either mail or username of the user.
     */
    @PostMapping("account/reset-password/init")
    @EnforceNothing
    public void requestPasswordReset(@RequestBody String mailUsername) {
        List<User> user = userRepository.findAllByEmailOrUsernameIgnoreCase(mailUsername);
        if (!user.isEmpty()) {
            List<User> internalUsers = user.stream().filter(User::isInternal).toList();
            if (internalUsers.isEmpty()) {
                throw new BadRequestAlertException("The user is handled externally. The password can't be reset within Artemis.", "Account", "externalUser");
            }
            else if (userService.prepareUserForPasswordReset(internalUsers.get(0))) {
                mailService.sendPasswordResetMail(internalUsers.get(0));
            }
        }
        else {
            // Pretend the request has been successful to prevent checking which emails or usernames really exist
            // but log that an invalid attempt has been made
            log.warn("Password reset requested for non-existing mail or username '{}'", mailUsername);
        }
    }

    /**
     * {@code POST /account/reset-password/finish} : Finish to reset the password of the user.
     *
     * @param keyAndPassword the generated key and the new password.
     * @throws PasswordViolatesRequirementsException {@code 400 (Bad Request)} if the password does not meet the requirements.
     * @throws RuntimeException         {@code 500 (Internal Server Error)} if the password could not be reset.
     */
    @PostMapping("account/reset-password/finish")
    @EnforceNothing
    public void finishPasswordReset(@RequestBody KeyAndPasswordVM keyAndPassword) {
        if (accountService.isPasswordLengthInvalid(keyAndPassword.getNewPassword())) {
            throw new PasswordViolatesRequirementsException();
        }
        Optional<User> user = userService.completePasswordReset(keyAndPassword.getNewPassword(), keyAndPassword.getKey());

        if (user.isEmpty()) {
            throw new AccessForbiddenException("No user was found for this reset key");
        }
    }
}
