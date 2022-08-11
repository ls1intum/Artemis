package de.tum.in.www1.artemis.service.connectors.jira;

import static de.tum.in.www1.artemis.config.Constants.ARTEMIS_GROUP_DEFAULT_PREFIX;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exception.ArtemisAuthenticationException;
import de.tum.in.www1.artemis.exception.GroupAlreadyExistsException;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProviderImpl;
import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;
import de.tum.in.www1.artemis.service.connectors.jira.dto.JiraUserDTO;
import de.tum.in.www1.artemis.service.connectors.jira.dto.JiraUserDTO.JiraUserGroupDTO;
import de.tum.in.www1.artemis.service.ldap.LdapUserService;
import de.tum.in.www1.artemis.service.user.AuthorityService;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.service.user.UserCreationService;
import de.tum.in.www1.artemis.web.rest.errors.CaptchaRequiredException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

@Component
@Profile("jira")
@Primary
@ComponentScan("de.tum.in.www1.artemis.*")
public class JiraAuthenticationProvider extends ArtemisAuthenticationProviderImpl implements ArtemisAuthenticationProvider {

    private final Logger log = LoggerFactory.getLogger(JiraAuthenticationProvider.class);

    @Value("${artemis.user-management.external.url}")
    private URL jiraUrl;

    private final RestTemplate restTemplate;

    private final RestTemplate shortTimeoutRestTemplate;

    private final Optional<LdapUserService> ldapUserService;

    private final AuthorityService authorityService;

    public JiraAuthenticationProvider(UserRepository userRepository, @Qualifier("jiraRestTemplate") RestTemplate restTemplate,
            @Qualifier("shortTimeoutJiraRestTemplate") RestTemplate shortTimeoutRestTemplate, Optional<LdapUserService> ldapUserService, PasswordService passwordService,
            AuthorityService authorityService, UserCreationService userCreationService) {
        super(userRepository, passwordService, userCreationService);
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
        this.restTemplate = restTemplate;
        this.ldapUserService = ldapUserService;
        this.authorityService = authorityService;
    }

    /**
     * @return the health of the connected JIRA instance
     */
    public ConnectorHealth health() {
        ConnectorHealth health;
        try {
            final var status = shortTimeoutRestTemplate.getForObject(jiraUrl + "/status", JsonNode.class);
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
        if (user != null) {
            return new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword(), user.getGrantedAuthorities());
        }
        return null;
    }

    @Override
    public User getOrCreateUser(Authentication authentication, String firstName, String lastName, String email, boolean skipPasswordCheck) {
        // NOTE: firstName, lastName, email is not needed in this case since we always get these values from Jira
        return getOrCreateUser(authentication, skipPasswordCheck);
    }

    private User getOrCreateUser(Authentication authentication, Boolean skipPasswordCheck) {
        String username = authentication.getName().toLowerCase();
        String password = authentication.getCredentials().toString();

        final var optionalUser = userRepository.findOneWithGroupsAndAuthoritiesByLogin(username);
        if (optionalUser.isPresent() && optionalUser.get().isInternal()) {
            // User found but is internal. Skip external authentication.
            return null;
        }

        // User is either not yet existent or an external user

        ResponseEntity<JiraUserDTO> authenticationResponse = null;
        try {
            final var path = jiraUrl + "/rest/api/2/user?username=" + username + "&expand=groups";
            // If we want to skip the password check, we can just use the ADMIN auth, which is already injected in the default restTemplate
            // Otherwise, we create our own authorization and use the credentials of the user.
            if (skipPasswordCheck) {
                // this is only the case if the systems wants to log in a user automatically (e.g. based on Oauth in LTI)
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
            // If the user has already existed, the check has already been completed and we can continue
            // Otherwise, we have to create it in the Artemis database
            User user = optionalUser.orElseGet(() -> userCreationService.createUser(jiraUserDTO.getName(), null, null, jiraUserDTO.getDisplayName(), "",
                    jiraUserDTO.getEmailAddress(), null, null, "en", false));
            // load additional details if the ldap service is available and the user follows the allowed username pattern (if specified)
            ldapUserService.ifPresent(service -> service.loadUserDetailsFromLdap(user));

            final var groups = jiraUserDTO.getGroups().getItems().stream().map(JiraUserGroupDTO::getName).collect(Collectors.toSet());
            user.setGroups(groups);
            user.setAuthorities(authorityService.buildAuthorities(user));

            if (!user.getActivated()) {
                user.setActivated(true);
                user.setActivationKey(null);
            }
            return userCreationService.saveUser(user);
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
     * Adds a JIRA user to a JIRA group. Ignores "user is already a member of" errors.
     *
     * @param user  The user
     * @param group The JIRA group name
     * @throws ArtemisAuthenticationException if JIRA returns an error
     */
    @Override
    public void addUserToGroup(User user, String group) throws ArtemisAuthenticationException {
        // then we also make sure to add it into JIRA so that the synchronization during the next login does not remove the group again
        log.debug("Add user {} to group {} in JIRA", user.getLogin(), group);
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
            log.error("Could not add user {} to JIRA group {}. Error: {}", user.getLogin(), group, e.getMessage());
            throw new ArtemisAuthenticationException("Error while adding " + user.getLogin() + " to JIRA group " + group, e);
        }
    }

    @Override
    public void createUserInExternalUserManagement(User user) {
        log.info("Try to create user {} in JIRA", user.getLogin());
        final var body = new JiraUserDTO(user.getLogin(), user.getLogin(), user.getName(), user.getEmail(), List.of("jira-software"));
        HttpEntity<?> entity = new HttpEntity<>(body);
        try {
            restTemplate.exchange(jiraUrl + "/rest/api/2/user", HttpMethod.POST, entity, Void.class);
            log.info("Creating user {} was successful", user.getLogin());
        }
        catch (HttpClientErrorException e) {
            // ignore the error if the user cannot be created, this can e.g. happen if the user already exists in the external user management system
            if (e.getStatusCode().equals(HttpStatus.BAD_REQUEST) && e.getResponseBodyAsString().contains("user with that username already exists")) {
                log.info("User {} already exists in JIRA", user.getLogin());
            }
            else {
                log.warn("Could not create user {} in JIRA. Error: {}", user.getLogin(), e.getMessage());
            }
        }
    }

    // NOTE: We currently cannot support the creation of new users in JIRA, so we cannot update their groups
    // The reason is that JIRA is using the readonly LDAP user directory to the TUM on the production server as first choice
    // and the internal directory as second choice. However, users can only be created in the first user directory and there is no option
    // to create them in the second user directory

    @Override
    public void createGroup(String groupName) {
        log.info("Create group {} in JIRA", groupName);
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
        log.info("Delete group {} in JIRA", groupName);
        try {
            restTemplate.exchange(jiraUrl + "/rest/api/2/group?groupname=" + groupName, HttpMethod.DELETE, null, Void.class);
        }
        catch (HttpClientErrorException e) {
            if (e.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                // ignore the error if the group does not exist
            }
            else {
                log.error("Could not delete group {} in JIRA. Error: {}", groupName, e.getMessage());
            }
        }
    }

    @Override
    public void removeUserFromGroup(User user, String group) {
        // then we also make sure to remove it in JIRA so that the synchronization during the next login does not add the group again
        log.debug("Remove user {} from group {}", user.getLogin(), group);
        try {
            final var path = UriComponentsBuilder.fromUri(jiraUrl.toURI()).path("/rest/api/2/group/user").queryParam("groupname", group).queryParam("username", user.getLogin())
                    .build().toUri();
            restTemplate.delete(path);
        }
        catch (HttpClientErrorException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                log.warn("Could not delete user {} from group {} as the user doesn't exist.", user.getLogin(), group);
                return;
            }
            if (HttpStatus.BAD_REQUEST.equals(e.getStatusCode()) && e.getResponseBodyAsString().contains("since user is not a member of")) {
                log.warn("Could not remove user {} from group {} since it is not a member.", user.getLogin(), group);
                return;
            }
            log.error("Could not delete user {} from group {}; Error: {}", user.getLogin(), group, e.getMessage());
            throw new ArtemisAuthenticationException(String.format("Error while deleting user %s from Jira group %s", user.getLogin(), group), e);
        }
        catch (URISyntaxException e) {
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
            log.warn("JIRA group {} does not exit", group);
            if (e.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
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
     * @throws ArtemisAuthenticationException an exception occurred in JIRA and the username for the email address could not be retrieved
     */
    @Override
    public Optional<String> getUsernameForEmail(String email) throws ArtemisAuthenticationException {
        try {
            var authenticationResponse = restTemplate.exchange(jiraUrl + "/rest/api/2/user/search?username=" + email, HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<JiraUserDTO>>() {
                    });

            var results = authenticationResponse.getBody();
            if (results == null || results.isEmpty()) {
                // no result
                return Optional.empty();
            }
            JiraUserDTO firstResult = results.get(0);
            return Optional.of(firstResult.getName());
        }
        catch (HttpClientErrorException e) {
            log.error("Could not get JIRA username for email address {}", email, e);
            throw new ArtemisAuthenticationException("Error while checking eMail address", e);
        }

    }
}
