package de.tum.cit.aet.artemis.web.rest.open;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.repository.UserRepository;
import de.tum.cit.aet.artemis.security.SecurityUtils;
import de.tum.cit.aet.artemis.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.service.AccountService;
import de.tum.cit.aet.artemis.service.dto.UserDTO;
import de.tum.cit.aet.artemis.service.notifications.MailService;
import de.tum.cit.aet.artemis.service.user.UserService;
import de.tum.cit.aet.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.errors.EmailAlreadyUsedException;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.cit.aet.artemis.web.rest.errors.InternalServerErrorException;
import de.tum.cit.aet.artemis.web.rest.errors.LoginAlreadyUsedException;
import de.tum.cit.aet.artemis.web.rest.errors.PasswordViolatesRequirementsException;
import de.tum.cit.aet.artemis.web.rest.vm.KeyAndPasswordVM;
import de.tum.cit.aet.artemis.web.rest.vm.ManagedUserVM;

/**
 * REST controller for public endpoints regarding the current user's account.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/public/")
public class PublicAccountResource {

    private static final Logger log = LoggerFactory.getLogger(PublicAccountResource.class);

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
     * @return ResponseEntity with status 201 (Created) if the user is registered.
     * @throws PasswordViolatesRequirementsException {@code 400 (Bad Request)} if the password does not meet the requirements.
     * @throws EmailAlreadyUsedException             {@code 400 (Bad Request)} if the email is already used.
     * @throws LoginAlreadyUsedException             {@code 400 (Bad Request)} if the login is already used.
     */
    @PostMapping("register")
    @EnforceNothing
    public ResponseEntity<Void> registerAccount(@Valid @RequestBody ManagedUserVM managedUserVM) throws URISyntaxException {

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
        return ResponseEntity.created(new URI("/api/register/" + user.getId())).build();
    }

    /**
     * {@code GET /activate} : activate the registered user.
     *
     * @param key the activation key.
     * @return ResponseEntity with status 200 (OK)
     * @throws RuntimeException {@code 500 (Internal Server Error)} if the user couldn't be activated.
     */
    @GetMapping("activate")
    @EnforceNothing
    public ResponseEntity<Void> activateAccount(@RequestParam("key") String key) {
        if (accountService.isRegistrationDisabled()) {
            throw new AccessForbiddenException("User Registration is disabled");
        }
        Optional<User> user = userService.activateRegistration(key);
        if (user.isEmpty()) {
            throw new InternalServerErrorException("No user was found for this activation key");
        }
        return ResponseEntity.ok().build();
    }

    /**
     * {@code GET /authenticate} : check if the user is authenticated, and return its login.
     *
     * @param request the HTTP request.
     * @return the ResponseEntity with status 200 (OK) and with body the login if the user is authenticated.
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
     * @return the ResponseEntity with status 200 (OK) and with body the current user, empty if not logged in.
     * @throws EntityNotFoundException {@code 404 (User not found)} if the user couldn't be returned.
     */
    @GetMapping("account")
    @EnforceNothing
    public ResponseEntity<UserDTO> getAccount() {
        long start = System.currentTimeMillis();

        Optional<User> userOptional = Optional.empty();

        Optional<String> loginOptional = SecurityUtils.getCurrentUserLogin();
        if (loginOptional.isPresent()) {
            userOptional = userRepository.findOneWithGroupsAndAuthoritiesAndGuidedTourSettingsAndIrisAcceptedTimestampByLogin(loginOptional.get());
        }

        if (userOptional.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        User user = userOptional.get();
        user.setVisibleRegistrationNumber();
        UserDTO userDTO = new UserDTO(user);
        // we set this value on purpose here: the user can only fetch their own information, make the token available for constructing the token-based clone-URL
        userDTO.setVcsAccessToken(user.getVcsAccessToken());
        userDTO.setVcsAccessTokenExpiryDate(user.getVcsAccessTokenExpiryDate());
        userDTO.setSshPublicKey(user.getSshPublicKey());
        log.info("GET /account {} took {}ms", user.getLogin(), System.currentTimeMillis() - start);
        return ResponseEntity.ok(userDTO);
    }

    /**
     * {@code POST  /account/change-language} : changes the current user's language key.
     *
     * @param languageKey languageKey to change to.
     * @return ResponseEntity with status 200 (OK)
     * @throws BadRequestAlertException {@code 400 (Bad Request)} if the language key is not 'en' or 'de'.
     */
    @PostMapping("account/change-language")
    @EnforceNothing
    public ResponseEntity<Void> changeLanguageKey(@RequestBody String languageKey) {
        User user = userRepository.getUser();
        String langKey = languageKey.replaceAll("\"", "").toLowerCase().trim();
        if (!"en".equals(langKey) && !"de".equals(langKey)) {
            throw new BadRequestAlertException("Language key %s not supported!".formatted(languageKey), "Account", "invalidLanguageKey");
        }
        userService.updateUserLanguageKey(user.getId(), langKey);
        return ResponseEntity.ok().build();
    }

    /**
     * {@code POST   /account/reset-password/init} : Send an email to reset the password of the user.
     *
     * @param mailUsername string containing either mail or username of the user.
     * @return ResponseEntity with status 200 (OK)
     */
    @PostMapping("account/reset-password/init")
    @EnforceNothing
    public ResponseEntity<Void> requestPasswordReset(@RequestBody String mailUsername) {
        List<User> users = userRepository.findAllByEmailOrUsernameIgnoreCase(mailUsername);
        if (!users.isEmpty()) {
            List<User> internalUsers = users.stream().filter(User::isInternal).toList();
            if (internalUsers.isEmpty()) {
                throw new BadRequestAlertException("The user is handled externally. The password can't be reset within Artemis.", "Account", "externalUser");
            }
            else if (internalUsers.size() >= 2) {
                throw new BadRequestAlertException("Email or username is not unique. Found multiple potential users", "Account", "usernameNotUnique");
            }
            var internalUser = internalUsers.getFirst();
            if (userService.prepareUserForPasswordReset(internalUser)) {
                mailService.sendPasswordResetMail(internalUsers.getFirst());
            }
        }
        else {
            // Pretend the request has been successful to prevent checking which emails or usernames really exist
            // but log that an invalid attempt has been made
            log.warn("Password reset requested for non-existing mail or username '{}'", mailUsername);
        }
        return ResponseEntity.ok().build();
    }

    /**
     * {@code POST   /account/reset-password/finish} : Finish to reset the password of the user.
     *
     * @param keyAndPassword the generated key and the new password.
     * @return ResponseEntity with status 200 (OK)
     * @throws PasswordViolatesRequirementsException {@code 400 (Bad Request)} if the password does not meet the requirements.
     * @throws RuntimeException                      {@code 500 (Internal Server Error)} if the password could not be reset.
     */
    @PostMapping("account/reset-password/finish")
    @EnforceNothing
    public ResponseEntity<Void> finishPasswordReset(@RequestBody KeyAndPasswordVM keyAndPassword) {
        if (accountService.isPasswordLengthInvalid(keyAndPassword.getNewPassword())) {
            throw new PasswordViolatesRequirementsException();
        }
        // TODO: the key should be 20 characters long according to jhipsters RandomUtil.DEF_COUNT, we should improve the following input validation
        // Idea: the key follows the same ideas as e.g. JWT: it should only be valid for a short time (i.e. the key should expire e.g. after 2 days)
        if (StringUtils.isEmpty(keyAndPassword.getKey()) || keyAndPassword.getKey().length() < 10) {
            throw new AccessForbiddenException("Invalid key for password reset");
        }
        Optional<User> user = userService.completePasswordReset(keyAndPassword.getNewPassword(), keyAndPassword.getKey());

        if (user.isEmpty()) {
            throw new AccessForbiddenException("No user was found for this reset key");
        }
        return ResponseEntity.ok().build();
    }
}
