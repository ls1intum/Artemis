package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exception.BitbucketException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Profile("bitbucket")
public class BitbucketService implements VersionControlService {

    private final Logger log = LoggerFactory.getLogger(BitbucketService.class);

    @Value("${artemis.jira.admin-group-name}")
    private String ADMIN_GROUP_NAME;

    @Value("${artemis.version-control.url}")
    private URL BITBUCKET_SERVER_URL;

    @Value("${artemis.version-control.user}")
    private String BITBUCKET_USER;

    @Value("${artemis.version-control.secret}")
    private String BITBUCKET_PASSWORD;

    @Value("${artemis.lti.user-prefix}")
    private String USER_PREFIX = "";

    private final UserService userService;

    public BitbucketService(UserService userService) {
        this.userService = userService;
    }


    @Override
    public URL copyRepository(URL baseRepositoryUrl, String username) {
        Map<String, String> result = this.forkRepository(getProjectKeyFromUrl(baseRepositoryUrl), getRepositorySlugFromUrl(baseRepositoryUrl), username);
        try {
            return new URL(result.get("cloneUrl"));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void configureRepository(URL repositoryUrl, String username) {


        if(username.startsWith(USER_PREFIX)) {
            // It is an automatically created user

            User user = userService.getUserByLogin(username).get();

            if (!userExists(username)) {
                log.debug("Bitbucket user {} does not exist yet", username);
                String displayName = (user.getFirstName() + " " + user.getLastName()).trim();
                createUser(username, userService.decryptPasswordByLogin(username).get(), user.getEmail(), displayName);

                try {
                    addUserToGroups(username, user.getGroups());
                } catch (BitbucketException e) {
            /*
                This might throw exceptions, for example if the group does not exist on Bitbucket.
                We can safely ignore them.
            */
                }

            } else {
                log.debug("Bitbucket user {} already exists", username);
            }

        }

        giveWritePermission(getProjectKeyFromUrl(repositoryUrl), getRepositorySlugFromUrl(repositoryUrl), username);
    }

    @Override
    public void addWebHook(URL repositoryUrl, String notificationUrl, String webHookName) {
        if (!webHookExists(getProjectKeyFromUrl(repositoryUrl), getRepositorySlugFromUrl(repositoryUrl), notificationUrl)) {
            createWebHook(getProjectKeyFromUrl(repositoryUrl), getRepositorySlugFromUrl(repositoryUrl), notificationUrl, webHookName);
        }
    }

    @Override
    public void addBambooService(String projectKey, String repositorySlug, String bambooUrl, String buildKey, String bambooUsername, String bambooPassword) {
        // NOT NEEDED
    }

    @Override
    public void deleteProject(String projectKey) {
        String baseUrl = BITBUCKET_SERVER_URL + "/rest/api/1.0/projects/" + projectKey;
        log.info("Delete bitbucket project " + projectKey);
        HttpHeaders headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(baseUrl, HttpMethod.DELETE, entity, Map.class);
        } catch (Exception e) {
            log.error("Could not delete project", e);
        }
    }

    @Override
    public void deleteRepository(URL repositoryUrl) {
        deleteRepositoryImpl(getProjectKeyFromUrl(repositoryUrl), getRepositorySlugFromUrl(repositoryUrl));
    }

    @Override
    public URL getRepositoryWebUrl(Participation participation) {
        try {
            return new URL(BITBUCKET_SERVER_URL +
                "/projects/" + getProjectKeyFromUrl(participation.getRepositoryUrlAsUrl()) +
                "/repos/" + getRepositorySlugFromUrl(participation.getRepositoryUrlAsUrl()) + "/browse");
        } catch (MalformedURLException e) {
            log.error("Couldn't construct repository web URL");
        }
        return BITBUCKET_SERVER_URL;
    }

    @Override
    public URL getCloneURL(String projectKey, String repositorySlug) {
        log.debug("getCloneURL: " + BITBUCKET_SERVER_URL.getProtocol() + "://" + BITBUCKET_SERVER_URL.getAuthority() + buildRepositoryPath(projectKey, repositorySlug));
        try {
            return new URL(BITBUCKET_SERVER_URL.getProtocol() + "://" + BITBUCKET_SERVER_URL.getAuthority() + buildRepositoryPath(projectKey, repositorySlug));
        } catch (MalformedURLException e) {
            log.error("Couldn't construct clone URL");
            throw new BitbucketException("Clone URL could not be constructed");
        }
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
    private String getRepositorySlugFromUrl(URL repositoryUrl) throws BitbucketException {
        // https://ga42xab@repobruegge.in.tum.de/scm/EIST2016RME/RMEXERCISE-ga42xab.git
        String[] urlParts = repositoryUrl.getFile().split("/");
        if (urlParts.length > 3) {
            String repositorySlug = urlParts[3];
            if (repositorySlug.endsWith(".git")) {
                repositorySlug = repositorySlug.substring(0, repositorySlug.length() - 4);
            }
            return repositorySlug;
        }

        log.error("No repository slug could be found for repository {}", repositoryUrl);
        throw new BitbucketException("No repository slug could be found");
    }

    /**
     * Uses the configured Bitbucket account to fork the given repository inside the project.
     *
     * @param baseProjectKey     The project key of the base project.
     * @param baseRepositorySlug The repository slug of the base repository.
     * @param username           The user for whom the repository is being forked.
     * @return The slug of the forked repository (i.e. its identifier).
     */
    private Map<String, String> forkRepository(String baseProjectKey, String baseRepositorySlug, String username) throws BitbucketException {
        String forkName = String.format("%s-%s", baseRepositorySlug, username);
        Map<String, Object> body = new HashMap<>();
        body.put("name", forkName);
        body.put("project", new HashMap<>());
        ((Map) body.get("project")).put("key", baseProjectKey);
        HttpHeaders headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(
                BITBUCKET_SERVER_URL + "/rest/api/1.0/projects/" + baseProjectKey + "/repos/" + baseRepositorySlug,
                HttpMethod.POST,
                entity,
                Map.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().equals(HttpStatus.CONFLICT)) {
                log.info("Repository already exists. Going to recover repository information...");
                Map<String, String> result = new HashMap<>();
                result.put("slug", forkName);
                result.put("cloneUrl", buildCloneUrl(baseProjectKey, forkName, username).toString());
                // Delete existing WebHooks (partipation ID might have changed)
                deleteExistingWebHooks(baseProjectKey, forkName);
                return result;
            } else {
                throw e;
            }
        } catch (Exception e) {
            log.error("Could not fork base repository for user " + username, e);
            throw new BitbucketException("Error while forking repository");
        }
        if (response != null && response.getStatusCode().equals(HttpStatus.CREATED)) {
            String slug = (String) response.getBody().get("slug");
            String cloneUrl = buildCloneUrl(baseProjectKey, forkName, username).toString();
            Map<String, String> result = new HashMap<>();
            result.put("slug", slug);
            result.put("cloneUrl", cloneUrl);
            return result;
        }
        return null;
    }


    /**
     * Checks if an username exists on Bitbucket
     *
     * @param username the Bitbucket username to check
     * @return true if it exists
     * @throws BitbucketException
     */
    private Boolean userExists(String username) throws BitbucketException {
        HttpHeaders headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(
                BITBUCKET_SERVER_URL + "/rest/api/1.0/users/" + username,
                HttpMethod.GET,
                entity,
                Map.class);
        } catch (HttpClientErrorException e) {
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
     * @throws BitbucketException
     */
    public void createUser(String username, String password, String emailAddress, String displayName) throws BitbucketException {
        HttpHeaders headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(BITBUCKET_SERVER_URL + "/rest/api/1.0/admin/users")
            .queryParam("name", username)
            .queryParam("email", emailAddress)
            .queryParam("emailAddress", emailAddress)
            .queryParam("password", password)
            .queryParam("displayName", displayName)
            .queryParam("addToDefaultGroup", "true")
            .queryParam("notify", "false");

        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();

        log.debug("Creating Bitbucket user {} ({})", username, emailAddress);

        try {
            restTemplate.exchange(
                builder.build().encode().toUri(),
                HttpMethod.POST,
                entity,
                Map.class);
        } catch (HttpClientErrorException e) {
            log.error("Could not create Bitbucket user " + username, e);
            throw new BitbucketException("Error while creating user");
        }
    }

    /**
     * Adds an Bitbucket user to (multiple) Bitbucket groups
     *
     * @param username The Bitbucket username
     * @param groups   Names of Bitbucket groups
     * @throws BitbucketException
     */
    public void addUserToGroups(String username, List<String> groups) throws BitbucketException {
        HttpHeaders headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);

        Map<String, Object> body = new HashMap<>();
        body.put("user", username);
        body.put("groups", groups);
        HttpEntity<?> entity = new HttpEntity<>(body, headers);

        RestTemplate restTemplate = new RestTemplate();

        log.debug("Adding Bitbucket user {} to groups {}", username, groups);

        try {
            restTemplate.exchange(
                BITBUCKET_SERVER_URL + "/rest/api/1.0/admin/users/add-groups",
                HttpMethod.POST,
                entity,
                Map.class);
        } catch (HttpClientErrorException e) {
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
    private void giveWritePermission(String projectKey, String repositorySlug, String username) throws BitbucketException {
        String baseUrl = BITBUCKET_SERVER_URL + "/rest/api/1.0/projects/" + projectKey + "/repos/" + repositorySlug + "/permissions/users?name=";//NAME&PERMISSION
        HttpHeaders headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(
                baseUrl + username + "&permission=REPO_WRITE",
                HttpMethod.PUT,
                entity, Map.class);
        } catch (Exception e) {
            log.error("Could not give write permission", e);
            throw new BitbucketException("Error while giving repository permissions");
        }
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
        HttpEntity<?> entity = new HttpEntity<>(body, headers);

        RestTemplate restTemplate = new RestTemplate();

        log.debug("Creating Bitbucket project {} with key {}", projectName, projectKey);

        try {
            restTemplate.exchange(BITBUCKET_SERVER_URL + "/rest/api/1.0/projects", HttpMethod.POST, entity, Map.class);
            grantGroupPermissionToProject(projectKey, ADMIN_GROUP_NAME, "PROJECT_ADMIN"); // admins get administrative permissions
            grantGroupPermissionToProject(projectKey, programmingExercise.getCourse().getInstructorGroupName(), "PROJECT_ADMIN"); // instructors get administrative permissions
            grantGroupPermissionToProject(projectKey, programmingExercise.getCourse().getTeachingAssistantGroupName(), "PROJECT_WRITE"); // teachingAssistants get write-permissions

        } catch (HttpClientErrorException e) {
            log.error("Could not create Bitbucket project {} with key {}", projectName, projectKey, e);
            throw new BitbucketException("Error while creating Bitbucket project");
        }
    }

    /**
     * Create a new repo
     *
     * @param repoName The project name
     * @param projectKey  The project key of the parent project
     * @throws BitbucketException if the repo could not be created
     */
    private void createRepository(String projectKey, String repoName) throws BitbucketException {
        HttpHeaders headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);

        Map<String, Object> body = new HashMap<>();
        body.put("name", repoName);
        HttpEntity<?> entity = new HttpEntity<>(body, headers);

        RestTemplate restTemplate = new RestTemplate();

        log.debug("Creating Bitbucket repo {} with parent key {}", repoName, projectKey);

        try {
            restTemplate.exchange(
                BITBUCKET_SERVER_URL + "/rest/api/1.0/projects/" + projectKey + "/repos",
                HttpMethod.POST,
                entity,
                Map.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                log.info("Repository {} (parent {}) already exists, reusing it...", repoName, projectKey);
                return;
            }
            log.error("Could not create Bitbucket repo {} with projectKey key {}", repoName, projectKey, e);
            throw new BitbucketException("Error while creating Bitbucket repo");
        }
    }

    public void grantGroupPermissionToProject(String projectKey, String groupName, String permission) {
        String baseUrl = BITBUCKET_SERVER_URL + "/rest/api/1.0/projects/" + projectKey + "/permissions/groups/?name="; // GROUPNAME&PERMISSION
        HttpHeaders headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(baseUrl + groupName + "&permission=" + permission, HttpMethod.PUT, entity, Map.class);
        } catch (Exception e) {
            log.error("Could not give project permission", e);
            throw new BitbucketException("Error while giving project permissions");
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
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(
                baseUrl,
                HttpMethod.GET,
                entity,
                Map.class);
        } catch (Exception e) {
            log.error("Error while getting existing WebHooks", e);
            throw new BitbucketException("Error while getting existing WebHooks", e);
        }

        Map<Integer, String> webHooks = new HashMap<>();

        if (response != null && response.getStatusCode().equals(HttpStatus.OK)) {
            // TODO: BitBucket uses a pagination API to split up the responses, so we might have to check all pages
            List<Map<String, Object>> rawWebHooks = (List<Map<String, Object>>) response.getBody().get("values");
            for (Map<String, Object> rawWebHook: rawWebHooks) {
                webHooks.put((Integer) rawWebHook.get("id"), (String) rawWebHook.get("url"));
            }
            return webHooks;
        }
        log.error("Error while getting existing WebHooks for {}-{}: Invalid response", projectKey, repositorySlug);
        throw new BitbucketException("Error while getting existing WebHooks: Invalid response");
    }

    private boolean webHookExists(String projectKey, String repositorySlug, String notificationUrl) {
        Map<Integer, String> webHooks = getExistingWebHooks(projectKey, repositorySlug);
        return webHooks.values().contains(notificationUrl);
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

        RestTemplate restTemplate = new RestTemplate();

        try {
            restTemplate.exchange(
                baseUrl,
                HttpMethod.POST,
                entity,
                Map.class);
        } catch (HttpClientErrorException e) {
            log.error("Could not add create WebHook for {}-{} ({})", projectKey, repositorySlug, notificationUrl, e);
            throw new BitbucketException("Error while creating WebHook");
        }
    }

    private void deleteWebHook(String projectKey, String repositorySlug, Integer webHookId) {
        String baseUrl = BITBUCKET_SERVER_URL + "/rest/api/1.0/projects/" + projectKey + "/repos/" + repositorySlug + "/webhooks/" + webHookId;
        log.info("Delete WebHook {} on project {}-{}", webHookId, projectKey, repositorySlug);
        HttpHeaders headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(baseUrl, HttpMethod.DELETE, entity, Map.class);
        } catch (Exception e) {
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
        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(baseUrl, HttpMethod.DELETE, entity, Map.class);
        } catch (Exception e) {
            log.error("Could not delete repository", e);
        }
    }

    /**
     *  Check if the given repository url is valid and accessible on Bitbucket.
     * @param repositoryUrl
     * @return
     */
    @Override
    public Boolean repositoryUrlIsValid(URL repositoryUrl) {
        String projectKey;
        String repositorySlug;
        try {
            projectKey = getProjectKeyFromUrl(repositoryUrl);
            repositorySlug = getRepositorySlugFromUrl(repositoryUrl);
        } catch (BitbucketException e) {
            // Either the project Key or the repository slug could not be extracted, therefor this can't be a valid URL
            return false;
        }

        HttpHeaders headers = HeaderUtil.createAuthorization(BITBUCKET_USER, BITBUCKET_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(BITBUCKET_SERVER_URL + "/rest/api/1.0/projects/" + projectKey + "/repos/" + repositorySlug, HttpMethod.GET, entity, Map.class);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public String getLastCommitHash(Object requestBody) throws BitbucketException {
        // https://confluence.atlassian.com/bitbucket/event-payloads-740262817.html
        try {
            Map<String, Object> requestBodyMap = (Map<String, Object>) requestBody;
            Map<String, Object> push = (Map<String, Object>) requestBodyMap.get("push");
            List<Object> changes = (List<Object>) push.get("changes");
            Map<String, Object> lastChange = (Map<String, Object>) changes.get(0);
            List<Object> commits = (List<Object>) lastChange.get("commits");
            Map<String, Object> lastCommit = (Map<String, Object>) commits.get(0);
            String hash = (String) lastCommit.get("hash");

            return hash;
        } catch (Exception e) {
            log.error("Error when getting hash of last commit");
            throw new BitbucketException("Could not get hash of last commit", e);
        }
    }

    @Override
    public void createRepository(String entityName, String topLevelEntity, String parentEntity) throws Exception {
        createRepository(entityName, topLevelEntity);
    }

    @Override
    public String getProjectName(URL repositoryUrl) {
        return getProjectKeyFromUrl(repositoryUrl);
    }

    @Override
    public String getRepositoryName(URL repositoryUrl) {
        return getRepositorySlugFromUrl(repositoryUrl);
    }

    private String buildRepositoryPath(String projectKey, String repositorySlug) {
        return BITBUCKET_SERVER_URL.getPath() + "/scm/" + projectKey + "/" + repositorySlug + ".git";
    }

    private URL buildCloneUrl(String projectKey, String repositorySlug, String username) {
        URL cloneUrl = null;
        try {
            cloneUrl = new URL(BITBUCKET_SERVER_URL.getProtocol() + "://" + username + "@" + BITBUCKET_SERVER_URL.getAuthority() + buildRepositoryPath(projectKey, repositorySlug));
        } catch (MalformedURLException e) {
            log.error("Could not build clone URL", e);
        }
        return cloneUrl;
    }
}
