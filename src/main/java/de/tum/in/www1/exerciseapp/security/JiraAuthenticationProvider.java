package de.tum.in.www1.exerciseapp.security;

import de.tum.in.www1.exerciseapp.domain.Authority;
import de.tum.in.www1.exerciseapp.domain.User;
import de.tum.in.www1.exerciseapp.repository.UserRepository;
import de.tum.in.www1.exerciseapp.service.CourseService;
import de.tum.in.www1.exerciseapp.service.UserService;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.inject.Inject;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by muenchdo on 08/06/16.
 */
@Component
@Profile("jira")
@ComponentScan("de.tum.in.www1.exerciseapp.*")
public class JiraAuthenticationProvider implements AuthenticationProvider {

    @Value("${exerciseapp.jira.instructor-group-name}")
    private String INSTRUCTOR_GROUP_NAME;

    @Value("${exerciseapp.jira.url}")
    private URL JIRA_URL;

    @Inject
    UserService userService;

    @Inject
    UserRepository userRepository;

    @Inject
    CourseService courseService;


    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        String username = authentication.getName().toLowerCase();
        String password = authentication.getCredentials().toString();
        HttpEntity<Principal> entity = new HttpEntity<>(HeaderUtil.createAuthorization(username, password));
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> authenticationResponse = null;
        try {
            authenticationResponse = restTemplate.exchange(
                JIRA_URL + "/rest/api/2/user?username=" + username + "&expand=groups",
                HttpMethod.GET,
                entity,
                Map.class);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 401) {
                throw new BadCredentialsException("Wrong credentials");
            } else if (e.getStatusCode().is5xxServerError()) {
                throw new ProviderNotFoundException("Could not authenticate via JIRA");
            }
        }
        if (authenticationResponse != null) {
            Map content = authenticationResponse.getBody();
            User user = userRepository.findOneByLogin((String) content.get("name")).orElseGet(() -> {
                User newUser = userService.createUserInformation((String) content.get("name"), "",
                    (String) content.get("displayName"), "", (String) content.get("emailAddress"),
                    "en");
                return newUser;
            });
            user.setGroups(getGroupStrings((ArrayList) ((Map) content.get("groups")).get("items")));
            user.setAuthorities(buildAuthoritiesFromGroups(getGroupStrings((ArrayList) ((Map) content.get("groups")).get("items"))));
            userRepository.save(user);
            if (!user.getActivated()) {
                userService.activateRegistration(user.getActivationKey());
            }
            UsernamePasswordAuthenticationToken token;
            Optional<User> matchingUser = userService.getUserWithAuthoritiesByLogin(username);
            if (matchingUser.isPresent()) {
                User user1 = matchingUser.get();
                List<GrantedAuthority> grantedAuthorities = user1.getAuthorities().stream()
                    .map(authority -> new SimpleGrantedAuthority(authority.getName()))
                    .collect(Collectors.toList());
                token = new UsernamePasswordAuthenticationToken(user1.getLogin(), user1.getPassword(), grantedAuthorities);
                return token;
            } else {
                throw new UsernameNotFoundException("User " + username + " was not found in the " +
                    "database");
            }
        } else {
            throw new InternalAuthenticationServiceException("JIRA Authentication failed");
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return true;
    }

    /**
     * Flattens the given given list of group maps into a list of strings.
     */
    private List<String> getGroupStrings(List<Map> groups) {
        return groups.stream()
            .parallel()
            .map(g -> (String) g.get("name"))
            .collect(Collectors.toList());
    }

    /**
     * Builds the authorities list from the groups:
     * group contains configured instructor group name -> instructor role
     * otherwise                                       -> student role
     */
    private Set<Authority> buildAuthoritiesFromGroups(List<String> groups) {
        Set<Authority> authorities = new HashSet<>();

        // Check if user is instructor
        if (groups.contains(INSTRUCTOR_GROUP_NAME)) {
            Authority adminAuthority = new Authority();
            adminAuthority.setName(AuthoritiesConstants.ADMIN);
            authorities.add(adminAuthority);
        }

        // Check if user is a tutor in any course
        if (groups.stream().anyMatch(g -> courseService.getAllTeachingAssistantGroupNames().contains(g))) {
            Authority taAuthority = new Authority();
            taAuthority.setName(AuthoritiesConstants.TEACHING_ASSISTANT);
            authorities.add(taAuthority);
        }

        Authority userAuthority = new Authority();
        userAuthority.setName(AuthoritiesConstants.USER);
        authorities.add(userAuthority);
        return authorities;
    }
}
