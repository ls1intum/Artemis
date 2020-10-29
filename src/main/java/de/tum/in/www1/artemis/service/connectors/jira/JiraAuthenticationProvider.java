package de.tum.in.www1.artemis.service.connectors.jira;

import static de.tum.in.www1.artemis.config.Constants.ARTEMIS_GROUP_DEFAULT_PREFIX;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;
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
import org.springframework.core.ParameterizedTypeReference;
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

import com.fasterxml.jackson.databind.JsonNode;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exception.ArtemisAuthenticationException;
import de.tum.in.www1.artemis.exception.GroupAlreadyExistsException;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProviderImpl;
import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;
import de.tum.in.www1.artemis.service.connectors.jira.dto.JiraUserDTO;
import de.tum.in.www1.artemis.service.connectors.jira.dto.JiraUserDTO.JiraUserGroupDTO;
import de.tum.in.www1.artemis.service.ldap.LdapUserDto;
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
public class JiraAuthenticationProvider extends ArtemisAuthenticationProviderImpl implements ArtemisAuthenticationProvider {

    private final Logger log = LoggerFactory.getLogger(JiraAuthenticationProvider.class);

    @Value("${artemis.user-management.external.url}")
    private URL jiraUrl;

    @Value("${artemis.user-management.ldap.allowed-username-pattern:#{null}}")
    private Optional<Pattern> allowedLdapUsernamePattern;

    private final RestTemplate restTemplate;

    private final Optional<LdapUserService> ldapUserService;

    private final AuditEventRepository auditEventRepository;

    public JiraAuthenticationProvider(UserRepository userRepository, @Qualifier("jiraRestTemplate") RestTemplate restTemplate, Optional<LdapUserService> ldapUserService,
            AuditEventRepository auditEventRepository) {
        super(userRepository);
        this.restTemplate = restTemplate;
        this.ldapUserService = ldapUserService;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * @return the health of the connected JIRA instance
     */
    public ConnectorHealth health() {
        ConnectorHealth health;
        try {
            final var status = restTemplate.getForObject(jiraUrl + "/status", JsonNode.class);
            health = status.get("state").asText().equals("RUNNING") ? new ConnectorHealth(true) : new ConnectorHealth(false);
        }
        catch (Exception emAll) {
            health = new ConnectorHealth(emAll);
        }

        health.setAdditionalInfo(Map.of("url", jiraUrl));
        return health;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        User user = getOrCreateUser(authentication, false);
        List<GrantedAuthority> grantedAuthorities = user.getAuthorities().stream().map(authority -> new SimpleGrantedAuthority(authority.getName())).collect(Collectors.toList());
        return new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword(), grantedAuthorities);
    }

    @Override
    public User getOrCreateUser(Authentication authentication, String firstName, String lastName, String email, boolean skipPasswordCheck) {
        // NOTE: firstName, lastName, email is not needed in this case since we always get these values from Jira
        return getOrCreateUser(authentication, skipPasswordCheck);
    }

    private User getOrCreateUser(Authentication authentication, Boolean skipPasswordCheck) {
        String username = authentication.getName().toLowerCase();
        String password = authentication.getCredentials().toString();
        ResponseEntity<JiraUserDTO> authenticationResponse = null;
        try {
            final var path = jiraUrl + "/rest/api/2/user?username=" + username + "&expand=groups";
            // If we want to skip the password check, we can just use the ADMIN auth, which is already injected in the default restTemplate
            // Otherwise, we create our own authorization and use the credentials of the user.
            if (skipPasswordCheck) {
                // this is only the case if the systems wants to login a user automatically (e.g. based on Oauth in LTI)
                // when we provide null, the default restTemplate header will be used automatically
                authenticationResponse = restTemplate.exchange(path, HttpMethod.GET, null, JiraUserDTO.class);
            }
            else {
                // this is the normal case, where we use the username and password provided by the user so that JIRA checks for us if this is valid
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
            final var jiraUserDTO = authenticationResponse.getBody();
            username = jiraUserDTO.getName();
            User user;
            final var optionalUser = userRepository.findOneWithGroupsAndAuthoritiesByLogin(username);
            if (optionalUser.isPresent()) {
                user = optionalUser.get();
            }
            else {
                // the user does not exist yet, we have to create it in the Artemis database
                // Note: we use an empty password, so that we don't store the credentials of Jira users in the Artemis DB (Spring enforces a non null password)
                user = userService.createUser(username, "", jiraUserDTO.getDisplayName(), "", jiraUserDTO.getEmailAddress(), null, null, "en");
                // load additional details if the ldap service is available and the user follows the allowed username pattern (if specified)
                if (ldapUserService.isPresent()) {
                    loadUserDetailsFromLdap(user);
                }
            }
            final var groups = jiraUserDTO.getGroups().getItems().stream().map(JiraUserGroupDTO::getName).collect(Collectors.toSet());
            user.setGroups(groups);
            user.setAuthorities(userService.buildAuthorities(user));

            if (!user.getActivated()) {
                userService.activateUser(user);
            }
            userRepository.save(user);
            return user;
        }
        else {
            throw new InternalAuthenticationServiceException("JIRA Authentication failed for user " + username);
        }
    }

    /**
     * loads the user details from the provided LDAP in case:
     * 1) the allowedUsernamePattern is not specified (means all users should be loaded) or
     * 2) the allowedUsernamePattern is specified and the username matches
     * Example for TUM: ab12cde
     * @param user the user for which the additional details (in particular the registration number should be loaded)
     */
    private void loadUserDetailsFromLdap(User user) {
        if (allowedLdapUsernamePattern.isEmpty() || allowedLdapUsernamePattern.get().matcher(user.getLogin()).matches()) {
            LdapUserDto ldapUserDto = userService.loadUserDetailsFromLdap(user.getLogin());
            if (ldapUserDto != null) {
                user.setFirstName(ldapUserDto.getFirstName());
                user.setLastName(ldapUserDto.getLastName());
                user.setEmail(ldapUserDto.getEmail());
                user.setRegistrationNumber(ldapUserDto.getRegistrationNumber());
            }
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return true;
    }

    /**
     * Adds a JIRA user to a JIRA group. Ignores "user is already a member of" errors.
     *
     * @param user     The user
     * @param group    The JIRA group name
     * @throws ArtemisAuthenticationException if JIRA returns an error
     */
    @Override
    public void addUserToGroup(User user, String group) throws ArtemisAuthenticationException {
        // we first add the user to the group in the Artemis database
        super.addUserToGroup(user, group);
        // then we also make sure to add it into JIRA so that the synchronization during the next login does not remove the group again
        log.info("Add user " + user.getLogin() + " to group " + group + " in JIRA");
        if (!isGroupAvailable(group)) {
            throw new IllegalArgumentException("Jira does not have a group: " + group);
        }

        final var body = new JiraUserDTO(user.getLogin());
        HttpEntity<?> entity = new HttpEntity<>(body);
        try {
            restTemplate.exchange(jiraUrl + "/rest/api/2/group/user?groupname=" + group, HttpMethod.POST, entity, Void.class);
        }
        catch (HttpClientErrorException e) {
            if (e.getStatusCode().equals(HttpStatus.BAD_REQUEST) && e.getResponseBodyAsString().contains("user is already a member of")) {
                // ignore the error if the user is already in the group
                return;
            }
            log.error("Could not add user " + user.getLogin() + " to JIRA group " + group + ". Error: " + e.getMessage());
            throw new ArtemisAuthenticationException("Error while adding " + user.getLogin() + " to JIRA group " + group, e);
        }
    }

    @Override
    public void createUserInExternalUserManagement(User user) {
        log.info("Try to create user " + user.getLogin() + " in JIRA");
        final var body = new JiraUserDTO(user.getLogin(), user.getLogin(), user.getName(), user.getEmail(), List.of("jira-software"));
        HttpEntity<?> entity = new HttpEntity<>(body);
        try {
            restTemplate.exchange(jiraUrl + "/rest/api/2/user", HttpMethod.POST, entity, Void.class);
            log.info("Creating user " + user.getLogin() + " was successful");
        }
        catch (HttpClientErrorException e) {
            // ignore the error if the user cannot be created, this can e.g. happen if the user already exists in the external user management system
            if (e.getStatusCode().equals(HttpStatus.BAD_REQUEST) && e.getResponseBodyAsString().contains("user with that username already exists")) {
                log.info("User " + user.getLogin() + " already exists in JIRA");
            }
            else {
                log.warn("Could not create user " + user.getLogin() + " in JIRA. Error: " + e.getMessage());
            }
        }
    }

    @Override
    public void addUserToGroups(User user, Set<String> groups) {
        // NOTE: this method should only be invoked for newly created users.
        // We currently cannot support the creation of new users in JIRA, so we cannot update their groups
        // The reason is that JIRA is using the readonly LDAP user directory to the TUM on the production server as first choice
        // and the internal directory as second choice. However, users can only be created in the first user directory and there is no option
        // to create them in the second user directory
    }

    @Override
    public void createGroup(String groupName) {
        log.info("Create group " + groupName + " in JIRA");
        final var body = new JiraUserDTO(groupName);
        HttpEntity<?> entity = new HttpEntity<>(body);
        try {
            restTemplate.exchange(jiraUrl + "/rest/api/2/group", HttpMethod.POST, entity, Void.class);
        }
        catch (HttpClientErrorException e) {
            // forward this error to the client because we might not want to create the course, if the group already exists
            // this prevents problems when multiple Artemis instances use the same JIRA server
            throw new GroupAlreadyExistsException("Error while creating group " + groupName + " in JIRA", e);
        }
    }

    @Override
    public void deleteGroup(String groupName) {
        // Important: only delete groups that have been created from artemis
        // we do not want to delete common groups such as tumuser or artemisdev if a course on the test server is deleted
        if (!groupName.startsWith(ARTEMIS_GROUP_DEFAULT_PREFIX)) {
            return;
        }
        log.info("Delete group " + groupName + " in JIRA");
        try {
            restTemplate.exchange(jiraUrl + "/rest/api/2/group?groupname=" + groupName, HttpMethod.DELETE, null, Void.class);
        }
        catch (HttpClientErrorException e) {
            if (e.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                // ignore the error if the group does not exist
            }
            else {
                log.error("Could not delete group " + groupName + " in JIRA. Error: " + e.getMessage());
            }
        }
        userService.removeGroupFromUsers(groupName);
    }

    @Override
    public void removeUserFromGroup(User user, String group) {
        // we first remove the user from the group in the Artemis database
        super.removeUserFromGroup(user, group);
        // then we also make sure to remove it in JIRA so that the synchronization during the next login does not add the group again
        log.info("Remove user {} from group {}", user.getLogin(), group);
        try {
            final var path = UriComponentsBuilder.fromUri(jiraUrl.toURI()).path("/rest/api/2/group/user").queryParam("groupname", group).queryParam("username", user.getLogin())
                    .build().toUri();
            restTemplate.delete(path);
        }
        catch (HttpClientErrorException | URISyntaxException e) {
            log.error("Could not delete user {} from group {}; Error: {}", user.getLogin(), group, e.getMessage());
            throw new ArtemisAuthenticationException(String.format("Error while deleting user %s from Jira group %s", user.getLogin(), group), e);
        }
    }

    @Override
    public boolean isGroupAvailable(String group) {
        try {
            ResponseEntity<Void> response = restTemplate.exchange(jiraUrl + "/rest/api/2/group/member?groupname=" + group, HttpMethod.GET, null, Void.class);
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
            addUserToGroup(user, courseStudentGroupName);
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
            var authenticationResponse = restTemplate.exchange(jiraUrl + "/rest/api/2/user/search?username=" + email, HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<JiraUserDTO>>() {
                    });

            var results = authenticationResponse.getBody();
            if (results == null || results.size() == 0) {
                // no result
                return Optional.empty();
            }
            JiraUserDTO firstResult = results.get(0);
            return Optional.of(firstResult.getName());
        }
        catch (HttpClientErrorException e) {
            log.error("Could not get JIRA username for email address " + email, e);
            throw new ArtemisAuthenticationException("Error while checking eMail address", e);
        }

    }
}
