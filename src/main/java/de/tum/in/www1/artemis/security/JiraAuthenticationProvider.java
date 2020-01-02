package de.tum.in.www1.artemis.security;

import static de.tum.in.www1.artemis.config.Constants.TUM_USERNAME_PATTERN;

import java.net.URL;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
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
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exception.ArtemisAuthenticationException;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.ldap.LdapUserService;
import de.tum.in.www1.artemis.web.rest.errors.CaptchaRequiredException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

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

    private final Optional<LdapUserService> ldapUserService;

    private final AuditEventRepository auditEventRepository;

    public JiraAuthenticationProvider(UserService userService, UserRepository userRepository, CourseRepository courseRepository, Optional<LdapUserService> ldapUserService,
            AuditEventRepository auditEventRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.ldapUserService = ldapUserService;
        this.auditEventRepository = auditEventRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        User user = getOrCreateUser(authentication, false);

        // load additional details if the ldap service is available and the registration number is not available and if the user follows the TUM pattern
        if (ldapUserService.isPresent() && user.getRegistrationNumber() == null && TUM_USERNAME_PATTERN.matcher(user.getLogin()).matches()) {
            long start = System.currentTimeMillis();
            userService.loadUserDetailsFromLdap(user);
            long end = System.currentTimeMillis();
            log.info("LDAP search took " + (end - start) + "ms");
        }
        List<GrantedAuthority> grantedAuthorities = user.getAuthorities().stream().map(authority -> new SimpleGrantedAuthority(authority.getName())).collect(Collectors.toList());
        return new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword(), grantedAuthorities);
    }

    /**
     * Gets or creates the user object for an JIRA user.
     *
     * @param authentication the Spring authentication object which includes the username and password
     * @param skipPasswordCheck whether the password check on JIRA should be skipped
     * @return
     */
    @Override
    @SuppressWarnings("unchecked")
    public User getOrCreateUser(Authentication authentication, Boolean skipPasswordCheck) {
        String username = authentication.getName().toLowerCase();
        String password = authentication.getCredentials().toString();
        HttpEntity<Principal> entity = new HttpEntity<>(
                !skipPasswordCheck ? HeaderUtil.createAuthorization(username, password) : HeaderUtil.createAuthorization(JIRA_USER, JIRA_PASSWORD));
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> authenticationResponse = null;
        try {
            authenticationResponse = restTemplate.exchange(JIRA_URL + "/rest/api/2/user?username=" + username + "&expand=groups", HttpMethod.GET, entity, Map.class);
        }
        catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                // If JIRA requires a CAPTCHA. Communicate this to the client
                if (e.getResponseHeaders() != null && e.getResponseHeaders().containsKey("X-Authentication-Denied-Reason")) {
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
            final String login = (String) content.get("name");
            final String emailAddress = (String) content.get("emailAddress");
            User user = userRepository.findOneByLogin(login).orElseGet(() -> userService.createUser(login, "", (String) content.get("displayName"), "", emailAddress, null, "en"));
            user.setEmail(emailAddress);

            final Set<String> groupStrings = getGroupStrings((ArrayList) ((Map) content.get("groups")).get("items"));
            user.setGroups(groupStrings);
            user.setAuthorities(buildAuthoritiesFromGroups(groupStrings));

            if (!user.getActivated()) {
                userService.activateRegistration(user.getActivationKey());
            }
            userRepository.save(user);

            return user;
        }
        else {
            throw new InternalAuthenticationServiceException("JIRA Authentication failed for user " + username);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return true;
    }

    /**
     * Flattens the given given set of group maps into a list of strings.
     */
    private Set<String> getGroupStrings(List<Map> groups) {
        return groups.stream().parallel().map(group -> (String) group.get("name")).collect(Collectors.toSet());
    }

    /**
     * Builds the authorities list from the groups: group contains configured instructor group name -> instructor role otherwise -> student role
     */
    private Set<Authority> buildAuthoritiesFromGroups(Set<String> groups) {
        Set<Authority> authorities = new HashSet<>();

        // Check if user is admin
        if (groups.contains(ADMIN_GROUP_NAME)) {
            Authority adminAuthority = new Authority();
            adminAuthority.setName(AuthoritiesConstants.ADMIN);
            authorities.add(adminAuthority);
        }

        Set<String> instructorGroups = courseRepository.findAllInstructorGroupNames();
        Set<String> teachingAssistantGroups = courseRepository.findAllTeachingAssistantGroupNames();

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
     * @param group    The JIRA group name
     * @throws ArtemisAuthenticationException if JIRA returns an error
     */
    @Override
    public void addUserToGroup(String username, String group) throws ArtemisAuthenticationException {
        log.info("Add user " + username + " to group " + group + " in JIRA");
        HttpHeaders headers = HeaderUtil.createAuthorization(JIRA_USER, JIRA_PASSWORD);
        Map<String, Object> body = new HashMap<>();
        body.put("name", username);
        HttpEntity<?> entity = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(JIRA_URL + "/rest/api/2/group/user?groupname=" + group, HttpMethod.POST, entity, Map.class);
        }
        catch (HttpClientErrorException e) {
            if (e.getStatusCode().equals(HttpStatus.BAD_REQUEST) && e.getResponseBodyAsString().contains("user is already a member of")) {
                // ignore the error if the user is already in the group
                return;
            }
            log.error("Could not add user " + username + " to JIRA group " + group + ". Error: " + e.getMessage());
            throw new ArtemisAuthenticationException("Error while adding " + username + " to JIRA group " + group, e);
        }
    }

    /**
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
            ResponseEntity<Map> response = restTemplate.exchange(JIRA_URL + "/rest/api/2/group/member?groupname=" + group, HttpMethod.GET, entity, Map.class);
            if (response.getStatusCode().equals(HttpStatus.OK)) {
                return true;
            }
        }
        catch (HttpClientErrorException e) {
            log.warn("JIRA group " + group + " does not exit");
            if (e.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                return false;
            }
        }
        return false;
    }

    @Override
    public void registerUserForCourse(User user, Course course) {
        String courseStudentGroupName = course.getStudentGroupName();
        if (!user.getGroups().contains(courseStudentGroupName)) {
            Set<String> groups = user.getGroups();
            groups.add(courseStudentGroupName);
            user.setGroups(groups);
            userRepository.save(user);
            var auditEvent = new AuditEvent(user.getLogin(), Constants.REGISTER_FOR_COURSE, "course=" + course.getTitle());
            auditEventRepository.add(auditEvent);
            log.info("User " + user.getLogin() + " has successfully registered for course " + course.getTitle());
        }
        try {
            addUserToGroup(user.getLogin(), courseStudentGroupName);
        }
        catch (ArtemisAuthenticationException e) {
            /*
             * This might throw exceptions, for example if the group does not exist on the authentication service. TODO: At the moment we ignore them, but it would be better to
             * handle them properly
             */
        }
    }

    /**
     * Checks if an JIRA user for the given email address exists.
     *
     * @param email The JIRA user email address
     * @return Optional String of JIRA username
     * @throws ArtemisAuthenticationException an exception occurred in JIRA and the username for the email address could not be retrieved
     */
    @Override
    public Optional<String> getUsernameForEmail(String email) throws ArtemisAuthenticationException {
        HttpHeaders headers = HeaderUtil.createAuthorization(JIRA_USER, JIRA_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<ArrayList> authenticationResponse = restTemplate.exchange(JIRA_URL + "/rest/api/2/user/search?username=" + email, HttpMethod.GET, entity,
                    ArrayList.class);

            var results = authenticationResponse.getBody();
            if (results == null || results.size() == 0) {
                // no result
                return Optional.empty();
            }
            Map firstResult = (Map) results.get(0);
            return Optional.of((String) firstResult.get("name"));
        }
        catch (HttpClientErrorException e) {
            log.error("Could not get JIRA username for email address " + email, e);
            throw new ArtemisAuthenticationException("Error while checking eMail address", e);
        }

    }
}
