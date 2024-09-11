package de.tum.cit.aet.artemis.service.connectors.ldap;

import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.codec.Utf8;
import org.springframework.security.ldap.SpringSecurityLdapTemplate;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.repository.UserRepository;
import de.tum.cit.aet.artemis.security.ArtemisAuthenticationProvider;
import de.tum.cit.aet.artemis.security.ArtemisAuthenticationProviderImpl;
import de.tum.cit.aet.artemis.security.SecurityUtils;
import de.tum.cit.aet.artemis.service.ldap.LdapUserDto;
import de.tum.cit.aet.artemis.service.ldap.LdapUserService;
import de.tum.cit.aet.artemis.service.user.AuthorityService;
import de.tum.cit.aet.artemis.service.user.PasswordService;
import de.tum.cit.aet.artemis.service.user.UserCreationService;
import de.tum.cit.aet.artemis.service.util.TimeLogUtil;

@Component
@Profile("ldap-only")
@Primary
@ComponentScan("de.tum.cit.aet.artemis.*")
public class LdapAuthenticationProvider extends ArtemisAuthenticationProviderImpl implements ArtemisAuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(LdapAuthenticationProvider.class);

    private final LdapUserService ldapUserService;

    private final AuthorityService authorityService;

    private final SpringSecurityLdapTemplate ldapTemplate;

    public LdapAuthenticationProvider(UserRepository userRepository, LdapUserService ldapUserService, PasswordService passwordService, AuthorityService authorityService,
            UserCreationService userCreationService, SpringSecurityLdapTemplate ldapTemplate) {
        super(userRepository, passwordService, userCreationService);
        this.ldapUserService = ldapUserService;
        this.authorityService = authorityService;
        this.ldapTemplate = ldapTemplate;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        User user = getOrCreateUser(authentication);
        if (user != null) {
            return new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword(), user.getGrantedAuthorities());
        }
        return null;
    }

    /**
     * Get or create a user based on the given authentication. This method will check the password
     *
     * @param authentication The authentication object
     * @return The user object or null if the user is internal
     */
    private User getOrCreateUser(Authentication authentication) {
        String loginOrEmail = authentication.getName().toLowerCase(Locale.ENGLISH);
        String password = authentication.getCredentials().toString();

        long start = System.nanoTime();

        // distinguish between login and email here by using a simple regex
        boolean isEmail = SecurityUtils.isEmail(loginOrEmail);

        Optional<User> optionalUser;
        if (isEmail) {
            // It's an email, try to find the Artemis user in the database based on the email
            optionalUser = userRepository.findOneWithGroupsAndAuthoritiesByEmail(loginOrEmail);
        }
        else {
            // It's a login, try to find the Artemis user in the database based on the login
            optionalUser = userRepository.findOneWithGroupsAndAuthoritiesByLogin(loginOrEmail);
        }
        if (optionalUser.isPresent() && optionalUser.get().isInternal()) {
            // User found but is internal. Skip external authentication.
            return null;
        }

        log.debug("Finished userRepository.findOneWithGroupsAndAuthoritiesByLoginOrEmail in {}", TimeLogUtil.formatDurationFrom(start));
        start = System.nanoTime();

        // If the following code is executed, the user is either not yet existent or an external user
        final LdapUserDto ldapUserDto;
        if (isEmail) {
            // It's an email, try to find the LDAP user in the external user management system based on the given email (which must not be the main user email)
            ldapUserDto = ldapUserService.findByAnyEmail(loginOrEmail).orElseThrow(() -> new BadCredentialsException("Wrong credentials"));
        }
        else {
            // It's a login, try to find the LDAP user in the external user management system based on the given login
            ldapUserDto = ldapUserService.findByLogin(loginOrEmail).orElseThrow(() -> new BadCredentialsException("Wrong credentials"));
        }

        if (isEmail && optionalUser.isEmpty()) {
            // this is an edge case which could happen when the user email changed or the user has multiple email addresses and used a secondary email to login
            // therefore, double check if the Artemis User with the LDAP login (based on the given email) exists. If yes, we will use this user and update the LDAP values below
            // without this code a second user would be created in Artemis which is not what we want (additionally this would fail because of unique constraints)
            optionalUser = userRepository.findOneWithGroupsAndAuthoritiesByLogin(ldapUserDto.getLogin());
        }
        log.debug("Finished ldapUserService.findByLogin in {}", TimeLogUtil.formatDurationFrom(start));
        start = System.nanoTime();

        // Use the given password to compare it with the LDAP entry (i.e. check the authorization)
        byte[] passwordBytes = Utf8.encode(password);
        boolean passwordCorrect = ldapTemplate.compare(ldapUserDto.getUid().toString(), "userPassword", passwordBytes);
        log.debug("Compare password with LDAP entry for user {} to validate login", loginOrEmail);
        if (!passwordCorrect) {
            throw new BadCredentialsException("Wrong credentials");
        }

        log.debug("Finished ldapTemplate.compare password in {}", TimeLogUtil.formatDurationFrom(start));

        // update the user details from ldapUserDto (because they might have changed, e.g. when the user changes the name)
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
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
                user.setRegistrationNumber(ldapUserDto.getRegistrationNumber());
                saveNeeded = true;
            }
            // only save the user in the database in case it has changed
            if (saveNeeded) {
                user = userRepository.save(user);
            }
            return user;
        }
        else {
            // this handles the case that the user does not exist in the Artemis database yet (i.e. first time user login)
            User newUser = userCreationService.createUser(ldapUserDto.getLogin(), null, null, ldapUserDto.getFirstName(), ldapUserDto.getLastName(), ldapUserDto.getEmail(),
                    ldapUserDto.getRegistrationNumber(), null, "en", false);

            newUser.setGroups(new HashSet<>());
            newUser.setAuthorities(authorityService.buildAuthorities(newUser));

            if (!newUser.getActivated()) {
                newUser.setActivated(true);
                newUser.setActivationKey(null);
            }
            return userCreationService.saveUser(newUser);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return true;
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
