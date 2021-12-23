package de.tum.in.www1.artemis.service.connectors.bitbucket;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.exception.BitbucketException;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.UrlService;
import de.tum.in.www1.artemis.service.connectors.*;
import de.tum.in.www1.artemis.service.connectors.bitbucket.dto.*;
import de.tum.in.www1.artemis.service.user.PasswordService;

@Service
@Profile("bitbucket")
public class BitbucketService extends AbstractVersionControlService {

    private static final int MAX_GIVE_PERMISSIONS_RETRIES = 5;

    private final Logger log = LoggerFactory.getLogger(BitbucketService.class);

    @Value("${artemis.user-management.external.admin-group-name}")
    private String adminGroupName;

    @Value("${artemis.version-control.url}")
    private URL bitbucketServerUrl;

    @Value("${artemis.git.name}")
    private String artemisGitName;

    @Value("${artemis.version-control.default-branch:master}")
    private String defaultBranch;

    private final PasswordService passwordService;

    private final UserRepository userRepository;

    private final RestTemplate restTemplate;

    private final RestTemplate shortTimeoutRestTemplate;

    public BitbucketService(PasswordService passwordService, @Qualifier("bitbucketRestTemplate") RestTemplate restTemplate, UserRepository userRepository, UrlService urlService,
            @Qualifier("shortTimeoutBitbucketRestTemplate") RestTemplate shortTimeoutRestTemplate, GitService gitService, ApplicationContext applicationContext) {
        super(applicationContext, gitService, urlService);
        this.passwordService = passwordService;
        this.userRepository = userRepository;
        this.restTemplate = restTemplate;
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
    }

    @Override
    public void configureRepository(ProgrammingExercise exercise, VcsRepositoryUrl repositoryUrl, Set<User> users, boolean allowAccess) {
        for (User user : users) {
            String username = user.getLogin();

            // TODO: does it really make sense to potentially create a user here? Should we not rather create this user when the user is created in the internal Artemis database?

            if ((userPrefixEdx.isPresent() && username.startsWith(userPrefixEdx.get())) || (userPrefixU4I.isPresent() && username.startsWith((userPrefixU4I.get())))) {
                // It is an automatically created user

                if (!userExists(username)) {
                    log.debug("Bitbucket user {} does not exist yet", username);
                    String displayName = (user.getFirstName() + " " + user.getLastName()).trim();
                    createUser(username, passwordService.decryptPasswordByLogin(username).get(), user.getEmail(), displayName);

                    try {
                        // NOTE: we need to fetch the user here again to make sure that the groups are not lazy loaded.
                        user = userRepository.getUserWithGroupsAndAuthorities(user.getLogin());
                        addUserToGroups(username, user.getGroups());
                    }
                    catch (BitbucketException e) {
                        /*
                         * This might throw exceptions, for example if the group does not exist on Bitbucket. We can safely ignore them.
                         */
                    }
                }
                else {
                    log.debug("Bitbucket user {} already exists", username);
                }
            }

            if (allowAccess && !Boolean.FALSE.equals(exercise.isAllowOfflineIde())) {
                // only add access to the repository if the offline IDE usage is NOT disallowed
                // NOTE: null values are interpreted as offline IDE is allowed
                addMemberToRepository(repositoryUrl, user);
            }
        }

        protectBranches(urlService.getProjectKeyFromRepositoryUrl(repositoryUrl), urlService.getRepositorySlugFromRepositoryUrl(repositoryUrl));
    }

    @Override
    public void addMemberToRepository(VcsRepositoryUrl repositoryUrl, User user) {
        giveWritePermission(urlService.getProjectKeyFromRepositoryUrl(repositoryUrl), urlService.getRepositorySlugFromRepositoryUrl(repositoryUrl), user.getLogin());
    }

    @Override
    public void removeMemberFromRepository(VcsRepositoryUrl repositoryUrl, User user) {
        removeStudentRepositoryAccess(repositoryUrl, urlService.getProjectKeyFromRepositoryUrl(repositoryUrl), user.getLogin());
    }

    /**
     * This methods protects the repository on the Bitbucket server by using a REST-call to setup branch protection.
     * The branch protection is applied to all branches and prevents rewriting the history (force-pushes) and deletion of branches.
     * @param projectKey The project key of the repository that should be protected
     * @param repositorySlug The slug of the repository that should be protected
     */
    private void protectBranches(String projectKey, String repositorySlug) {
        String baseUrl = bitbucketServerUrl + "/rest/branch-permissions/2.0/projects/" + projectKey + "/repos/" + repositorySlug + "/restrictions";
        log.debug("Setting up branch protection for repository {}", repositorySlug);

        // Payload according to https://docs.atlassian.com/bitbucket-server/rest/4.2.0/bitbucket-ref-restriction-rest.html
        final var type = new BitbucketBranchProtectionDTO.TypeDTO("PATTERN", "Pattern");
        // A wildcard (*) ist used to protect all branches
        final var matcher = new BitbucketBranchProtectionDTO.MatcherDTO("*", "*", type, true);
        // Prevent force-pushes
        final var fastForwardOnlyProtection = new BitbucketBranchProtectionDTO("fast-forward-only", matcher);
        // Prevent deletion of branches
        final var noDeletesProtection = new BitbucketBranchProtectionDTO("no-deletes", matcher);
        final var body = List.of(fastForwardOnlyProtection, noDeletesProtection);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "vnd.atl.bitbucket.bulk+json")); // Set content-type manually as required by Bitbucket
        HttpEntity<?> entity = new HttpEntity<>(body, headers);
        try {
            restTemplate.exchange(baseUrl, HttpMethod.POST, entity, Object.class);
        }
        catch (Exception emAll) {
            log.error("Exception occurred while protecting repository " + repositorySlug, emAll);
        }

        log.debug("Branch protection for repository {} set up", repositorySlug);
    }

    @Override
    protected void addWebHook(VcsRepositoryUrl repositoryUrl, String notificationUrl, String webHookName) {
        if (!webHookExists(urlService.getProjectKeyFromRepositoryUrl(repositoryUrl), urlService.getRepositorySlugFromRepositoryUrl(repositoryUrl))) {
            createWebHook(urlService.getProjectKeyFromRepositoryUrl(repositoryUrl), urlService.getRepositorySlugFromRepositoryUrl(repositoryUrl), notificationUrl, webHookName);
        }
    }

    @Override
    protected void addAuthenticatedWebHook(VcsRepositoryUrl repositoryUrl, String notificationUrl, String webHookName, String secretToken) {
        // Not needed for Bitbucket
        throw new UnsupportedOperationException("Authenticated webhooks with Bitbucket are not supported!");
    }

    @Override
    public void deleteProject(String projectKey) {
        String baseUrl = bitbucketServerUrl + "/rest/api/latest/projects/" + projectKey;
        log.info("Try to delete bitbucket project {}", projectKey);
        try {
            restTemplate.exchange(baseUrl, HttpMethod.DELETE, null, Void.class);
            log.info("Delete bitbucket project {} was successful", projectKey);
        }
        catch (Exception e) {
            log.error("Could not delete project", e);
        }
    }

    @Override
    public void deleteRepository(VcsRepositoryUrl repositoryUrl) {
        final String projectKey = urlService.getProjectKeyFromRepositoryUrl(repositoryUrl);
        final String repositorySlug = urlService.getRepositorySlugFromRepositoryUrl(repositoryUrl);
        final String baseUrl = bitbucketServerUrl + "/rest/api/latest/projects/" + projectKey + "/repos/" + repositorySlug.toLowerCase();
        log.info("Delete repository {}", baseUrl);
        try {
            restTemplate.exchange(baseUrl, HttpMethod.DELETE, null, Void.class);
        }
        catch (Exception e) {
            log.error("Could not delete repository", e);
        }
    }

    @Override
    public VcsRepositoryUrl getCloneRepositoryUrl(String projectKey, String repositorySlug) {
        final var cloneUrl = new BitbucketRepositoryUrl(projectKey, repositorySlug);
        log.debug("getCloneRepositoryUrl: {}", cloneUrl.toString());
        return cloneUrl;
    }

    private BitbucketProjectDTO getBitbucketProject(String projectKey) {
        return restTemplate.exchange(bitbucketServerUrl + "/rest/api/latest/projects/" + projectKey, HttpMethod.GET, null, BitbucketProjectDTO.class).getBody();
    }

    /**
     * Checks if an username exists on Bitbucket
     *
     * @param username the Bitbucket username to check
     * @return true if it exists
     * @throws BitbucketException any exception occurred on the Bitbucket server
     */
    private Boolean userExists(String username) throws BitbucketException {
        try {
            restTemplate.exchange(bitbucketServerUrl + "/rest/api/latest/users/" + username, HttpMethod.GET, null, Void.class);
        }
        catch (HttpClientErrorException e) {
            if (e.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                return false;
            }
            log.error("Could not check if user  " + username + " exists.", e);
            throw new BitbucketException("Could not check if user exists");
        }
        return true;
    }

    /**
     * Creates an user on Bitbucket
     *
     * @param username     The wanted Bitbucket username
     * @param password     The wanted password in clear text
     * @param emailAddress The eMail address for the user
     * @param displayName  The display name (full name)
     * @throws BitbucketException if the rest request to Bitbucket for creating the user failed.
     */
    public void createUser(String username, String password, String emailAddress, String displayName) throws BitbucketException {

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(bitbucketServerUrl + "/rest/api/latest/admin/users").queryParam("name", username)
                .queryParam("email", emailAddress).queryParam("emailAddress", emailAddress).queryParam("password", password).queryParam("displayName", displayName)
                .queryParam("addToDefaultGroup", "true").queryParam("notify", "false");

        log.debug("Creating Bitbucket user {} ({})", username, emailAddress);

        try {
            restTemplate.exchange(builder.build().encode().toUri(), HttpMethod.POST, null, Void.class);
        }
        catch (HttpClientErrorException e) {
            log.error("Could not create Bitbucket user " + username, e);
            throw new BitbucketException("Error while creating user", e);
        }
    }

    /**
     * Adds an Bitbucket user to (multiple) Bitbucket groups
     *
     * @param username The Bitbucket username
     * @param groups   Names of Bitbucket groups
     * @throws BitbucketException if the rest request to Bitbucket for adding the user to the specified groups failed.
     */
    public void addUserToGroups(String username, Set<String> groups) throws BitbucketException {
        final var body = new BitbucketUserDTO(username, groups);
        HttpEntity<?> entity = new HttpEntity<>(body, null);

        log.debug("Adding Bitbucket user {} to groups {}", username, groups);

        try {
            restTemplate.exchange(bitbucketServerUrl + "/rest/api/latest/admin/users/add-groups", HttpMethod.POST, entity, Void.class);
        }
        catch (HttpClientErrorException e) {
            log.error("Could not add Bitbucket user " + username + " to groups" + groups, e);
            throw new BitbucketException("Error while adding Bitbucket user to groups");
        }
    }

    /**
     * Gives user write permissions for a repository.
     *
     * @param projectKey     The project key of the repository's project.
     * @param repositorySlug The repository's slug.
     * @param username       The user whom to give write permissions.
     */
    // TODO: Refactor to also use setStudentRepositoryPermission.
    private void giveWritePermission(String projectKey, String repositorySlug, String username) throws BitbucketException {
        String url = bitbucketServerUrl + "/rest/api/latest/projects/" + projectKey + "/repos/" + repositorySlug + "/permissions/users?name=" + username + "&permission=REPO_WRITE";

        try {
            /*
             * This is an edge case. If a new users logs in and clicks on Start Exercise within 1 minute, the user does not yet exist in Bitbucket.
             */
            User user = null;
            for (int i = 0; i < MAX_GIVE_PERMISSIONS_RETRIES; i++) {
                try {
                    restTemplate.exchange(url, HttpMethod.PUT, null, Void.class);
                }
                catch (HttpClientErrorException e) {

                    if (e.getResponseBodyAsString().contains("No such user")) {
                        if (user == null) {
                            user = userRepository.getUser();
                        }
                        if (user.getCreatedDate().plusSeconds(90).isAfter(Instant.now())) {
                            log.warn("Could not give write permissions to user {} because the user does not yet exist in Bitbucket. Trying again in 5s", username);
                            Thread.sleep(5000);
                            // if the last attempt fails, we throw an exception to make sure to exit this method with an exception
                            if (i == MAX_GIVE_PERMISSIONS_RETRIES - 1) {
                                throw e;
                            }
                        }
                        else {
                            throw e;
                        }
                    }
                    else {
                        throw e;
                    }

                    // Try again if there was an exception
                    continue;
                }
                // Don't try again if everything went fine
                break;
            }
        }
        catch (HttpClientErrorException e) {
            log.error("Server Error on Bitbucket with message: '{}', body: '{}', headers: '{}', status text: '{}'.", e.getMessage(), e.getResponseBodyAsString(),
                    e.getResponseHeaders(), e.getStatusText());
            log.error("Could not give write permission using " + url, e);
            throw new BitbucketException("Error while giving repository permissions", e);
        }
        catch (Exception emAll) {
            log.error("Could not give write permission using " + url, emAll);
            throw new BitbucketException("Error while giving repository permissions", emAll);
        }

    }

    @Override
    public void setRepositoryPermissionsToReadOnly(VcsRepositoryUrl repositoryUrl, String projectKey, Set<User> users) throws BitbucketException {
        users.forEach(user -> setStudentRepositoryPermission(repositoryUrl, projectKey, user.getLogin(), VersionControlRepositoryPermission.READ_ONLY));
    }

    /**
     * Get the default branch of the repository
     *
     * @param repositoryUrl The repository url to get the default branch for.
     * @return the name of the default branch, e.g. 'main'
     */
    @Override
    public String getDefaultBranchOfRepository(VcsRepositoryUrl repositoryUrl) throws BitbucketException {
        String projectKey = urlService.getProjectKeyFromRepositoryUrl(repositoryUrl);
        String repositorySlug = urlService.getRepositorySlugFromRepositoryUrl(repositoryUrl);
        var getDefaultBranchUrl = bitbucketServerUrl + "/rest/api/latest/projects/" + projectKey + "/repos/" + repositorySlug.toLowerCase() + "/default-branch";

        try {
            var response = restTemplate.exchange(getDefaultBranchUrl, HttpMethod.GET, null, BitbucketDefaultBranchDTO.class);
            var body = response.getBody();

            if (body == null) {
                log.error("Unable to get default branch for repository " + repositorySlug);
                throw new BitbucketException("Unable to get default branch for repository " + repositorySlug);
            }

            return body.getDisplayId();
        }
        catch (HttpClientErrorException e) {
            log.error("Unable to get default branch for repository " + repositorySlug, e);
            throw new BitbucketException("Unable to get default branch for repository " + repositorySlug, e);
        }
    }

    /**
     * Set the default branch of the repository
     *
     * @param repositoryUrl The repository url to set the default branch for.
     * @param branchName The name of the branch to set as default, e.g. 'main'
     */
    public void setDefaultBranchOfRepository(VcsRepositoryUrl repositoryUrl, String branchName) throws BitbucketException {
        var projectKey = urlService.getProjectKeyFromRepositoryUrl(repositoryUrl);
        var repositorySlug = urlService.getRepositorySlugFromRepositoryUrl(repositoryUrl);
        var getDefaultBranchUrl = bitbucketServerUrl + "/rest/api/latest/projects/" + projectKey + "/repos/" + repositorySlug.toLowerCase() + "/branches/default";

        try {
            var payload = new BitbucketDefaultBranchDTO("refs/heads/" + branchName);
            restTemplate.exchange(getDefaultBranchUrl, HttpMethod.PUT, new HttpEntity<>(payload), Void.class);
        }
        catch (HttpClientErrorException e) {
            log.error("Unable to set default branch for repository " + repositorySlug, e);
            throw new BitbucketException("Unable to set default branch for repository " + repositorySlug, e);
        }
    }

    @Override
    public void unprotectBranch(VcsRepositoryUrl repositoryUrl, String branch) throws VersionControlException {
        // Not implemented because it's not needed in Bitbucket for the current use case, because the master branch is not protected by default
    }

    /**
     * Set the permission of a student for a repository
     *
     * @param repositoryUrl         The complete repository-url (including protocol, host and the complete path)
     * @param projectKey            The project key of the repository's project.
     * @param username              The username of the user whom to assign a permission level
     * @param repositoryPermission  Repository permission to set for the user (e.g. READ_ONLY, WRITE)
     */
    private void setStudentRepositoryPermission(VcsRepositoryUrl repositoryUrl, String projectKey, String username, VersionControlRepositoryPermission repositoryPermission)
            throws BitbucketException {
        String permissionString = repositoryPermission == VersionControlRepositoryPermission.READ_ONLY ? "READ" : "WRITE";
        String repositorySlug = getRepositoryName(repositoryUrl);
        String baseUrl = bitbucketServerUrl + "/rest/api/latest/projects/" + projectKey + "/repos/" + repositorySlug + "/permissions/users?name="; // NAME&PERMISSION
        String url = baseUrl + username + "&permission=REPO_" + permissionString;
        try {
            restTemplate.exchange(url, HttpMethod.PUT, null, Void.class);
        }
        catch (Exception e) {
            log.error("Could not give " + repositoryPermission + " permissions using " + url, e);
            throw new BitbucketException("Error while giving repository permissions", e);
        }
    }

    /**
     * Remove all permissions of a student for a repository
     *
     * @param repositoryUrl  The complete repository-url (including protocol, host and the complete path)
     * @param projectKey     The project key of the repository's project.
     * @param username       The username of the user whom to remove access
     */
    private void removeStudentRepositoryAccess(VcsRepositoryUrl repositoryUrl, String projectKey, String username) throws BitbucketException {
        String repositorySlug = getRepositoryName(repositoryUrl);
        String baseUrl = bitbucketServerUrl + "/rest/api/latest/projects/" + projectKey + "/repos/" + repositorySlug + "/permissions/users?name="; // NAME
        String url = baseUrl + username;
        try {
            restTemplate.exchange(url, HttpMethod.DELETE, null, Void.class);
        }
        catch (Exception e) {
            log.error("Could not remove repository access using " + url, e);
            throw new BitbucketException("Error while removing repository access", e);
        }
    }

    @Override
    public boolean checkIfProjectExists(String projectKey, String projectName) {
        try {
            // first check that the project key is unique, if the project does not exist, we expect a 404 Not Found status
            var project = getBitbucketProject(projectKey);
            log.warn("Bitbucket project with key {} already exists: {}", projectKey, project.getName());
            return true;
        }
        catch (HttpClientErrorException e) {
            log.debug("Bitbucket project {} does not exist", projectKey);
            if (e.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                // only if this is the case, we additionally check that the project name is unique

                final var response = restTemplate.exchange(bitbucketServerUrl + "/rest/api/latest/projects?name=" + projectName, HttpMethod.GET, null,
                        new ParameterizedTypeReference<BitbucketSearchDTO<BitbucketProjectDTO>>() {
                        });

                if (response.getBody() != null && response.getBody().getSize() > 0) {
                    final var exists = response.getBody().getSearchResults().stream().anyMatch(project -> project.getName().equalsIgnoreCase(projectName));
                    if (exists) {
                        log.warn("Bitbucket project with name {} already exists", projectName);
                        return true;
                    }
                }

                return false;
            }
            else {
                // rethrow so that other errors are not hidden
                log.error(e.getMessage(), e);
                throw e;
            }
        }
    }

    /**
     * Create a new project
     *
     * @param programmingExercise the programming exercise for which the Bitbucket Project should be created
     * @throws BitbucketException if the project could not be created
     */
    @Override
    public void createProjectForExercise(ProgrammingExercise programmingExercise) throws BitbucketException {
        String projectKey = programmingExercise.getProjectKey();
        String projectName = programmingExercise.getProjectName();
        final var body = new BitbucketProjectDTO(projectKey, projectName);
        HttpEntity<?> entity = new HttpEntity<>(body, null);

        log.debug("Creating Bitbucket project {} with key {}", projectName, projectKey);

        try {
            // Get course over exerciseGroup in exam mode
            Course course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();

            restTemplate.exchange(bitbucketServerUrl + "/rest/api/latest/projects", HttpMethod.POST, entity, Void.class);
            grantGroupPermissionToProject(projectKey, adminGroupName, BitbucketPermission.PROJECT_ADMIN); // admins get administrative permissions

            if (StringUtils.hasText(course.getInstructorGroupName())) {
                grantGroupPermissionToProject(projectKey, course.getInstructorGroupName(), BitbucketPermission.PROJECT_ADMIN); // instructors get administrative permissions
            }

            // editors get write permissions
            if (StringUtils.hasText(course.getEditorGroupName())) {
                grantGroupPermissionToProject(projectKey, course.getEditorGroupName(), BitbucketPermission.PROJECT_WRITE);
            }
            // tutors get read permissions
            if (StringUtils.hasText(course.getTeachingAssistantGroupName())) {
                grantGroupPermissionToProject(projectKey, course.getTeachingAssistantGroupName(), BitbucketPermission.PROJECT_READ);
            }
        }
        catch (HttpClientErrorException e) {
            log.error("Could not create Bitbucket project {} with key {}", projectName, projectKey, e);
            throw new BitbucketException("Error while creating Bitbucket project. Try a different name!");
        }
    }

    @Override
    public ConnectorHealth health() {
        ConnectorHealth health;
        try {
            final var status = shortTimeoutRestTemplate.getForObject(bitbucketServerUrl + "/status", JsonNode.class);
            health = status.get("state").asText().equals("RUNNING") ? new ConnectorHealth(true) : new ConnectorHealth(false);
        }
        catch (Exception emAll) {
            health = new ConnectorHealth(emAll);
        }

        health.setAdditionalInfo(Map.of("url", bitbucketServerUrl));
        return health;
    }

    /**
     * Create a new repo
     *
     * @param repoName   The project name
     * @param projectKey The project key of the parent project
     * @throws BitbucketException if the repo could not be created
     */
    private void createRepository(String projectKey, String repoName) throws BitbucketException {
        final var body = new BitbucketRepositoryDTO(repoName.toLowerCase());
        body.setDefaultBranch(defaultBranch);
        HttpEntity<?> entity = new HttpEntity<>(body, null);

        log.debug("Creating Bitbucket repo {} with parent key {}", repoName, projectKey);

        try {
            restTemplate.exchange(bitbucketServerUrl + "/rest/api/latest/projects/" + projectKey + "/repos", HttpMethod.POST, entity, Void.class);
        }
        catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                log.info("Repository {} (parent {}) already exists, reusing it...", repoName, projectKey);
                return;
            }
            log.error("Could not create Bitbucket repo {} with projectKey key {}", repoName, projectKey, e);
            throw new BitbucketException("Error while creating Bitbucket repo");
        }
    }

    private void grantGroupPermissionToProject(String projectKey, String groupName, BitbucketPermission permission) {
        String baseUrl = bitbucketServerUrl + "/rest/api/latest/projects/" + projectKey + "/permissions/groups/?name="; // GROUPNAME&PERMISSION
        try {
            restTemplate.exchange(baseUrl + groupName + "&permission=" + permission, HttpMethod.PUT, null, Void.class);
        }
        catch (Exception e) {
            log.error("Could not give project permission", e);
            throw new BitbucketException("Error while giving project permissions", e);
        }
    }

    /**
     * Get all existing WebHooks for a specific repository.
     *
     * @param projectKey     The project key of the repository's project.
     * @param repositorySlug The repository's slug.
     * @return A map of all ids of the WebHooks to the URL they notify.
     * @throws BitbucketException if the request to get the WebHooks failed
     */
    @Nullable
    private List<BitbucketWebHookDTO> getExistingWebHooks(String projectKey, String repositorySlug) throws BitbucketException {
        String baseUrl = bitbucketServerUrl + "/rest/api/latest/projects/" + projectKey + "/repos/" + repositorySlug + "/webhooks";
        ResponseEntity<BitbucketSearchDTO<BitbucketWebHookDTO>> response;
        try {
            response = restTemplate.exchange(baseUrl, HttpMethod.GET, null, new ParameterizedTypeReference<>() {
            });
        }
        catch (Exception e) {
            log.error("Error while getting existing WebHooks", e);
            throw new BitbucketException("Error while getting existing WebHooks", e);
        }

        if (response.getStatusCode().equals(HttpStatus.OK) && response.getBody() != null) {
            // TODO: BitBucket uses a pagination API to split up the responses, so we might have to check all pages
            return response.getBody().getSearchResults();
        }
        log.error("Error while getting existing WebHooks for {}-{}: Invalid response", projectKey, repositorySlug);
        throw new BitbucketException("Error while getting existing WebHooks: Invalid response");
    }

    private boolean webHookExists(String projectKey, String repositorySlug) {
        List<BitbucketWebHookDTO> webHooks = getExistingWebHooks(projectKey, repositorySlug);
        return webHooks != null && !webHooks.isEmpty();
    }

    private void createWebHook(String projectKey, String repositorySlug, String notificationUrl, String webHookName) {
        log.debug("Creating WebHook for Repository {}-{} ({})", projectKey, repositorySlug, notificationUrl);
        String baseUrl = bitbucketServerUrl + "/rest/api/latest/projects/" + projectKey + "/repos/" + repositorySlug + "/webhooks";
        final var body = new BitbucketWebHookDTO(webHookName, notificationUrl, List.of("repo:refs_changed"));
        // TODO: We might want to add a token to ensure the notification is valid

        HttpEntity<?> entity = new HttpEntity<>(body, null);

        try {
            restTemplate.exchange(baseUrl, HttpMethod.POST, entity, Void.class);
        }
        catch (HttpClientErrorException e) {
            log.error("Could not add create WebHook for {}-{} ({})", projectKey, repositorySlug, notificationUrl, e);
            throw new BitbucketException("Error while creating WebHook");
        }
    }

    @Override
    public Boolean repositoryUrlIsValid(@Nullable VcsRepositoryUrl repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.getURL() == null) {
            return false;
        }
        String projectKey;
        String repositorySlug;
        try {
            projectKey = urlService.getProjectKeyFromRepositoryUrl(repositoryUrl);
            repositorySlug = urlService.getRepositorySlugFromRepositoryUrl(repositoryUrl);
        }
        catch (BitbucketException e) {
            // Either the project Key or the repository slug could not be extracted, therefore this can't be a valid URL
            return false;
        }

        try {
            restTemplate.exchange(bitbucketServerUrl + "/rest/api/latest/projects/" + projectKey + "/repos/" + repositorySlug, HttpMethod.GET, null, Void.class);
        }
        catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    @NotNull
    public Commit getLastCommitDetails(Object requestBody) throws BitbucketException {
        // NOTE the requestBody should look like this:
        // {"eventKey":"...","date":"...","actor":{...},"repository":{...},"changes":[{"ref":{...},"refId":"refs/heads/master","fromHash":"5626436a443eb898a5c5f74b6352f26ea2b7c84e","toHash":"662868d5e16406d1dd4dcfa8ac6c46ee3d677924","type":"UPDATE"}]}
        // we are interested in the toHash
        Commit commit = new Commit();
        try {
            // TODO: use a DTO (e.g. something similar to CommitDTO)
            final var commitData = new ObjectMapper().convertValue(requestBody, JsonNode.class);
            var lastChange = commitData.get("changes").get(0);
            var ref = lastChange.get("ref");
            if (ref != null) {
                var branch = ref.get("displayId").asText();
                commit.setBranch(branch);
            }
            var hash = lastChange.get("toHash").asText();
            commit.setCommitHash(hash);
            var actor = commitData.get("actor");
            String name = actor.get("name").asText();
            String email = actor.get("emailAddress").asText();
            if (artemisGitName.equalsIgnoreCase(name)) {
                final var commitInfo = fetchCommitInfo(commitData, hash);
                if (commitInfo != null) {
                    commit.setMessage(commitInfo.get("message").asText());
                    name = commitInfo.get("author").get("name").asText();
                    email = commitInfo.get("author").get("emailAddress").asText();
                }
            }
            commit.setAuthorName(name);
            commit.setAuthorEmail(email);
        }
        catch (Exception e) {
            // silently fail because this step is not absolutely necessary
            log.error("Error when getting hash of last commit. Able to continue.", e);
        }
        return commit;
    }

    @Nullable
    private JsonNode fetchCommitInfo(JsonNode commitData, String hash) {
        try {
            var cloneLinks = commitData.get("repository").get("links").get("clone");
            VcsRepositoryUrl repositoryURL;
            // it might be the case that cloneLinks contains two URLs and the first one is 'ssh'. Then we are interested in http
            // we use contains here, because it could be the case that https is used here as well in the future.
            // It should not be possible that the cloneLinks array is empty.
            if (cloneLinks.size() > 1 && !cloneLinks.get(0).get("name").asText().contains("http")) {
                repositoryURL = new VcsRepositoryUrl(cloneLinks.get(1).get("href").asText());
            }
            else {
                repositoryURL = new VcsRepositoryUrl(cloneLinks.get(0).get("href").asText());
            }
            final var projectKey = urlService.getProjectKeyFromRepositoryUrl(repositoryURL);
            final var slug = urlService.getRepositorySlugFromRepositoryUrl(repositoryURL);
            final var uriBuilder = UriComponentsBuilder.fromUri(bitbucketServerUrl.toURI())
                    .pathSegment("rest", "api", "1.0", "projects", projectKey, "repos", slug, "commits", hash).build();
            final var commitInfo = restTemplate.exchange(uriBuilder.toUri(), HttpMethod.GET, null, JsonNode.class).getBody();
            if (commitInfo == null) {
                throw new BitbucketException("Unable to fetch commit info from Bitbucket for hash " + hash);
            }

            return commitInfo;
        }
        catch (Exception e) {
            log.warn("Cannot fetch commit info for hash {} due to error: {}", hash, e.getMessage());
        }
        return null;
    }

    @Override
    public void createRepository(String entityName, String topLevelEntity, String parentEntity) {
        createRepository(entityName, topLevelEntity);
    }

    public final class BitbucketRepositoryUrl extends VcsRepositoryUrl {

        public BitbucketRepositoryUrl(String projectKey, String repositorySlug) {
            final var urlString = bitbucketServerUrl.getProtocol() + "://" + bitbucketServerUrl.getAuthority() + buildRepositoryPath(projectKey, repositorySlug);
            try {
                this.url = new URL(urlString);
            }
            catch (MalformedURLException e) {
                throw new BitbucketException("Could not Bitbucket Repository URL", e);
            }
        }

        private BitbucketRepositoryUrl(String urlString) {
            try {
                this.url = new URL(urlString);
            }
            catch (MalformedURLException e) {
                throw new BitbucketException("Could not Bitbucket Repository URL", e);
            }
        }

        @Override
        public VcsRepositoryUrl withUser(String username) {
            this.username = username;
            return new BitbucketRepositoryUrl(url.toString().replaceAll("(https?://)(.*)", "$1" + username + "@$2"));
        }

        private String buildRepositoryPath(String projectKey, String repositorySlug) {
            return bitbucketServerUrl.getPath() + "/scm/" + projectKey + "/" + repositorySlug + ".git";
        }
    }
}
