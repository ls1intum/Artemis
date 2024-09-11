package de.tum.cit.aet.artemis.service.connectors;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
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

import de.tum.cit.aet.artemis.core.config.SAML2Properties;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.UserNotActivatedException;
import de.tum.cit.aet.artemis.domain.Authority;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.repository.UserRepository;
import de.tum.cit.aet.artemis.service.notifications.MailService;
import de.tum.cit.aet.artemis.service.user.UserCreationService;
import de.tum.cit.aet.artemis.service.user.UserService;
import de.tum.cit.aet.artemis.web.rest.vm.ManagedUserVM;

/**
 * This class describes a service for SAML2 authentication.
 * <p>
 * The main method is {@link #handleAuthentication(Saml2AuthenticatedPrincipal)}. The service extracts the user information
 * from the {@link Saml2AuthenticatedPrincipal} and creates the user, if it does not exist already.
 * <p>
 * When the user gets created, the SAML2 attributes can be used to fill in user information. The configuration happens
 * via patterns for every field in the SAML2 configuration.
 * <p>
 * The service creates a {@link UsernamePasswordAuthenticationToken} which can then be used by the client to authenticate.
 * This is needed, since the client "does not know" that he is already authenticated via SAML2.
 */
@Service
@Profile("saml2")
public class SAML2Service {

    @Value("${info.saml2.enable-password:#{null}}")
    private Optional<Boolean> saml2EnablePassword;

    private static final Logger log = LoggerFactory.getLogger(SAML2Service.class);

    private final UserCreationService userCreationService;

    private final UserRepository userRepository;

    private final UserService userService;

    private final SAML2Properties properties;

    private final MailService mailService;

    private final Map<String, Pattern> extractionPatterns;

    /**
     * Constructs a new instance.
     *
     * @param userRepository      The user repository
     * @param properties          The properties
     * @param userCreationService The user creation service
     */
    public SAML2Service(final UserRepository userRepository, final SAML2Properties properties, final UserCreationService userCreationService, MailService mailService,
            UserService userService) {
        this.userRepository = userRepository;
        this.properties = properties;
        this.userCreationService = userCreationService;
        this.mailService = mailService;
        this.userService = userService;

        this.extractionPatterns = generateExtractionPatterns(properties);
    }

    private Map<String, Pattern> generateExtractionPatterns(final SAML2Properties properties) {
        return properties.getValueExtractionPatterns().stream()
                .collect(Collectors.toMap(SAML2Properties.ExtractionPattern::getKey, pattern -> Pattern.compile(pattern.getValuePattern())));
    }

    /**
     * Handles an authentication via SAML2.
     * <p>
     * Registers new users and returns a new {@link UsernamePasswordAuthenticationToken} matching the SAML2 user.
     *
     * @param principal the principal, containing the user information
     * @return a new {@link UsernamePasswordAuthenticationToken} matching the SAML2 user
     */
    public Authentication handleAuthentication(final Saml2AuthenticatedPrincipal principal) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        log.debug("SAML2 User '{}' logged in, attributes {}", auth.getName(), principal.getAttributes());
        log.debug("SAML2 password-enabled: {}", saml2EnablePassword);

        final String username = substituteAttributes(properties.getUsernamePattern(), principal);
        Optional<User> user = userRepository.findOneWithGroupsAndAuthoritiesByLogin(username);
        if (user.isEmpty()) {
            // create User if not exists
            user = Optional.of(createUser(username, principal));

            if (saml2EnablePassword.isPresent() && Boolean.TRUE.equals(saml2EnablePassword.get())) {
                log.debug("Sending SAML2 creation mail");
                if (userService.prepareUserForPasswordReset(user.get())) {
                    mailService.sendSAML2SetPasswordMail(user.get());
                }
                else {
                    log.error("User {} was created but could not be found in the database!", user.get());
                }
            }
        }

        if (!user.get().getActivated()) {
            log.debug("Not activated SAML2 user {} attempted login.", user.get());
            throw new UserNotActivatedException("User was disabled.");
        }

        auth = new UsernamePasswordAuthenticationToken(user.get().getLogin(), user.get().getPassword(), toGrantedAuthorities(user.get().getAuthorities()));
        return auth;
    }

    private User createUser(String username, final Saml2AuthenticatedPrincipal principal) {
        ManagedUserVM newUser = new ManagedUserVM();
        // Fill in User information using the patterns and the SAML2 attributes.
        newUser.setLogin(username);
        newUser.setFirstName(substituteAttributes(properties.getFirstNamePattern(), principal));
        newUser.setLastName(substituteAttributes(properties.getLastNamePattern(), principal));
        newUser.setEmail(substituteAttributes(properties.getEmailPattern(), principal));
        String registrationNumber = substituteAttributes(properties.getRegistrationNumberPattern(), principal);
        if (!registrationNumber.isBlank()) {
            newUser.setVisibleRegistrationNumber(registrationNumber);
        } // else set registration number to null to preserve uniqueness
        newUser.setLangKey(substituteAttributes(properties.getLangKeyPattern(), principal));
        newUser.setAuthorities(new HashSet<>(Set.of(Role.STUDENT.getAuthority())));
        newUser.setGroups(new HashSet<>());

        // userService.createUser(ManagedUserVM) does create an activated User
        // a random password is generated
        return userCreationService.createUser(newUser);
    }

    private static Collection<GrantedAuthority> toGrantedAuthorities(final Collection<Authority> authorities) {
        return authorities.stream().map(Authority::getName).map(SimpleGrantedAuthority::new).collect(Collectors.toSet());
    }

    private String substituteAttributes(final String input, final Saml2AuthenticatedPrincipal principal) {
        String output = input;
        for (String key : principal.getAttributes().keySet()) {
            final String escapedKey = Pattern.quote(key);
            output = output.replaceAll("\\{" + escapedKey + "\\}", getAttributeValue(principal, key));
        }
        return output.replaceAll("\\{[^\\}]*?\\}", "");
    }

    /**
     * Gets the value associated with the given key from the principal.
     *
     * @param principal containing the user information.
     * @param key       of the attribute that should be extracted.
     * @return the value associated with the given key.
     */
    private String getAttributeValue(final Saml2AuthenticatedPrincipal principal, final String key) {
        final String value = principal.getFirstAttribute(key);
        if (value == null) {
            return "";
        }

        final Pattern extractionPattern = extractionPatterns.get(key);
        if (extractionPattern == null) {
            return value;
        }

        final Matcher matcher = extractionPattern.matcher(value);
        if (matcher.matches()) {
            return matcher.group(SAML2Properties.ATTRIBUTE_VALUE_EXTRACTION_GROUP_NAME);
        }
        else {
            return value;
        }
    }
}
