package de.tum.in.www1.artemis.security;

import static de.tum.in.www1.artemis.config.Constants.TUM_USERNAME_PATTERN;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exception.ArtemisAuthenticationException;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.connectors.jira.dto.JiraUserDTO;
import de.tum.in.www1.artemis.service.connectors.jira.dto.JiraUserDTO.JiraUserGroupDTO;
import de.tum.in.www1.artemis.service.ldap.LdapUserService;
import de.tum.in.www1.artemis.web.rest.errors.CaptchaRequiredException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * Created by muenchdo on 08/06/16.
 */
@Component
@Profile("jira")
@Primary
@ComponentScan("de.tum.in.www1.artemis.*")
public class JiraAuthenticationProvider implements ArtemisAuthenticationProvider {

    private final Logger log = LoggerFactory.getLogger(JiraAuthenticationProvider.class);

    @Value("${artemis.user-management.external.admin-group-name}")
    private String ADMIN_GROUP_NAME;

    @Value("${artemis.user-management.external.url}")
    private URL JIRA_URL;

    private final UserService userService;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final RestTemplate restTemplate;

    private final Optional<LdapUserService> ldapUserService;

    private final AuditEventRepository auditEventRepository;

    public JiraAuthenticationProvider(UserService userService, UserRepository userRepository, CourseRepository courseRepository,
            @Qualifier("jiraRestTemplate") RestTemplate restTemplate, Optional<LdapUserService> ldapUserService, AuditEventRepository auditEventRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.restTemplate = restTemplate;
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

    @Override
    public User getOrCreateUser(Authentication authentication, String firstName, String lastName, String email, boolean skipPasswordCheck) {
        // NOTE: firstName, lastName, email is not needed in this case since we always get these values from Jira
        return getOrCreateUser(authentication, skipPasswordCheck);
    }

    // TODO this method is way too long, split it up
    @SuppressWarnings("unchecked")
    private User getOrCreateUser(Authentication authentication, Boolean skipPasswordCheck) {
        String username = authentication.getName().toLowerCase();
        String password = authentication.getCredentials().toString();
        ResponseEntity<JiraUserDTO> authenticationResponse = null;
        try {
            final var path = JIRA_URL + "/rest/api/2/user?username=" + username + "&expand=groups";
            // If we want to skip the password check, we can just use the ADMIN auth, which is already injected in the default restTemplate
            // Otherwise, we create our own authorization and use the credentials of the user.
            if (skipPasswordCheck) {
                authenticationResponse = restTemplate.exchange(path, HttpMethod.GET, null, JiraUserDTO.class);
            }
            else {
                final var entity = new HttpEntity<>(HeaderUtil.createAuthorization(username, password));
                authenticationResponse = restTemplate.exchange(path, HttpMethod.GET, entity, JiraUserDTO.class);
            }
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

        if (authenticationResponse != null && authenticationResponse.getBody() != null) {
            final var content = authenticationResponse.getBody();
            final var login = content.getName();
            final var emailAddress = content.getEmailAddress();
            // Use empty password, so that we don't store the credentials of Jira users in the Artemis DB
            final var user = userRepository.findOneByLogin(login).orElseGet(() -> userService.createUser(login, "", content.getDisplayName(), "", emailAddress, null, "en"));
            final var groupStrings = content.getGroups().getItems().stream().map(JiraUserGroupDTO::getName).collect(Collectors.toSet());

            user.setEmail(emailAddress);
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
        if (!isGroupAvailable(group)) {
            throw new IllegalArgumentException("Jira does not have a group: " + group);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("name", username);
        HttpEntity<?> entity = new HttpEntity<>(body);
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

    @Override
    public void removeUserFromGroup(String username, String group) {
        log.info("Remove user {} from group {}", username, group);
        try {
            final var path = UriComponentsBuilder.fromUri(JIRA_URL.toURI()).path("/rest/api/2/group/user").queryParam("groupname", group).queryParam("username", username).build()
                    .toUri();
            restTemplate.delete(path);
        }
        catch (HttpClientErrorException | URISyntaxException e) {
            log.error("Could not delete user {} from group {}; Error: {}", username, group, e.getMessage());
            throw new ArtemisAuthenticationException(String.format("Error while deleting user %s from Jira group %s", username, group), e);
        }
    }

    @Override
    public boolean isGroupAvailable(String group) {
        try {
            ResponseEntity<Map> response = restTemplate.exchange(JIRA_URL + "/rest/api/2/group/member?groupname=" + group, HttpMethod.GET, null, Map.class);
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
        try {
            ResponseEntity<ArrayList> authenticationResponse = restTemplate.exchange(JIRA_URL + "/rest/api/2/user/search?username=" + email, HttpMethod.GET, null, ArrayList.class);

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
