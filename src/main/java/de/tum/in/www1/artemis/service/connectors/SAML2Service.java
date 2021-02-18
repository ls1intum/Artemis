package de.tum.in.www1.artemis.service.connectors;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
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
import de.tum.in.www1.artemis.security.AuthoritiesConstants;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;

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

        log.debug("User {} logged in with SAML2", auth.getName());
        log.debug("User {} attributes {}", auth.getName(), principal.getAttributes());

        final String username = principal.getFirstAttribute("uid");


        Optional<User> user = userService.getUserByLogin(username);
        if (user.isEmpty()) {
            // create User
            ManagedUserVM newUser = new ManagedUserVM();
            newUser.setLogin(username);
            newUser.setFirstName(principal.getFirstAttribute("first_name"));
            newUser.setLastName(principal.getFirstAttribute("last_name"));
            newUser.setEmail(principal.getFirstAttribute("email"));
            // newUser.setVisibleRegistrationNumber(principal.getFirstAttribute("matriculation"));

            newUser.setAuthorities(new HashSet<>(Set.of(AuthoritiesConstants.USER)));
            newUser.setGroups(new HashSet<>());

            // userService.createUser(ManagedUserVM) does create an activated User, else use userService.registerUser()
            // a random password is generated
            user = Optional.of(userService.createUser(newUser));
        }
        
        // failes with LazyInitializationException
        //SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        //        user.getLogin(), user.getPassword(), toGrantedAuthorities(user.getAuthorities())));

        auth = new UsernamePasswordAuthenticationToken(
                 user.get().getLogin(), user.get().getPassword(), Collections.singletonList(new SimpleGrantedAuthority(AuthoritiesConstants.USER)));
        return auth;
    }

    private static Collection<GrantedAuthority> toGrantedAuthorities(final Collection<Authority> authorities) {
        return authorities.stream().map(Authority::getName).map(SimpleGrantedAuthority::new).collect(Collectors.toSet());
    }


}