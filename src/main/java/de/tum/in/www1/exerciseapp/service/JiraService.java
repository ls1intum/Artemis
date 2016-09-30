package de.tum.in.www1.exerciseapp.service;

import java.net.URL;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;


import de.tum.in.www1.exerciseapp.domain.Authority;
import de.tum.in.www1.exerciseapp.domain.User;
import de.tum.in.www1.exerciseapp.exception.JiraException;
import de.tum.in.www1.exerciseapp.repository.UserRepository;
import de.tum.in.www1.exerciseapp.security.AuthoritiesConstants;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.inject.Inject;

/**
 * Created by Josias Montag on 22.09.16.
 */
@Service
@Transactional
@Profile("jira")
public class JiraService implements RemoteUserService {
    private final Logger log = LoggerFactory.getLogger(JiraService.class);


    @Value("${exerciseapp.jira.instructor-group-name}")
    private String INSTRUCTOR_GROUP_NAME;

    @Value("${exerciseapp.jira.url}")
    private URL JIRA_URL;

    @Value("${exerciseapp.bitbucket.url}")
    private URL BITBUCKET_URL;

    @Value("${exerciseapp.jira.user}")
    private String JIRA_USER;

    @Value("${exerciseapp.jira.password}")
    private String JIRA_PASSWORD;


    @Inject
    UserService userService;

    @Inject
    UserRepository userRepository;


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
        if (groups.contains(INSTRUCTOR_GROUP_NAME)) {
            Authority adminAuthority = new Authority();
            adminAuthority.setName(AuthoritiesConstants.ADMIN);
            authorities.add(adminAuthority);
        }
        Authority userAuthority = new Authority();
        userAuthority.setName(AuthoritiesConstants.USER);
        authorities.add(userAuthority);
        return authorities;
    }


    /**
     * Creates a JIRA user with given password.
     *
     * @param username     The wanted JIRA username
     * @param emailAddress The eMail address for the user
     * @param displayName  The display name (full name)
     * @throws JiraException if JIRA returns an error
     */

    public void createUser(String username, String password, String emailAddress, String displayName) throws JiraException {
        HttpHeaders headers = HeaderUtil.createAuthorization(JIRA_USER, JIRA_PASSWORD);
        Map<String, Object> body = new HashMap<>();
        body.put("name", username);
        body.put("emailAddress", emailAddress);
        body.put("password", password);
        body.put("displayName", displayName);
        HttpEntity<?> entity = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(
                JIRA_URL + "/rest/api/2/user",
                HttpMethod.POST,
                entity,
                Map.class);
        } catch (HttpClientErrorException e) {
            log.error("Could not create JIRA user " + username, e);
            throw new JiraException("Error while creating user");
        }

        /**
         * As default JIRA <-> Bitbucket user directory is synced every hour.
         * Unfortunately there is no way to sync the user database via API. See also: https://jira.atlassian.com/browse/BSERV-5108
         *
         * Workaround: "login" to Bitbucket with the new user via any API request. By this the new user is synced to Bitbucket.
         *
         */


        headers = HeaderUtil.createAuthorization(username, password);
        entity = new HttpEntity<>(headers);
        restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(
                BITBUCKET_URL + "/rest/api/1.0/repos",
                HttpMethod.GET,
                entity,
                Map.class);
        } catch (HttpClientErrorException e) {
            // ignore
        }


    }

    /**
     * Adds a JIRA user to a JIRA group. Ignores "user is already a member of" errors.
     *
     * @param username The JIRA username
     * @param group    The JIRA group name
     * @throws JiraException if JIRA returns an error
     */
    public void addUserToGroup(String username, String group) throws JiraException {
        HttpHeaders headers = HeaderUtil.createAuthorization(JIRA_USER, JIRA_PASSWORD);
        Map<String, Object> body = new HashMap<>();
        body.put("name", username);
        HttpEntity<?> entity = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(
                JIRA_URL + "/rest/api/2/group/user?groupname=" + group,
                HttpMethod.POST,
                entity,
                Map.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().equals(HttpStatus.BAD_REQUEST)
                && e.getResponseBodyAsString().contains("user is already a member of")) {
                // ignore the error if the user is already in the group
                return;
            }
            log.error("Could not add JIRA user to group " + group, e);
            throw new JiraException("Error while adding user to group");
        }
    }


}
