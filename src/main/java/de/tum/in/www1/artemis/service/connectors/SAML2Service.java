package de.tum.in.www1.artemis.service.connectors;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;
import de.tum.in.www1.artemis.security.AuthoritiesConstants;
import de.tum.in.www1.artemis.service.UserService;

/**
 * This class describes a service for SAML2 authentication.
 * 
 */
@Service
@Profile("saml2")
public class SAML2Service {

    private final Logger log = LoggerFactory.getLogger(SAML2Service.class);

    @Autowired
    private UserService userService;

    @Autowired
    private ArtemisAuthenticationProvider artemisAuthenticationProvider;

    /**
     * Handles an authentication via SAML2.
     * 
     * Registers new users and logs in existing users, by the specified identifier.
     *
     * @param      principal  The principal
     */
    public void handleAuthentication(final Saml2AuthenticatedPrincipal principal) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        log.debug("User {} logged in with SAML2", auth.getName());
        log.debug("User {} attributes {}", auth.getName(), principal.getAttributes());

        final String username = principal.getFirstAttribute("uid");
        final String firstName = principal.getFirstAttribute("first_name");
        final String lastName = principal.getFirstAttribute("last_name");
        final String email = principal.getFirstAttribute("email");


        final User user = artemisAuthenticationProvider.getOrCreateUser(new UsernamePasswordAuthenticationToken(
                username, "randomPassword"), firstName, lastName, email, true);
        
        // or send activation mail...
        if (!user.getActivated()) {
            userService.activateUser(user);
        }
        
        // failes with LazyInitializationException
        //SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        //        user.getLogin(), user.getPassword(), toGrantedAuthorities(user.getAuthorities())));
        
        // only register for now
        // auth = new UsernamePasswordAuthenticationToken(
        //         user.getLogin(), user.getPassword(), Collections.singletonList(new SimpleGrantedAuthority(AuthoritiesConstants.USER)));
        // SecurityContextHolder.getContext().setAuthentication(auth);

    }

    private static Collection<GrantedAuthority> toGrantedAuthorities(final Collection<Authority> authorities) {
        return authorities.stream().map(Authority::getName).map(SimpleGrantedAuthority::new).collect(Collectors.toSet());
    }


}