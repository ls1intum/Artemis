package de.tum.in.www1.artemis.service.connectors.ldap;

import java.util.HashSet;
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

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exception.ArtemisAuthenticationException;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProviderImpl;
import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;
import de.tum.in.www1.artemis.service.ldap.LdapUserDto;
import de.tum.in.www1.artemis.service.ldap.LdapUserService;
import de.tum.in.www1.artemis.service.user.AuthorityService;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.service.user.UserCreationService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;

@Component
@Profile("ldap-only")
@Primary
@ComponentScan("de.tum.in.www1.artemis.*")
public class LdapAuthenticationProvider extends ArtemisAuthenticationProviderImpl implements ArtemisAuthenticationProvider {

    private final Logger log = LoggerFactory.getLogger(LdapAuthenticationProvider.class);

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
        User user = getOrCreateUser(authentication, false);
        if (user != null) {
            return new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword(), user.getGrantedAuthorities());
        }
        return null;
    }

    @Override
    public User getOrCreateUser(Authentication authentication, String firstName, String lastName, String email, boolean skipPasswordCheck) {
        // NOTE: firstName, lastName, email is not needed in this case since we always get these values from LDAP
        return getOrCreateUser(authentication, skipPasswordCheck);
    }

    private User getOrCreateUser(Authentication authentication, Boolean skipPasswordCheck) {
        String username = authentication.getName().toLowerCase();
        String password = authentication.getCredentials().toString();

        long start = System.nanoTime();

        final var optionalUser = userRepository.findOneWithGroupsAndAuthoritiesByLogin(username);
        if (optionalUser.isPresent() && optionalUser.get().isInternal()) {
            // User found but is internal. Skip external authentication.
            return null;
        }

        log.info("Finished userRepository.findOneWithGroupsAndAuthoritiesByLogin in {}", TimeLogUtil.formatDurationFrom(start));
        start = System.nanoTime();

        // If the following code is executed, the user is either not yet existent or an external user

        final LdapUserDto ldapUserDto = ldapUserService.findByUsername(username).orElseThrow(() -> new BadCredentialsException("Wrong credentials"));

        log.info("Finished ldapUserService.findByUsername in {}", TimeLogUtil.formatDurationFrom(start));
        start = System.nanoTime();

        // If we want to skip the password check, we can just use the ADMIN auth, which is already injected in the default restTemplate
        // Otherwise, we create our own authorization and use the credentials of the user.
        if (!skipPasswordCheck) {
            byte[] passwordBytes = Utf8.encode(password);
            boolean passwordCorrect = ldapTemplate.compare(ldapUserDto.getUid().toString(), "userPassword", passwordBytes);
            log.debug("Compare password with LDAP entry for user " + username + " to validate login");
            // this is the normal case, where the password is validated
            if (!passwordCorrect) {
                throw new BadCredentialsException("Wrong credentials");
            }
        }

        log.info("Finished ldapTemplate.compare password in {}", TimeLogUtil.formatDurationFrom(start));

        return optionalUser.orElseGet(() -> {
            User newUser = userCreationService.createUser(ldapUserDto.getUsername(), null, null, ldapUserDto.getFirstName(), ldapUserDto.getLastName(), ldapUserDto.getEmail(),
                    ldapUserDto.getRegistrationNumber(), null, "en", false);

            newUser.setGroups(new HashSet<>());
            newUser.setAuthorities(authorityService.buildAuthorities(newUser));

            if (!newUser.getActivated()) {
                newUser.setActivated(true);
                newUser.setActivationKey(null);
            }
            return userCreationService.saveUser(newUser);
        });
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return true;
    }

    /**
     * Adds a user to a group. Ignores "user is already a member of" errors.
     *
     * @param user  The user
     * @param group The group name
     * @throws ArtemisAuthenticationException returns an error
     */
    @Override
    public void addUserToGroup(User user, String group) throws ArtemisAuthenticationException {
        // not relevant: this would only be possible if the LDAP supports groups and the connection has write access
    }

    @Override
    public void createUserInExternalUserManagement(User user) {
        // not relevant
    }

    @Override
    public void createGroup(String groupName) {
        // not relevant
    }

    @Override
    public void deleteGroup(String groupName) {
        // not relevant
    }

    @Override
    public void removeUserFromGroup(User user, String group) {
        // not relevant: this would only be possible if the LDAP supports groups and the connection has write access
    }

    @Override
    public boolean isGroupAvailable(String group) {
        // not relevant: this would only be possible if the LDAP supports groups
        return true;
    }

    @Override
    public ConnectorHealth health() {
        // we cannot
        return new ConnectorHealth(true);
    }

    /**
     * Checks if a user for the given email address exists.
     *
     * @param email The user email address
     * @return Optional String of username
     * @throws ArtemisAuthenticationException an exception when the user cannot be retrieved
     */
    @Override
    public Optional<String> getUsernameForEmail(String email) throws ArtemisAuthenticationException {
        return ldapUserService.findByEmail(email).map(LdapUserDto::getUsername);
    }
}
