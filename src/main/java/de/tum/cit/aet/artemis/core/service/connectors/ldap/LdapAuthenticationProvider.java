package de.tum.cit.aet.artemis.core.service.connectors.ldap;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LDAP;

import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.ldap.SpringSecurityLdapTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.ArtemisAuthenticationProvider;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.ldap.LdapUserDto;
import de.tum.cit.aet.artemis.core.service.ldap.LdapUserService;
import de.tum.cit.aet.artemis.core.service.user.AuthorityService;
import de.tum.cit.aet.artemis.core.service.user.UserCreationService;

/**
 * This class is responsible for authenticating users against an LDAP server.
 * It retrieves user information from the LDAP server and creates or updates the user in the Artemis database.
 */
@Component("ldapAuthenticationProvider")
@Profile(PROFILE_LDAP)
@Lazy
@Primary
public class LdapAuthenticationProvider implements ArtemisAuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(LdapAuthenticationProvider.class);

    private final LdapUserService ldapUserService;

    private final AuthorityService authorityService;

    private final UserCreationService userCreationService;

    private final SpringSecurityLdapTemplate ldapTemplate;

    private final UserRepository userRepository;

    public LdapAuthenticationProvider(LdapUserService ldapUserService, AuthorityService authorityService, UserCreationService userCreationService,
            SpringSecurityLdapTemplate ldapTemplate, UserRepository userRepository) {
        this.ldapUserService = ldapUserService;
        this.authorityService = authorityService;
        this.userCreationService = userCreationService;
        this.ldapTemplate = ldapTemplate;
        this.userRepository = userRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        User user = authenticateUser(authentication);
        if (user != null) {
            return new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword(), user.getGrantedAuthorities());
        }
        return null;
    }

    /**
     * Authenticate a user based on the given authentication against the connected LDAP. This method will check the password in the external LDAP system.
     * It will retrieve the user information from the LDAP and compare it against an existing user in the database if it exists.
     * In case a user already exists and information has changed, this will be updated.
     * In case a user does not exist in the Artemis database yet, a new user will be created based on the LDAP user information.
     *
     * @param authentication The authentication object including the login and password
     * @return The user object or null if the user is internal
     */
    private User authenticateUser(Authentication authentication) throws BadCredentialsException {
        String loginOrEmail = authentication.getName().toLowerCase(Locale.ENGLISH);
        String password = authentication.getCredentials().toString();

        // distinguish between login and email here by using a simple regex
        boolean isEmail = SecurityUtils.isEmail(loginOrEmail);

        var optionalUser = findArtemisUser(isEmail, loginOrEmail);
        if (optionalUser.isPresent() && optionalUser.get().isInternal()) {
            // User found but is internal. Skip external authentication.
            return null;
        }

        final var ldapUserDto = findLdapUser(isEmail, loginOrEmail);

        if (isEmail && optionalUser.isEmpty()) {
            // this is an edge case which could happen when the user email changed or the user has multiple email addresses and used a secondary email to login
            // therefore, double check if the Artemis User with the LDAP login (based on the given email) exists. If yes, we will use this user and update the LDAP values below
            // without this code a second user would be created in Artemis which is not what we want (additionally this would fail because of unique constraints)
            optionalUser = userRepository.findOneWithGroupsAndAuthoritiesByLogin(ldapUserDto.getLogin());
        }

        // Use the given password to authenticate the user in the LDAP
        boolean isAuthenticated = ldapTemplate.authenticate("", String.format("(uid=%s)", ldapUserDto.getLogin()), password);
        if (!isAuthenticated) {
            throw new BadCredentialsException("Wrong credentials");
        }

        // update the user details from ldapUserDto (because they might have changed, e.g. when the user changes the name)
        if (optionalUser.isPresent()) {
            // TODO: make sure the user is not deactivated in the meantime
            return saveUserIfNeeded(optionalUser.get(), ldapUserDto);
        }
        else {
            // this handles the case that the user does not exist in the Artemis database yet (i.e. first time user login)
            return createUser(ldapUserDto);
        }
    }

    /**
     * Creates a new Artemis user based on the given LDAP user DTO and stores it in the database.
     * Initially, the user does not belong to any groups and has only the STUDENT authority assigned
     *
     * @param ldapUserDto The LDAP user DTO containing the user information
     * @return The created Artemis user
     */
    private User createUser(LdapUserDto ldapUserDto) {
        User newUser = userCreationService.createUser(ldapUserDto.getLogin(), null, null, ldapUserDto.getFirstName(), ldapUserDto.getLastName(), ldapUserDto.getEmail(),
                ldapUserDto.getRegistrationNumber(), null, "en", false);

        newUser.setGroups(new HashSet<>());
        newUser.setAuthorities(authorityService.buildAuthorities(newUser));

        if (!newUser.getActivated()) {
            newUser.setActivated(true);
            newUser.setActivationKey(null);
        }
        log.info("New LDAP user {} created in Artemis", ldapUserDto.getLogin());
        return userCreationService.saveUser(newUser);
    }

    /**
     * Saves the user if any of the fields have changed compared to the LDAP user DTO.
     *
     * @param user        The Artemis user to be saved
     * @param ldapUserDto The LDAP user DTO containing the latest information
     * @return The saved or updated user
     */
    private User saveUserIfNeeded(User user, LdapUserDto ldapUserDto) {
        boolean saveNeeded = false;
        if (!Objects.equals(user.getLogin(), ldapUserDto.getLogin())) {
            user.setLogin(ldapUserDto.getLogin());
            saveNeeded = true;
        }
        if (!Objects.equals(user.getFirstName(), ldapUserDto.getFirstName())) {
            user.setFirstName(ldapUserDto.getFirstName());
            saveNeeded = true;
        }
        if (!Objects.equals(user.getLastName(), ldapUserDto.getLastName())) {
            user.setLastName(ldapUserDto.getLastName());
            saveNeeded = true;
        }
        if (!Objects.equals(user.getEmail(), ldapUserDto.getEmail())) {
            user.setEmail(ldapUserDto.getEmail());
            saveNeeded = true;
        }
        if (!Objects.equals(user.getRegistrationNumber(), ldapUserDto.getRegistrationNumber())) {
            // an empty string is considered as null to satisfy the unique constraint on registration number
            if (StringUtils.hasText(ldapUserDto.getRegistrationNumber())) {
                user.setRegistrationNumber(ldapUserDto.getRegistrationNumber());
                saveNeeded = true;
            }
        }
        // only save the user in the database in case it has changed
        if (saveNeeded) {
            user = userRepository.save(user);
        }
        return user;
    }

    /**
     * Finds the Artemis user based on the given login or email.
     * If the user does not exist, it will return an empty Optional.
     *
     * @param isEmail      true if the loginOrEmail is an email, false if it is a login
     * @param loginOrEmail The login or email of the user to be found
     * @return An Optional containing the found Artemis user, or empty if not found
     */
    private Optional<User> findArtemisUser(boolean isEmail, String loginOrEmail) {
        return isEmail ?
        // It's an email, try to find the Artemis user in the database based on the email
                userRepository.findOneWithGroupsAndAuthoritiesByEmail(loginOrEmail) :
                // It's a login, try to find the Artemis user in the database based on the login
                userRepository.findOneWithGroupsAndAuthoritiesByLogin(loginOrEmail);
    }

    /**
     * Finds the LDAP user based on the given login or email.
     * If the user does not exist, a BadCredentialsException is thrown.
     *
     * @param isEmail      true if the loginOrEmail is an email, false if it is a login
     * @param loginOrEmail The login or email of the user to be found
     * @return The found LDAP user DTO
     * @throws BadCredentialsException if the user does not exist in the LDAP system
     */
    private LdapUserDto findLdapUser(boolean isEmail, String loginOrEmail) throws BadCredentialsException {
        // If the following code is executed, the user is either not yet existent or an external user
        var optionalLdapUser = isEmail ?
        // It's an email, try to find the LDAP user in the external user management system based on the given email (which must not be the main user email)
                ldapUserService.findByAnyEmail(loginOrEmail) :
                // It's a login, try to find the LDAP user in the external user management system based on the given login
                ldapUserService.findByLogin(loginOrEmail);
        return optionalLdapUser.orElseThrow(() -> new BadCredentialsException("Wrong credentials"));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    /**
     * Checks if a user for the given email address exists.
     *
     * @param email The user email address
     * @return Optional String of username
     */
    @Override
    public Optional<String> getUsernameForEmail(String email) {
        return ldapUserService.findByAnyEmail(email).map(LdapUserDto::getLogin);
    }
}
