package de.tum.in.www1.artemis.security;

import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exception.ArtemisAuthenticationException;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.web.rest.errors.CaptchaRequiredException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URL;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by muenchdo on 08/06/16.
 */
@Component
@Profile("jira")
@ComponentScan("de.tum.in.www1.artemis.*")
public class JiraAuthenticationProvider implements ArtemisAuthenticationProvider {

    private final Logger log = LoggerFactory.getLogger(JiraAuthenticationProvider.class);

    @Value("${artemis.jira.admin-group-name}")
    private String ADMIN_GROUP_NAME;

    @Value("${artemis.jira.url}")
    private URL JIRA_URL;

    @Value("${artemis.jira.user}")
    private String JIRA_USER;

    @Value("${artemis.jira.password}")
    private String JIRA_PASSWORD;

    private final UserService userService;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;

    public JiraAuthenticationProvider(UserService userService, UserRepository userRepository, CourseRepository courseRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        User user = getOrCreateUser(authentication, false);
        List<GrantedAuthority> grantedAuthorities = user.getAuthorities().stream()
            .map(authority -> new SimpleGrantedAuthority(authority.getName()))
            .collect(Collectors.toList());
        return new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword(), grantedAuthorities);
    }

    /**
     * Gets or creates the user object for an JIRA user.
     *
     * @param authentication
     * @param skipPasswordCheck     Skip checking the password
     * @return
     */
    @Override
    public User getOrCreateUser(Authentication authentication, Boolean skipPasswordCheck) {
        String username = authentication.getName().toLowerCase();
        String password = authentication.getCredentials().toString();
        HttpEntity<Principal> entity = new HttpEntity<>(!skipPasswordCheck ? HeaderUtil.createAuthorization(username, password) : HeaderUtil.createAuthorization(JIRA_USER, JIRA_PASSWORD));
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> authenticationResponse = null;
        try {
            authenticationResponse = restTemplate.exchange(JIRA_URL + "/rest/api/2/user?username=" + username + "&expand=groups", HttpMethod.GET, entity, Map.class);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                // If JIRA requires a CAPTCHA. Communicate this to the client
                if (e.getResponseHeaders().containsKey("X-Authentication-Denied-Reason")) {
                    String authenticationDeniedReason = e.getResponseHeaders().get("X-Authentication-Denied-Reason").get(0);
                    if (authenticationDeniedReason.toLowerCase().contains("captcha")) {
                        throw new CaptchaRequiredException("CAPTCHA required");
                    }
                }

                // Otherwise, the user used the wrong credentials
                throw new BadCredentialsException("Wrong credentials");
            }
            else if (e.getStatusCode().is5xxServerError()) {
                throw new ProviderNotFoundException("Could not authenticate via JIRA");
            }
        }

        if (authenticationResponse != null) {
            Map content = authenticationResponse.getBody();
            User user = userRepository.findOneByLogin((String) content.get("name")).orElseGet(() ->
                userService.createUser((String) content.get("name"), "", (String) content.get("displayName"), "", (String) content.get("emailAddress"), null, "en"));
            user.setGroups(getGroupStrings((ArrayList) ((Map) content.get("groups")).get("items")));
            user.setAuthorities(buildAuthoritiesFromGroups(getGroupStrings((ArrayList) ((Map) content.get("groups")).get("items"))));
            userRepository.save(user);


            if (!user.getActivated()) {
                userService.activateRegistration(user.getActivationKey());
            }

            Optional<User> matchingUser = userService.getUserWithAuthoritiesByLogin(username);
            if (matchingUser.isPresent()) {
                return matchingUser.get();
            } else {
                throw new UsernameNotFoundException("User " + username + " was not found in the database");
            }
        } else {
            throw new InternalAuthenticationServiceException("JIRA Authentication failed for user " + username);
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

        // Check if user is admin
        if (groups.contains(ADMIN_GROUP_NAME)) {
            Authority adminAuthority = new Authority();
            adminAuthority.setName(AuthoritiesConstants.ADMIN);
            authorities.add(adminAuthority);
        }

        List<Course> courses = courseRepository.findAll();
        List<String> instructorGroups = courses.stream().map(Course::getInstructorGroupName).collect(Collectors.toList());
        List<String> teachingAssistantGroups = courses.stream().map(Course::getTeachingAssistantGroupName).collect(Collectors.toList());

        // Check if user is an instructor in any course
        if (groups.stream().anyMatch(instructorGroups::contains)) {
            Authority instructorAuthority = new Authority();
            instructorAuthority.setName(AuthoritiesConstants.INSTRUCTOR);
            authorities.add(instructorAuthority);
        }

        // Check if user is a tutor in any course
        if (groups.stream().anyMatch(teachingAssistantGroups::contains)) {
            Authority taAuthority = new Authority();
            taAuthority.setName(AuthoritiesConstants.TEACHING_ASSISTANT);
            authorities.add(taAuthority);
        }

        Authority userAuthority = new Authority();
        userAuthority.setName(AuthoritiesConstants.USER);
        authorities.add(userAuthority);
        return authorities;
    }

    /**
     * Adds a JIRA user to a JIRA group. Ignores "user is already a member of" errors.
     *
     * @param username The JIRA username
     * @param group The JIRA group name
     * @throws ArtemisAuthenticationException if JIRA returns an error
     */
    @Override
    public void addUserToGroup(String username, String group) throws ArtemisAuthenticationException {
        HttpHeaders headers = HeaderUtil.createAuthorization(JIRA_USER, JIRA_PASSWORD);
        Map<String, Object> body = new HashMap<>();
        body.put("name", username);
        HttpEntity<?> entity = new HttpEntity<>(body,headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(
                JIRA_URL + "/rest/api/2/group/user?groupname=" + group,
                HttpMethod.POST,
                entity,
                Map.class);
        } catch (HttpClientErrorException e) {
            if(e.getStatusCode().equals(HttpStatus.BAD_REQUEST)
                && e.getResponseBodyAsString().contains("user is already a member of")) {
                // ignore the error if the user is already in the group
                return;
            }
            log.error("Could not add JIRA user to group " + group, e);
            throw new ArtemisAuthenticationException("Error while adding user to JIRA group");
        }
    }

    /**
     *
     * Checks if the group exists in JIRA to avoid specifying a group that does not exist
     *
     * @param group
     * @return
     */
    @Override
    public Boolean checkIfGroupExists(String group) {
        HttpHeaders headers = HeaderUtil.createAuthorization(JIRA_USER, JIRA_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                JIRA_URL + "/rest/api/2/group/member?groupname=" + group,
                HttpMethod.GET,
                entity,
                Map.class);
            if (response.getStatusCode().equals(HttpStatus.OK)) {
                return true;
            }
        } catch (HttpClientErrorException e) {
            log.warn("JIRA group " + group + " does not exit");
            if(e.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                return false;
            }
        }
        return false;
    }

    /**
     * Checks if an JIRA user for the given email address exists.
     *
     * @param email The JIRA user email address
     * @return Optional String of JIRA username
     * @throws ArtemisAuthenticationException
     */
    @Override
    public Optional<String> getUsernameForEmail(String email) throws ArtemisAuthenticationException {
        HttpHeaders headers = HeaderUtil.createAuthorization(JIRA_USER, JIRA_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<ArrayList> authenticationResponse = restTemplate.exchange(
                JIRA_URL + "/rest/api/2/user/search?username=" + email,
                HttpMethod.GET,
                entity,
                ArrayList.class);


            ArrayList results = authenticationResponse.getBody();
            if(results.size() == 0) {
                // no result
                return Optional.empty();
            }
            Map firstResult = (Map) results.get(0);
            return Optional.of((String) firstResult.get("name"));
        } catch (HttpClientErrorException e) {
            log.error("Could not get JIRA username for email address " + email, e);
            throw new ArtemisAuthenticationException("Error while checking eMail address");
        }

    }
}
