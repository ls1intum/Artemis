package de.tum.in.www1.artemis.service.connectors;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.util.*;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;

import de.tum.in.www1.artemis.domain.Commit;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.exception.BitbucketException;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.connectors.bitbucket.dto.BitbucketBranchProtectionDTO;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

@Service
@Profile("bitbucket")
public class BitbucketService extends AbstractVersionControlService {

    private static final int MAX_FORK_RETRIES = 5;

    private static final int MAX_GIVE_PERMISSIONS_RETRIES = 5;

    private final Logger log = LoggerFactory.getLogger(BitbucketService.class);

    @Value("${artemis.jira.admin-group-name}")
    private String ADMIN_GROUP_NAME;

    @Value("${artemis.version-control.url}")
    private URL BITBUCKET_SERVER_URL;

    @Value("${artemis.version-control.user}")
    private String BITBUCKET_USER;

    @Value("${artemis.version-control.password}")
    private String BITBUCKET_PASSWORD;

    @Value("${artemis.lti.user-prefix-edx}")
    private String USER_PREFIX_EDX = "";

    @Value("${artemis.lti.user-prefix-u4i}")
    private String USER_PREFIX_U4I = "";

    @Value("${artemis.git.name}")
    private String ARTEMIS_GIT_NAME;

    private final UserService userService;

    private final RestTemplate restTemplate;

    public BitbucketService(UserService userService, @Qualifier("bitbucketRestTemplate") RestTemplate restTemplate) {
        this.userService = userService;
        this.restTemplate = restTemplate;
    }

    @Override
    public void configureRepository(URL repositoryUrl, String username) {
        if (username.startsWith(USER_PREFIX_EDX) || username.startsWith((USER_PREFIX_U4I))) {
            // It is an automatically created user

            User user = userService.getUserWithGroupsByLogin(username).get();

            if (!userExists(username)) {
                log.debug("Bitbucket user {} does not exist yet", username);
                String displayName = (user.getFirstName() + " " + user.getLastName()).trim();
                createUser(username, userService.decryptPasswordByLogin(username).get(), user.getEmail(), displayName);

                try {
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

        giveWritePermission(getProjectKeyFromUrl(repositoryUrl), getRepositorySlugFromUrl(repositoryUrl), username);
        protectBranches(getProjectKeyFromUrl(repositoryUrl), getRepositorySlugFromUrl(repositoryUrl));
    }

    /**
     * This methods protects the repository on the Bitbucket server by using a REST-call to setup branch protection.
     * The branch protection is applied to all branches and prevents rewriting the history (force-pushes) and deletion of branches.
     * @param projectKey The project key of the repository that should be protected
     * @param repositorySlug The slug of the repository that should be protected
     */
    private void protectBranches(String projectKey, String repositorySlug) {
        String baseUrl = BITBUCKET_SERVER_URL + "/rest/branch-permissions/2.0/projects/" + projectKey + "/repos/" + repositorySlug + "/restrictions";
        log.debug("Setting up branch protection for repository " + repositorySlug);

        // Payload according to https://docs.atlassian.com/bitbucket-server/rest/4.2.0/bitbucket-ref-restriction-rest.html
        final var type = new BitbucketBranchProtectionDTO.TypeDTO("PATTERN", "Pattern");
        // A wildcard (*) ist used to protect all branches
        final var matcher = new BitbucketBranchProtectionDTO.MatcherDTO("*", "*", type, true);
        // Prevent force-pushes
        final var fastForwardOnlyProtection = new BitbucketBranchProtectionDTO("fast-forward-only", matcher);
        // Prevent deletion of branches
        final var noDeletesProtection = new BitbucketBranchProtectionDTO("no-deletes", matcher);
        final var body = List.of(fastForwardOnlyProtection, noDeletesProtection);

        HttpHeaders headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);
        headers.setContentType(new MediaType("application", "vnd.atl.bitbucket.bulk+json")); // Set content-type manually as required by Bitbucket
        HttpEntity<?> entity = new HttpEntity<>(body, headers);
        try {
            restTemplate.exchange(baseUrl, HttpMethod.POST, entity, Object.class);
        }
        catch (Exception emAll) {
            log.error("Exception occurred while protecting repository " + repositorySlug, emAll);
        }

        log.debug("Branch protection for repository " + repositorySlug + " set up");
    }

    @Override
    protected void addWebHook(URL repositoryUrl, String notificationUrl, String webHookName) {
        if (!webHookExists(getProjectKeyFromUrl(repositoryUrl), getRepositorySlugFromUrl(repositoryUrl))) {
            createWebHook(getProjectKeyFromUrl(repositoryUrl), getRepositorySlugFromUrl(repositoryUrl), notificationUrl, webHookName);
        }
    }

    @Override
    protected void addAuthenticatedWebHook(URL repositoryUrl, String notificationUrl, String webHookName, String secretToken) {
        // Not needed for Bitbucket
        throw new UnsupportedOperationException("Authenticated webhooks with Bitbucket are not supported!");
    }

    @Override
    public void deleteProject(String projectKey) {
        String baseUrl = BITBUCKET_SERVER_URL + "/rest/api/1.0/projects/" + projectKey;
        log.info("Delete bitbucket project " + projectKey);
        HttpHeaders headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        try {
            restTemplate.exchange(baseUrl, HttpMethod.DELETE, entity, Map.class);
        }
        catch (Exception e) {
            log.error("Could not delete project", e);
        }
    }

    @Override
    public void deleteRepository(URL repositoryUrl) {
        deleteRepositoryImpl(getProjectKeyFromUrl(repositoryUrl), getRepositorySlugFromUrl(repositoryUrl));
    }

    @Override
    public VcsRepositoryUrl getCloneRepositoryUrl(String projectKey, String repositorySlug) {
        final var cloneUrl = new BitbucketRepositoryUrl(projectKey, repositorySlug);
        log.debug("getCloneURL: " + cloneUrl.toString());
        return cloneUrl;
    }

    @Override
    public VcsRepositoryUrl copyRepository(String sourceProjectKey, String sourceRepositoryName, String targetProjectKey, String targetRepositoryName) {
        sourceRepositoryName = sourceRepositoryName.toLowerCase();
        targetRepositoryName = targetRepositoryName.toLowerCase();
        final var targetRepoSlug = targetProjectKey.toLowerCase() + "-" + targetRepositoryName;
        final var body = new HashMap<String, Object>();
        body.put("name", targetRepoSlug);
        final var projectMap = new HashMap<>();
        projectMap.put("key", targetProjectKey);
        body.put("project", projectMap);
        final var headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(body, headers);

        log.info("Try to copy repository " + sourceProjectKey + "/repos/" + sourceRepositoryName + " into " + targetRepoSlug);
        final String repoUrl = BITBUCKET_SERVER_URL + "/rest/api/1.0/projects/" + sourceProjectKey + "/repos/" + sourceRepositoryName;

        try {
            /*
             * There is an edge case occurring when multiple students fork a repository leading to a race condition on the filelock for the gitconfig of the base repository since
             * Bitbucket always tries to set pruneexpire to never as soon as one forks a repository. We only catch this case and loop over the request until we managed to fork the
             * repository, or the maximum amount of retries has been exceeded. There is no direct other solution as of now since this is a default Bitbucket behavior we cannot
             * control
             */
            for (int i = 0; i < MAX_FORK_RETRIES; i++) {
                try {
                    final var response = restTemplate.postForEntity(new URI(repoUrl), entity, Map.class);
                    if (response.getStatusCode().equals(HttpStatus.CREATED)) {
                        return new BitbucketRepositoryUrl(targetProjectKey, targetRepoSlug);
                    }
                    else {
                        log.warn("Invalid response code from Bitbucket while trying to fork repository {}: {}. Body from Bitbucket: {}", sourceRepositoryName,
                                response.getStatusCode(), new ObjectMapper().writeValueAsString(response.getBody()));
                    }
                }
                catch (HttpServerErrorException.InternalServerError e) {

                    if (e.getResponseBodyAsString().contains("code 255 saying: error: could not lock config file config: File exists")) {
                        log.warn("Could not acquire lock for gitconfig in Bitbucket while forking the repository. Trying again");
                        if (i == MAX_FORK_RETRIES - 1) {
                            // if the last attempt fails, we throw an exception to make sure to exit this method with an exception
                            throw e;
                        }
                    }
                    else {
                        throw e;
                    }
                }
            }
        }
        catch (URISyntaxException e) {
            throw new BitbucketException("Invalid repository URL built while trying to fork: " + repoUrl);
        }
        catch (HttpClientErrorException e) {
            if (e.getStatusCode().equals(HttpStatus.CONFLICT)) {
                log.info("Repository already exists. Going to recover repository information...");
                return new BitbucketRepositoryUrl(targetProjectKey, targetRepoSlug);
            }
            else {
                var bodyString = Joiner.on(",").withKeyValueSeparator("=").join(body);
                log.error("Could not fork base repository on " + repoUrl + " using " + bodyString, e);
                throw new BitbucketException("Error while forking repository", e);
            }
        }
        catch (HttpServerErrorException e) {
            if (e instanceof HttpServerErrorException.InternalServerError) {
                var internalServerError = (HttpServerErrorException.InternalServerError) e;
                log.error("Internal Server Error on Bitbucket with message: '" + internalServerError.getMessage() + "', body: '" + internalServerError.getResponseBodyAsString()
                        + "', headers: '" + internalServerError.getResponseHeaders() + "', status text: '" + internalServerError.getStatusText() + "'.");
            }
            var bodyString = Joiner.on(",").withKeyValueSeparator("=").join(body);
            log.error("Could not fork base repository on " + repoUrl + " using " + bodyString, e);
            throw new BitbucketException("Error while forking repository", e);
        }
        catch (Exception emAll) {
            var bodyString = Joiner.on(",").withKeyValueSeparator("=").join(body);
            log.error("Could not fork base repository on " + repoUrl + " using " + bodyString, emAll);
            throw new BitbucketException("Error while forking repository", emAll);
        }

        throw new BitbucketException("Max retries for forking reached. Could not fork repository " + sourceRepositoryName + " to " + targetRepositoryName);
    }

    /**
     * Gets the project key from the given URL
     *
     * @param repositoryUrl The complete repository-url (including protocol, host and the complete path)
     * @return The project key
     * @throws BitbucketException if the URL is invalid and no project key could be extracted
     */
    private String getProjectKeyFromUrl(URL repositoryUrl) throws BitbucketException {
        // https://ga42xab@repobruegge.in.tum.de/scm/EIST2016RME/RMEXERCISE-ga42xab.git
        String[] urlParts = repositoryUrl.getFile().split("/");
        if (urlParts.length > 2) {
            return urlParts[2];
        }

        log.error("No project key could be found for repository {}", repositoryUrl);
        throw new BitbucketException("No project key could be found");
    }

    /**
     * Gets the repository slug from the given URL
     *
     * @param repositoryUrl The complete repository-url (including protocol, host and the complete path)
     * @return The repository slug
     * @throws BitbucketException if the URL is invalid and no repository slug could be extracted
     */
    public String getRepositorySlugFromUrl(URL repositoryUrl) throws BitbucketException {
        // https://ga42xab@repobruegge.in.tum.de/scm/EIST2016RME/RMEXERCISE-ga42xab.git
        String[] urlParts = repositoryUrl.getFile().split("/");
        if (urlParts[urlParts.length - 1].endsWith(".git")) {
            String repositorySlug = urlParts[urlParts.length - 1];
            repositorySlug = repositorySlug.substring(0, repositorySlug.length() - 4);
            return repositorySlug;
        }

        log.error("No repository slug could be found for repository {}", repositoryUrl);
        throw new BitbucketException("No repository slug could be found");
    }

    /**
     * Checks if an username exists on Bitbucket
     *
     * @param username the Bitbucket username to check
     * @return true if it exists
     * @throws BitbucketException any exception occurred on the Bitbucket server
     */
    private Boolean userExists(String username) throws BitbucketException {
        HttpHeaders headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        try {
            restTemplate.exchange(BITBUCKET_SERVER_URL + "/rest/api/1.0/users/" + username, HttpMethod.GET, entity, Map.class);
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
     * @param password     The wanted passowrd in clear text
     * @param emailAddress The eMail address for the user
     * @param displayName  The display name (full name)
     * @throws BitbucketException if the rest request to Bitbucket for creating the user failed.
     */
    public void createUser(String username, String password, String emailAddress, String displayName) throws BitbucketException {
        HttpHeaders headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(BITBUCKET_SERVER_URL + "/rest/api/1.0/admin/users").queryParam("name", username)
                .queryParam("email", emailAddress).queryParam("emailAddress", emailAddress).queryParam("password", password).queryParam("displayName", displayName)
                .queryParam("addToDefaultGroup", "true").queryParam("notify", "false");

        HttpEntity<?> entity = new HttpEntity<>(headers);

        log.debug("Creating Bitbucket user {} ({})", username, emailAddress);

        try {
            restTemplate.exchange(builder.build().encode().toUri(), HttpMethod.POST, entity, Map.class);
        }
        catch (HttpClientErrorException e) {
            log.error("Could not create Bitbucket user " + username, e);
            throw new BitbucketException("Error while creating user");
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
        HttpHeaders headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);

        Map<String, Object> body = new HashMap<>();
        body.put("user", username);
        body.put("groups", groups);
        HttpEntity<?> entity = new HttpEntity<>(body, headers);

        log.debug("Adding Bitbucket user {} to groups {}", username, groups);

        try {
            restTemplate.exchange(BITBUCKET_SERVER_URL + "/rest/api/1.0/admin/users/add-groups", HttpMethod.POST, entity, Map.class);
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
        String baseUrl = BITBUCKET_SERVER_URL + "/rest/api/1.0/projects/" + projectKey + "/repos/" + repositorySlug + "/permissions/users?name=";// NAME&PERMISSION
        HttpHeaders headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        String url = baseUrl + username + "&permission=REPO_WRITE";

        try {
            /*
             * This is an edge case. If a new users logs in and clicks on Start Exercise within 1 minute, the user does not yet exist in Bitbucket.
             */
            User user = null;
            for (int i = 0; i < MAX_GIVE_PERMISSIONS_RETRIES; i++) {
                try {
                    restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);
                }
                catch (HttpClientErrorException e) {

                    if (e.getResponseBodyAsString().contains("No such user")) {
                        if (user == null) {
                            user = userService.getUser();
                        }
                        if (user.getCreatedDate().plusSeconds(90).isAfter(Instant.now())) {
                            log.warn("Could not give write permissions to user " + username + ", because the user does not yet exist in Bitbucket. Trying again in 5s");
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
            log.error("Server Error on Bitbucket with message: '" + e.getMessage() + "', body: '" + e.getResponseBodyAsString() + "', headers: '" + e.getResponseHeaders()
                    + "', status text: '" + e.getStatusText() + "'.");
            log.error("Could not give write permission using " + url, e);
            throw new BitbucketException("Error while giving repository permissions", e);
        }
        catch (Exception emAll) {
            log.error("Could not give write permission using " + url, emAll);
            throw new BitbucketException("Error while giving repository permissions", emAll);
        }

    }

    @Override
    public void setRepositoryPermissionsToReadOnly(URL repositoryUrl, String projectKey, String username) throws BitbucketException {
        setStudentRepositoryPermission(repositoryUrl, projectKey, username, VersionControlRepositoryPermission.READ_ONLY);
    }

    private void setStudentRepositoryPermission(URL repositoryUrl, String projectKey, String username, VersionControlRepositoryPermission repositoryPermission)
            throws BitbucketException {
        String permissionString = repositoryPermission == VersionControlRepositoryPermission.READ_ONLY ? "READ" : "WRITE";
        String repositorySlug = getRepositoryName(repositoryUrl);
        String baseUrl = BITBUCKET_SERVER_URL + "/rest/api/1.0/projects/" + projectKey + "/repos/" + repositorySlug + "/permissions/users?name=";// NAME&PERMISSION
        HttpHeaders headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        String url = baseUrl + username + "&permission=REPO_" + permissionString;
        try {
            restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);
        }
        catch (Exception e) {
            log.error("Could not give " + repositoryPermission + " permissions using " + url, e);
            throw new BitbucketException("Error while giving repository permissions", e);
        }
    }

    @Override
    public boolean checkIfProjectExists(String projectKey, String projectName) {
        HttpHeaders headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = null;
        try {
            // first check that the project key is unique
            response = restTemplate.exchange(BITBUCKET_SERVER_URL + "/rest/api/1.0/projects/" + projectKey, HttpMethod.GET, entity, Map.class);
            log.warn("Bitbucket project with key " + projectKey + " already exists");
            return true;
        }
        catch (HttpClientErrorException e) {
            log.debug("Bitbucket project " + projectKey + " does not exit");
            if (e.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                // only if this is the case, we additionally check that the project name is unique

                response = restTemplate.exchange(BITBUCKET_SERVER_URL + "/rest/api/1.0/projects?name=" + projectName, HttpMethod.GET, entity, Map.class);

                if ((Integer) response.getBody().get("size") != 0) {
                    List<Object> vcsProjects = (List<Object>) response.getBody().get("values");
                    for (Object vcsProject : vcsProjects) {
                        String vcsProjectName = (String) ((Map) vcsProject).get("name");
                        if (vcsProjectName.equalsIgnoreCase(projectName)) {
                            log.warn("Bitbucket project with name" + projectName + " already exists");
                            return true;
                        }
                    }
                }
                return false;
            }
        }
        return true;
    }

    /**
     * Create a new project
     *
     * @param programmingExercise
     * @throws BitbucketException if the project could not be created
     */
    @Override
    public void createProjectForExercise(ProgrammingExercise programmingExercise) throws BitbucketException {
        String projectKey = programmingExercise.getProjectKey();
        String projectName = programmingExercise.getProjectName();
        HttpHeaders headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);

        Map<String, Object> body = new HashMap<>();
        body.put("key", projectKey);
        body.put("name", projectName);
        // TODO: add a description
        HttpEntity<?> entity = new HttpEntity<>(body, headers);

        log.debug("Creating Bitbucket project {} with key {}", projectName, projectKey);

        try {
            restTemplate.exchange(BITBUCKET_SERVER_URL + "/rest/api/1.0/projects", HttpMethod.POST, entity, Map.class);
            grantGroupPermissionToProject(projectKey, ADMIN_GROUP_NAME, "PROJECT_ADMIN"); // admins get administrative permissions

            if (programmingExercise.getCourse().getInstructorGroupName() != null && !programmingExercise.getCourse().getInstructorGroupName().isEmpty()) {
                grantGroupPermissionToProject(projectKey, programmingExercise.getCourse().getInstructorGroupName(), "PROJECT_ADMIN"); // instructors get administrative permissions
            }

            if (programmingExercise.getCourse().getTeachingAssistantGroupName() != null && !programmingExercise.getCourse().getTeachingAssistantGroupName().isEmpty()) {
                grantGroupPermissionToProject(projectKey, programmingExercise.getCourse().getTeachingAssistantGroupName(), "PROJECT_WRITE"); // teachingAssistants get
                // write-permissions
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
            final var status = restTemplate.getForObject(BITBUCKET_SERVER_URL + "/status", JsonNode.class);
            health = status.get("state").asText().equals("RUNNING") ? new ConnectorHealth(true) : new ConnectorHealth(false);
        }
        catch (Exception emAll) {
            health = new ConnectorHealth(emAll);
        }

        health.setAdditionalInfo(Map.of("url", BITBUCKET_SERVER_URL));
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
        HttpHeaders headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);

        Map<String, Object> body = new HashMap<>();
        body.put("name", repoName.toLowerCase());
        HttpEntity<?> entity = new HttpEntity<>(body, headers);

        log.debug("Creating Bitbucket repo {} with parent key {}", repoName, projectKey);

        try {
            restTemplate.exchange(BITBUCKET_SERVER_URL + "/rest/api/1.0/projects/" + projectKey + "/repos", HttpMethod.POST, entity, Map.class);
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

    private void grantGroupPermissionToProject(String projectKey, String groupName, String permission) {
        String baseUrl = BITBUCKET_SERVER_URL + "/rest/api/1.0/projects/" + projectKey + "/permissions/groups/?name="; // GROUPNAME&PERMISSION
        HttpHeaders headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        try {
            restTemplate.exchange(baseUrl + groupName + "&permission=" + permission, HttpMethod.PUT, entity, Map.class);
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
    private Map<Integer, String> getExistingWebHooks(String projectKey, String repositorySlug) throws BitbucketException {
        HttpHeaders headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);
        String baseUrl = BITBUCKET_SERVER_URL + "/rest/api/1.0/projects/" + projectKey + "/repos/" + repositorySlug + "/webhooks";

        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(baseUrl, HttpMethod.GET, entity, Map.class);
        }
        catch (Exception e) {
            log.error("Error while getting existing WebHooks", e);
            throw new BitbucketException("Error while getting existing WebHooks", e);
        }

        Map<Integer, String> webHooks = new HashMap<>();

        if (response.getStatusCode().equals(HttpStatus.OK)) {
            // TODO: BitBucket uses a pagination API to split up the responses, so we might have to check all pages
            List<Map<String, Object>> rawWebHooks = (List<Map<String, Object>>) response.getBody().get("values");
            for (Map<String, Object> rawWebHook : rawWebHooks) {
                webHooks.put((Integer) rawWebHook.get("id"), (String) rawWebHook.get("url"));
            }
            return webHooks;
        }
        log.error("Error while getting existing WebHooks for {}-{}: Invalid response", projectKey, repositorySlug);
        throw new BitbucketException("Error while getting existing WebHooks: Invalid response");
    }

    private boolean webHookExists(String projectKey, String repositorySlug) {
        Map<Integer, String> webHooks = getExistingWebHooks(projectKey, repositorySlug);
        return !webHooks.isEmpty();
    }

    private void createWebHook(String projectKey, String repositorySlug, String notificationUrl, String webHookName) {
        log.debug("Creating WebHook for Repository {}-{} ({})", projectKey, repositorySlug, notificationUrl);
        HttpHeaders headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);
        String baseUrl = BITBUCKET_SERVER_URL + "/rest/api/1.0/projects/" + projectKey + "/repos/" + repositorySlug + "/webhooks";

        Map<String, Object> body = new HashMap<>();
        body.put("name", webHookName);
        body.put("url", notificationUrl);
        body.put("events", new ArrayList<>());
        ((List) body.get("events")).add("repo:refs_changed"); // Inform on push
        // TODO: We might want to add a token to ensure the notification is valid

        HttpEntity<?> entity = new HttpEntity<>(body, headers);

        try {
            restTemplate.exchange(baseUrl, HttpMethod.POST, entity, Map.class);
        }
        catch (HttpClientErrorException e) {
            log.error("Could not add create WebHook for {}-{} ({})", projectKey, repositorySlug, notificationUrl, e);
            throw new BitbucketException("Error while creating WebHook");
        }
    }

    private void deleteWebHook(String projectKey, String repositorySlug, Integer webHookId) {
        String baseUrl = BITBUCKET_SERVER_URL + "/rest/api/1.0/projects/" + projectKey + "/repos/" + repositorySlug + "/webhooks/" + webHookId;
        log.info("Delete WebHook {} on project {}-{}", webHookId, projectKey, repositorySlug);
        HttpHeaders headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        try {
            restTemplate.exchange(baseUrl, HttpMethod.DELETE, entity, Map.class);
        }
        catch (Exception e) {
            log.error("Could not delete WebHook", e);
        }
    }

    private void deleteExistingWebHooks(String projectKey, String repositorySlug) {
        Map<Integer, String> webHooks = getExistingWebHooks(projectKey, repositorySlug);
        for (Integer webHookId : webHooks.keySet()) {
            deleteWebHook(projectKey, repositorySlug, webHookId);
        }
    }

    /**
     * Deletes the given repository from Bitbucket.
     *
     * @param projectKey     The project key of the repository's project.
     * @param repositorySlug The repository's slug.
     */
    private void deleteRepositoryImpl(String projectKey, String repositorySlug) {
        String baseUrl = BITBUCKET_SERVER_URL + "/rest/api/1.0/projects/" + projectKey + "/repos/" + repositorySlug;
        log.info("Delete repository " + baseUrl);
        HttpHeaders headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        try {
            restTemplate.exchange(baseUrl, HttpMethod.DELETE, entity, Map.class);
        }
        catch (Exception e) {
            log.error("Could not delete repository", e);
        }
    }

    @Override
    public Boolean repositoryUrlIsValid(URL repositoryUrl) {
        String projectKey;
        String repositorySlug;
        try {
            projectKey = getProjectKeyFromUrl(repositoryUrl);
            repositorySlug = getRepositorySlugFromUrl(repositoryUrl);
        }
        catch (BitbucketException e) {
            // Either the project Key or the repository slug could not be extracted, therefor this can't be a valid URL
            return false;
        }

        HttpHeaders headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        try {
            restTemplate.exchange(BITBUCKET_SERVER_URL + "/rest/api/1.0/projects/" + projectKey + "/repos/" + repositorySlug, HttpMethod.GET, entity, Map.class);
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
            if (ARTEMIS_GIT_NAME.equalsIgnoreCase(name)) {
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
            log.error("Error when getting hash of last commit. Will continue, but the following error happened: " + e.getMessage(), e);
        }
        return commit;
    }

    @Nullable
    private JsonNode fetchCommitInfo(JsonNode commitData, String hash) {
        try {
            var cloneLinks = commitData.get("repository").get("links").get("clone");
            URL repositoryURL;
            // it might be the case that cloneLinks contains two URLs and the first one is 'ssh'. Then we are interested in http
            // we use contains here, because it could be the case that https is used here as well in the future.
            // It should not be possible that the cloneLinks array is empty.
            if (cloneLinks.size() > 1 && !cloneLinks.get(0).get("name").asText().contains("http")) {
                repositoryURL = new URL(cloneLinks.get(1).get("href").asText());
            }
            else {
                repositoryURL = new URL(cloneLinks.get(0).get("href").asText());
            }
            final var projectKey = getProjectKeyFromUrl(repositoryURL);
            final var slug = getRepositorySlugFromUrl(repositoryURL);
            final var uriBuilder = UriComponentsBuilder.fromUri(BITBUCKET_SERVER_URL.toURI())
                    .pathSegment("rest", "api", "1.0", "projects", projectKey, "repos", slug, "commits", hash).build();
            final var headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);
            final var entity = new HttpEntity<>(headers);

            final var commitInfo = restTemplate.exchange(uriBuilder.toUri(), HttpMethod.GET, entity, JsonNode.class).getBody();
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

    @Override
    public String getRepositoryName(URL repositoryUrl) {
        return getRepositorySlugFromUrl(repositoryUrl);
    }

    public final class BitbucketRepositoryUrl extends VcsRepositoryUrl {

        public BitbucketRepositoryUrl(String projectKey, String repositorySlug) {
            super();
            final var urlString = BITBUCKET_SERVER_URL.getProtocol() + "://" + BITBUCKET_SERVER_URL.getAuthority() + buildRepositoryPath(projectKey, repositorySlug);
            try {
                this.url = new URL(urlString);
            }
            catch (MalformedURLException e) {
                throw new BitbucketException("Could not build clone URL", e);
            }
        }

        private BitbucketRepositoryUrl(String urlString) {
            try {
                this.url = new URL(urlString);
            }
            catch (MalformedURLException e) {
                throw new BitbucketException("Could not build clone URL", e);
            }
        }

        @Override
        public VcsRepositoryUrl withUser(String username) {
            this.username = username;
            return new BitbucketRepositoryUrl(url.toString().replaceAll("(https?://)(.*)", "$1" + username + "@$2"));
        }

        private String buildRepositoryPath(String projectKey, String repositorySlug) {
            return BITBUCKET_SERVER_URL.getPath() + "/scm/" + projectKey + "/" + repositorySlug + ".git";
        }
    }
}
