package de.tum.in.www1.artemis.service.connectors;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.SAML2Properties;
import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.AuthoritiesConstants;
import de.tum.in.www1.artemis.service.MailService;
import de.tum.in.www1.artemis.service.user.UserCreationService;
import de.tum.in.www1.artemis.service.user.UserService;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;

/**
 * This class describes a service for SAML2 authentication.
 * 
 */
@Service
@Profile("saml2")
public class SAML2Service {

    @Value("${info.saml2.enable-password:#{null}}")
    private Optional<Boolean> saml2EnablePassword;

    private final Logger log = LoggerFactory.getLogger(SAML2Service.class);

    private final UserCreationService userCreationService;

    private final UserRepository userRepository;

    private final UserService userService;

    private final SAML2Properties properties;

    private final MailService mailService;

    /**
     * Constructs a new instance.
     *
     * @param      userRepository  The user repository
     * @param      properties      The properties
     * @param      userCreationService The user creation service
     */
    public SAML2Service(final UserRepository userRepository, final SAML2Properties properties, final UserCreationService userCreationService, MailService mailService,
            UserService userService) {
        this.userRepository = userRepository;
        this.properties = properties;
        this.userCreationService = userCreationService;
        this.mailService = mailService;
        this.userService = userService;
    }

    /**
     * Handles an authentication via SAML2.
     * 
     * Registers new users and returns a new {@link UsernamePasswordAuthenticationToken} matching the SAML2 user.
     *
     * @param      principal  The principal
     * @return a new {@link UsernamePasswordAuthenticationToken} matching the SAML2 user
     */
    public Authentication handleAuthentication(final Saml2AuthenticatedPrincipal principal) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        log.debug("SAML2 User '{}' logged in, attributes {}", auth.getName(), principal.getAttributes());
        log.debug("SAML2 password-enabled: {}", saml2EnablePassword);

        final String username = substituteAttributes(properties.getUsernamePattern(), principal);
        Optional<User> user = userRepository.findOneWithGroupsAndAuthoritiesByLogin(username);
        if (user.isEmpty()) {
            // create User
            user = Optional.of(createUser(username, principal));

            if (saml2EnablePassword.isPresent() && Boolean.TRUE.equals(saml2EnablePassword.get())) {
                log.debug("Sending SAML2 creation mail");
                Optional<User> mailUser = userService.requestPasswordReset(user.get().getEmail());
                if (mailUser.isPresent()) {
                    mailService.sendSAML2SetPasswordMail(mailUser.get());
                }
                else {
                    log.error("User {} was created but could not be found in the database!");
                }
            }
        }

        auth = new UsernamePasswordAuthenticationToken(user.get().getLogin(), user.get().getPassword(), toGrantedAuthorities(user.get().getAuthorities()));
        return auth;
    }

    private User createUser(String username, final Saml2AuthenticatedPrincipal principal) {
        ManagedUserVM newUser = new ManagedUserVM();
        newUser.setLogin(username);
        newUser.setFirstName(substituteAttributes(properties.getFirstNamePattern(), principal));
        newUser.setLastName(substituteAttributes(properties.getLastNamePattern(), principal));
        newUser.setEmail(substituteAttributes(properties.getEmailPattern(), principal));
        String registrationNumber = substituteAttributes(properties.getRegistrationNumberPattern(), principal);
        if (!registrationNumber.isBlank()) {
            newUser.setVisibleRegistrationNumber(registrationNumber);
        } // else set registration number to null to preserve uniqueness
        newUser.setLangKey(substituteAttributes(properties.getLangKeyPattern(), principal));
        newUser.setAuthorities(new HashSet<>(Set.of(AuthoritiesConstants.USER)));
        newUser.setGroups(new HashSet<>());

        // userService.createUser(ManagedUserVM) does create an activated User, else use userService.registerUser()
        // a random password is generated
        return userCreationService.createUser(newUser);
    }

    private static Collection<GrantedAuthority> toGrantedAuthorities(final Collection<Authority> authorities) {
        return authorities.stream().map(Authority::getName).map(SimpleGrantedAuthority::new).collect(Collectors.toSet());
    }

    private static String substituteAttributes(final String input, final Saml2AuthenticatedPrincipal principal) {
        String output = input;
        for (String key : principal.getAttributes().keySet()) {
            final String escapedKey = Pattern.quote(key);
            output = output.replaceAll("\\{" + escapedKey + "\\}", principal.getFirstAttribute(key));
        }
        return output.replaceAll("\\{[^\\}]*?\\}", "");
    }

}
