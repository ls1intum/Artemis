package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.PersistentToken;
import de.tum.in.www1.exerciseapp.repository.PersistentTokenRepository;
import de.tum.in.www1.exerciseapp.domain.User;
import de.tum.in.www1.exerciseapp.repository.UserRepository;
import de.tum.in.www1.exerciseapp.security.SecurityUtils;
import de.tum.in.www1.exerciseapp.service.MailService;
import de.tum.in.www1.exerciseapp.service.UserService;
import de.tum.in.www1.exerciseapp.service.dto.UserDTO;
import de.tum.in.www1.exerciseapp.web.rest.errors.*;
import de.tum.in.www1.exerciseapp.web.rest.vm.KeyAndPasswordVM;
import de.tum.in.www1.exerciseapp.web.rest.vm.ManagedUserVM;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
* REST controller for managing the current user's account.
*/
@RestController
@RequestMapping("/api")
public class AccountResource {

    private final Logger log = LoggerFactory.getLogger(AccountResource.class);

    private final UserRepository userRepository;

    private final UserService userService;

    private final MailService mailService;

    private final PersistentTokenRepository persistentTokenRepository;

    public AccountResource(UserRepository userRepository, UserService userService, MailService mailService, PersistentTokenRepository persistentTokenRepository) {

        this.userRepository = userRepository;
        this.userService = userService;
        this.mailService = mailService;
        this.persistentTokenRepository = persistentTokenRepository;
    }

    /**
    * POST  /register : register the user.
    *
    * @param managedUserVM the managed user View Model
    * @throws InvalidPasswordException 400 (Bad Request) if the password is incorrect
    * @throws EmailAlreadyUsedException 400 (Bad Request) if the email is already used
    * @throws LoginAlreadyUsedException 400 (Bad Request) if the login is already used
    */
    @PostMapping("/register")
    @Timed
    @ResponseStatus(HttpStatus.CREATED)
    public void registerAccount(@Valid @RequestBody ManagedUserVM managedUserVM) {
        if (!checkPasswordLength(managedUserVM.getPassword())) {
            throw new InvalidPasswordException();
        }
        userRepository.findOneByLogin(managedUserVM.getLogin().toLowerCase()).ifPresent(u -> {throw new LoginAlreadyUsedException();});
        userRepository.findOneByEmailIgnoreCase(managedUserVM.getEmail()).ifPresent(u -> {throw new EmailAlreadyUsedException();});
        User user = userService.registerUser(managedUserVM);
        mailService.sendActivationEmail(user);
    }

    /**
    * GET  /activate : activate the registered user.
    *
    * @param key the activation key
    * @throws RuntimeException 500 (Internal Server Error) if the user couldn't be activated
    */
    @GetMapping("/activate")
    @Timed
    public void activateAccount(@RequestParam(value = "key") String key) {
        Optional<User> user = userService.activateRegistration(key);
        if (!user.isPresent()) {
            throw new InternalServerErrorException("No user was found for this reset key");
        };
    }

    /**
    * GET  /authenticate : check if the user is authenticated, and return its login.
    *
    * @param request the HTTP request
    * @return the login if the user is authenticated
    */
    @GetMapping("/authenticate")
    @Timed
    public String isAuthenticated(HttpServletRequest request) {
        log.debug("REST request to check if the current user is authenticated");
        return request.getRemoteUser();
    }

    /**
    * GET  /account : get the current user.
    *
    * @return the current user
    * @throws RuntimeException 500 (Internal Server Error) if the user couldn't be returned
    */
    @GetMapping("/account")
    @Timed
    public UserDTO getAccount() {
        return Optional.ofNullable(userService.getUserWithAuthorities())
            .map(UserDTO::new)
            .orElseThrow(() -> new InternalServerErrorException("User could not be found"));
    }

    /**
     * GET  /account/password : get the current users password.
     *
     * @return the ResponseEntity with status 200 (OK) and the current user password in body, or status 500 (Internal Server Error) if the user couldn't be returned
     */
    @GetMapping(value = "/account/password", produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<?> getPassword() {

        Map<String, String> body = new HashMap<>();
        body.put("password", userService.decryptPassword());
        return new ResponseEntity<>(body, HttpStatus.OK);
    }

    /**
     * POST  /account : update the current user information.
     *
     * @param userDTO the current user information
     * @throws EmailAlreadyUsedException 400 (Bad Request) if the email is already used
     * @throws RuntimeException 500 (Internal Server Error) if the user login wasn't found
     */
    @PostMapping("/account")
    @Timed
    public void saveAccount(@Valid @RequestBody UserDTO userDTO) {
        final String userLogin = SecurityUtils.getCurrentUserLogin();
        Optional<User> existingUser = userRepository.findOneByEmailIgnoreCase(userDTO.getEmail());
        if (existingUser.isPresent() && (!existingUser.get().getLogin().equalsIgnoreCase(userLogin))) {
            throw new EmailAlreadyUsedException();
        }
        Optional<User> user = userRepository.findOneByLogin(userLogin);
        if (!user.isPresent()) {
            throw new InternalServerErrorException("User could not be found");
        }
        userService.updateUser(userDTO.getFirstName(), userDTO.getLastName(), userDTO.getEmail(),
            userDTO.getLangKey(), userDTO.getImageUrl());
   }

    /**
    * POST  /account/change-password : changes the current user's password
    *
    * @param password the new password
    * @throws InvalidPasswordException 400 (Bad Request) if the new password is incorrect
    */
    @PostMapping(path = "/account/change-password")
    @Timed
    public void changePassword(@RequestBody String password) {
        if (!checkPasswordLength(password)) {
            throw new InvalidPasswordException();
        }
        userService.changePassword(password);
   }

    /**
    * GET  /account/sessions : get the current open sessions.
    *
    * @return the current open sessions
    * @throws RuntimeException 500 (Internal Server Error) if the current open sessions couldn't be retrieved
    */
    @GetMapping("/account/sessions")
    @Timed
    public List<PersistentToken> getCurrentSessions() {
        return persistentTokenRepository.findByUser(
            userRepository.findOneByLogin(SecurityUtils.getCurrentUserLogin())
                .orElseThrow(() -> new InternalServerErrorException("User could not be found"))
        );
    }

    /**
    * DELETE  /account/sessions?series={series} : invalidate an existing session.
    *
    * - You can only delete your own sessions, not any other user's session
    * - If you delete one of your existing sessions, and that you are currently logged in on that session, you will
    *   still be able to use that session, until you quit your browser: it does not work in real time (there is
    *   no API for that), it only removes the "remember me" cookie
    * - This is also true if you invalidate your current session: you will still be able to use it until you close
    *   your browser or that the session times out. But automatic login (the "remember me" cookie) will not work
    *   anymore.
    *   There is an API to invalidate the current session, but there is no API to check which session uses which
    *   cookie.
    *
    * @param series the series of an existing session
    * @throws UnsupportedEncodingException if the series couldnt be URL decoded
    */
    @DeleteMapping("/account/sessions/{series}")
    @Timed
    public void invalidateSession(@PathVariable String series) throws UnsupportedEncodingException {
        String decodedSeries = URLDecoder.decode(series, "UTF-8");
        userRepository.findOneByLogin(SecurityUtils.getCurrentUserLogin()).ifPresent(u ->
            persistentTokenRepository.findByUser(u).stream()
                .filter(persistentToken -> StringUtils.equals(persistentToken.getSeries(), decodedSeries))
                .findAny().ifPresent(t -> persistentTokenRepository.delete(decodedSeries)));
    }

    /**
    * POST   /account/reset-password/init : Send an email to reset the password of the user
    *
    * @param mail the mail of the user
    * @throws EmailNotFoundException 400 (Bad Request) if the email address is not registered
    */
    @PostMapping(path = "/account/reset-password/init")
    @Timed
    public void requestPasswordReset(@RequestBody String mail) {
       mailService.sendPasswordResetMail(
           userService.requestPasswordReset(mail)
               .orElseThrow(EmailNotFoundException::new)
       );
    }

    /**
    * POST   /account/reset-password/finish : Finish to reset the password of the user
    *
    * @param keyAndPassword the generated key and the new password
    * @throws InvalidPasswordException 400 (Bad Request) if the password is incorrect
    * @throws RuntimeException 500 (Internal Server Error) if the password could not be reset
    */
    @PostMapping(path = "/account/reset-password/finish")
    @Timed
    public void finishPasswordReset(@RequestBody KeyAndPasswordVM keyAndPassword) {
        if (!checkPasswordLength(keyAndPassword.getNewPassword())) {
            throw new InvalidPasswordException();
        }
        Optional<User> user =
            userService.completePasswordReset(keyAndPassword.getNewPassword(), keyAndPassword.getKey());

        if (!user.isPresent()) {
            throw new InternalServerErrorException("No user was found for this reset key");
        }
    }

    private static boolean checkPasswordLength(String password) {
        return !StringUtils.isEmpty(password) &&
            password.length() >= ManagedUserVM.PASSWORD_MIN_LENGTH &&
            password.length() <= ManagedUserVM.PASSWORD_MAX_LENGTH;
    }
}
