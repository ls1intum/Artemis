package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exception.GitlabException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import net.sourceforge.plantuml.Url;
import org.apache.commons.lang.RandomStringUtils;
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
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Profile("gitlab")
public class GitlabService implements VersionControlService {

    private final Logger log = LoggerFactory.getLogger(GitlabService.class);

    @Value("${artemis.version-control.url}")
    private URL GITLAB_SERVER_URL;

    @Value("${artemis.version-control.user}")
    private String GITLAB_USER;

    @Value("${artemis.version-control.secret}")
    private String GITLAB_PRIVATE_TOKEN;

    @Value("${artemis.lti.user-prefix}")
    private String USER_PREFIX = "";

    @Value("${artemis.ldap.dn-prefix}")
    private String LDAP_DN_PREFIX = "";

    @Value("${artemis.ldap.dn-suffix}")
    private String LDAP_DN_SUFFIX = "";

    private final String API_PATH = "/api/v4/";

    private final int USER_NOT_FOUND = -1; // Should be negative to avoid collision with existing user id

    private final UserService userService;

    public GitlabService(UserService userService) {
        this.userService = userService;
    }


    @Override
    public URL copyRepository(URL baseRepositoryUrl, String username) {
        Map<String, String> result = this.createRepository(getNamespaceFromUrl(baseRepositoryUrl), getProjectNameFromUrl(baseRepositoryUrl), username);
        try {
            return new URL(result.get("cloneUrl"));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void configureRepository(URL repositoryUrl, String username) {
        User user = userService.getUserByLogin(username).get();
        String displayName = (user.getFirstName() + " " + user.getLastName()).trim();

        if(username.startsWith(USER_PREFIX)) {
            // It is an automatically created user

            if (!userExists(username)) {
                log.debug("Gitlab user {} does not exist yet", username);
                createUser(username, userService.decryptPasswordByLogin(username).get(), user.getEmail(), displayName);

            } else {
                log.debug("Gitlab user {} already exists", username);
            }

        } else {
            // It is an user connected to LDAP
            if (!userExists(username) && (!LDAP_DN_PREFIX.equals("") || !LDAP_DN_SUFFIX.equals(""))) {
                log.debug("Gitlab user {} (LDAP) does not exist yet", username);
                createUserExternalProvider(username, user.getEmail(), displayName, "ldapmain", LDAP_DN_PREFIX + username + LDAP_DN_SUFFIX); // This will create a User linked to the default LDAP provider

            } else if (LDAP_DN_PREFIX.equals("") && LDAP_DN_SUFFIX.equals("")) {
                log.debug("No LDAP provider set for user {}", username);

            } else {
                log.debug("Gitlab user {} (LDAP) already exists", username);
            }
        }

        giveWritePermissionRepository(repositoryUrl, username);
    }

    @Override
    public void addWebHook(URL repositoryUrl, String notificationUrl, String webHookName) {
        // Gitlab does not support webhooks with names, therefor we don't use the 'webHookName'-value
        if (!webHookExists(repositoryUrl, notificationUrl)) {
            createWebHook(repositoryUrl, notificationUrl);
        }
    }

    @Override
    public void addBambooService(String vcsTopLevelIdentifier, String vcsLowerLevelIdentifier, String bambooUrl, String buildKey, String bambooUsername, String bambooPassword) {
        createBambooService(vcsTopLevelIdentifier, vcsLowerLevelIdentifier, bambooUrl, buildKey, bambooUsername, bambooPassword);
    }

    @Override
    public void deleteRepository(URL repositoryUrl) {
        deleteRepositoryImpl(repositoryUrl);
    }

    @Override
    public URL getRepositoryWebUrl(Participation participation) {
        try {
            return new URL(GITLAB_SERVER_URL+
                "/" + getNamespaceFromUrl(participation.getRepositoryUrlAsUrl()) +
                "/" + getProjectNameFromUrl(participation.getRepositoryUrlAsUrl()) + "/tree/master"); // TODO: Don't hardcode master branch
        } catch (MalformedURLException e) {
            log.error("Couldn't construct repository web URL");
        }
        return GITLAB_SERVER_URL;
    }

    public void createBambooService(String namespace, String project, String bambooUrl, String buildKey, String bambooUsername, String bambooPassword) {
        log.debug("Creating Bamboo-Service for Repository {}", getURLEncodedIdentifier(namespace, project));
        HttpHeaders headers = HeaderUtil.createPrivateTokenAuthorization(GITLAB_PRIVATE_TOKEN);

        URI baseUri = buildUri(GITLAB_SERVER_URL + API_PATH + "projects/", getURLEncodedIdentifier(namespace, project), "/services/bamboo");
        Map<String, Object> body = new HashMap<>();
        body.put("bamboo_url", bambooUrl);
        body.put("build_key", buildKey);
        body.put("username", bambooUsername);
        body.put("password", bambooPassword);

        HttpEntity<?> entity = new HttpEntity<>(body, headers);

        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(
                baseUri,
                HttpMethod.PUT,
                entity,
                Map.class);
        } catch(HttpClientErrorException e) {
            log.error("Could not create Bamboo Service (HTTP Error)", e);
            throw new GitlabException("Error while creating Bamboo Service (HTTP Error)");
        } catch (Exception e) {
            log.error("Could not create Bamboo Service {}", getURLEncodedIdentifier(namespace, project), e);
            throw new GitlabException("Error while creating Bamboo Service");
        }
    }

    /**
     * Gets the namespace from the given URL (including %2F if it is a subgroup)
     *
     * @param repositoryUrl The complete repository-url (including protocol, host and the complete path)
     * @return The namespace
     * @throws GitlabException if the URL is invalid and no namespace could be extracted
     */
    private String getNamespaceFromUrl(URL repositoryUrl) throws GitlabException {
        // https://ga42xab@gitlabbruegge.in.tum.de/EIST2016/RME/RMEXERCISE-ga42xab.git -> EIST2016&2FRME (see the "%2F")
        String[] urlParts = repositoryUrl.getFile().split("/");
        if (urlParts.length > 2) { // Can have a subgroup or no subgroup
            return urlParts[1] + "%2F" + urlParts[2];
        } else if (urlParts.length > 1) {
            return urlParts[1];
        }

        log.error("No namespace could be found for repository {}", repositoryUrl);
        throw new GitlabException("No namespace could be found");
    }

    /**
     * Gets the project name from the given URL
     *
     * @param repositoryUrl The complete repository-url (including protocol, host and the complete path)
     * @return The project name
     * @throws GitlabException if the URL is invalid and no project name could be extracted
     */
    private String getProjectNameFromUrl(URL repositoryUrl) {
        // https://ga42xab@gitlabbruegge.in.tum.de/EIST2016/RME/RMEXERCISE-ga42xab.git -> RMEXERCISE-ga42xab
        String[] urlParts = repositoryUrl.getFile().split("/");

        String repositoryName = null;
        if (urlParts.length > 3) { // Can have a subgroup or no subgroup
            repositoryName = urlParts[3];
        } else if (urlParts.length > 2) {
            repositoryName = urlParts[2];
        }

        if (repositoryName != null) {
            if (repositoryName.endsWith(".git")) {
                repositoryName = repositoryName.substring(0, repositoryName.length() - 4);
            }
            return repositoryName;
        }

        log.error("No project name could be found for repository {}", repositoryUrl);
        throw new GitlabException("No project name could be found");
    }

    /**
     * This returns the URL encoded Identifier (consisting of the namespace and the projectname.
     *
     * @param repositoryUrl  The repositoryUrl
     * @return The already encoded identifier
     */
    private String getURLEncodedIdentifier(URL repositoryUrl) {
        return getNamespaceFromUrl(repositoryUrl) + "%2F" + getProjectNameFromUrl(repositoryUrl);
    }

    /**
     * This returns the URL encoded Identifier (consisting of the namespace and the projectname.
     *
     * @param namespace      The namespace
     * @param projectName    The projectName
     * @return The already encoded identifier
     */
    private String getURLEncodedIdentifier(String namespace, String projectName) {
        return namespace + "%2F" + projectName;
    }

    /**
     * Uses the configured Gitlab account to create a new repository in the given namespace using the base repository.
     *
     * @param namespace          The namespace of the base project.
     * @param baseProjectName    The project name of the base repository.
     * @param username           The user for whom the repository is being forked.
     * @return The name of the forked repository
     * @throws GitlabException if the creation of the repository failed
     */
    private Map<String, String> createRepository(String namespace, String baseProjectName, String username) throws GitlabException {
        /*
         * In Gitlab, you cannot fork an existing repository with another name. Therefor we create a new repository and
         * specify the base repository as import_url. We authenticate with the private token and the according username.
         */
        // TODO: Check if we have to wait for a constant amount of time / check the import status as the import is done asynchronous by Gitlab
        String projectName = String.format("%s-%s", baseProjectName, username);
        Map<String, Object> body = new HashMap<>();
        body.put("name", projectName);
        body.put("namespace_id", getNamespaceId(namespace));
        body.put("import_url", GITLAB_SERVER_URL.getProtocol() + "://" + GITLAB_USER + ":" + GITLAB_PRIVATE_TOKEN + "@" +
            GITLAB_SERVER_URL.getAuthority() + GITLAB_SERVER_URL.getPath() + "/" + namespace + "/" + baseProjectName + ".git");

        HttpHeaders headers = HeaderUtil.createPrivateTokenAuthorization(GITLAB_PRIVATE_TOKEN);
        HttpEntity<?> entity = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(
                GITLAB_SERVER_URL + API_PATH + "projects/",
                HttpMethod.POST,
                entity,
                Map.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().equals(HttpStatus.BAD_REQUEST)) {
                log.info("Repository already exists. Going to recover repository information...");
                Map<String, String> result = new HashMap<>();
                result.put("name", projectName);
                result.put("cloneUrl", buildCloneUrl(namespace, projectName, username).toString());
                // Delete existing WebHooks (partipation ID might have changed)
                deleteExistingWebHooks(getURLEncodedIdentifier(namespace, projectName));
                return result;
            } else {
                throw e;
            }
        } catch (Exception e) {
            log.error("Could not fork base repository for user " + username, e);
            throw new GitlabException("Error while forking repository");
        }
        if (response != null && response.getStatusCode().equals(HttpStatus.CREATED)) {
            String name = (String) response.getBody().get("name");
            String cloneUrl = buildCloneUrl(namespace, projectName, username).toString();
            Map<String, String> result = new HashMap<>();
            result.put("name", name);
            result.put("cloneUrl", cloneUrl);
            return result;
        }
        return null;
    }

    /**
     * Creates an user on Gitlab with the given credentials
     *
     * @param username     The wanted Gitlab username
     * @param password     The wanted password in clear text
     * @param emailAddress The eMail address for the user
     * @param displayName  The display name (full name)
     * @throws GitlabException if the user could not be created
     */
    private void createUser(String username, String password, String emailAddress, String displayName) throws GitlabException {
        String baseUrl = GITLAB_SERVER_URL + API_PATH + "users";
        Map<String, Object> body = new HashMap<>();
        body.put("email", emailAddress);
        body.put("username", username);
        body.put("password", password);
        body.put("name", displayName);
        body.put("skip_confirmation", true); // User should be able to login immediately

        HttpHeaders headers = HeaderUtil.createPrivateTokenAuthorization(GITLAB_PRIVATE_TOKEN);
        HttpEntity<?> entity = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();

        log.debug("Creating Gitlab user {} ({})", username, emailAddress);

        try {
            restTemplate.exchange(
                baseUrl,
                HttpMethod.POST,
                entity,
                Map.class);
        } catch (HttpClientErrorException e) {
            log.error("Could not create Gitlab user " + username, e);
            throw new GitlabException("Error while creating user");
        }
    }

    /**
     * Creates an user on Gitlab and links it to the given provider. This will create a random password for the user,
     * which will neither shown to the user nor saved in ArTEMiS.
     *
     * @param username     The wanted Gitlab username
     * @param emailAddress The eMail address for the user
     * @param displayName  The display name (full name)
     * @param provider     The provider for the login credentials (e.g. 'ldapmain' for the default LDAP provider)
     * @param externUid    The extern Uid used to link to the account of the provider
     * @throws GitlabException if the user could not be created
     */
    private void createUserExternalProvider(String username, String emailAddress, String displayName, String provider, String externUid) throws GitlabException {
        // Create a 15 character random password that will not be used as the user will login using his LDAP credentials, but a password is needed for the Gitlab-API
        // https://stackoverflow.com/questions/31260512/generate-a-secure-random-password-in-java-with-minimum-special-character-require
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789~`!@#$%^&*()-_=+[{]}\\|;:\'\",<.>/?";
        String randomPassword = RandomStringUtils.random( 15, characters );

        String baseUrl = GITLAB_SERVER_URL + API_PATH + "users";
        Map<String, Object> body = new HashMap<>();
        body.put("email", emailAddress);
        body.put("username", username);
        body.put("password", randomPassword);
        body.put("name", displayName);
        body.put("skip_confirmation", true); // User should be able to login immediately
        body.put("provider", provider);
        body.put("extern_uid", externUid);

        HttpHeaders headers = HeaderUtil.createPrivateTokenAuthorization(GITLAB_PRIVATE_TOKEN);
        HttpEntity<?> entity = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();

        log.debug("Creating Gitlab user {} (External Provider) ({})", username, emailAddress);

        try {
            restTemplate.exchange(
                baseUrl,
                HttpMethod.POST,
                entity,
                Map.class);
        } catch (HttpClientErrorException e) {
            log.error("Could not create Gitlab user {} (External Provider)", username, e);
            throw new GitlabException("Error while creating user (External Provider)");
        }
    }

    /**
     * Gives user write permissions for a repository.
     *
     * @param repositoryUrl  The repository's URL.
     * @param username       The user whom to give write permissions.
     * @throws GitlabException if the permission could not be granted
     */
    private void giveWritePermissionRepository(URL repositoryUrl, String username) throws GitlabException {
        URI baseUri = buildUri(GITLAB_SERVER_URL + API_PATH + "projects/", getURLEncodedIdentifier(repositoryUrl), "/members");
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", getUserId(username, true));
        body.put("access_level", 30); // TODO: make this configurable? Access Level 30 equals Developer

        HttpHeaders headers = HeaderUtil.createPrivateTokenAuthorization(GITLAB_PRIVATE_TOKEN);
        HttpEntity<?> entity = new HttpEntity<>(body, headers);

        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(
                baseUri,
                HttpMethod.POST,
                entity,
                Map.class);
        } catch(HttpClientErrorException e) {
            if (e.getStatusCode().equals(HttpStatus.CONFLICT)) {
                //TODO: Maybe check if the user has at least the access_level that would be given to him
                log.info("User already had permission. Assuming he has the correct permission.");
                return;
            }
            log.error("Could not give write permission", e);
            throw new GitlabException("Error while giving repository permissions");
        } catch (Exception e) {
            log.error("Could not give write permission", e);
            throw new GitlabException("Error while giving repository permissions");
        }
    }

    /**
     * Gives user permission for a group.
     *
     * @param groupIdentifer  The group's identifier
     * @param username        The user whom to give write permissions
     * @param permissionLevel The level of permission the user should be granted
     * @throws GitlabException if the permission could not be granted
     */
    private void giveGroupPermission(String groupIdentifer, String username, Integer permissionLevel) throws GitlabException {
        URI baseUri = buildUri(GITLAB_SERVER_URL + API_PATH + "groups/", groupIdentifer, "/members");
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", getUserId(username, true));
        body.put("access_level", permissionLevel);

        HttpHeaders headers = HeaderUtil.createPrivateTokenAuthorization(GITLAB_PRIVATE_TOKEN);
        HttpEntity<?> entity = new HttpEntity<>(body, headers);

        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(
                baseUri,
                HttpMethod.POST,
                entity,
                Map.class);
        } catch(HttpClientErrorException e) {
            if (e.getStatusCode().equals(HttpStatus.CONFLICT)) {
                //TODO: Maybe check if the user has at least the access_level that would be given to him
                log.info("User already had permission. Assuming he has the correct permission.");
                return;
            }
            log.error("Could not give permission", e);
            throw new GitlabException("Error while giving group permissions");
        } catch (Exception e) {
            log.error("Could not give permission", e);
            throw new GitlabException("Error while giving group permissions");
        }
    }

    /**
     * Get all existing WebHooks for a specific repository.
     *
     * @param urlEncodedIdentifier The already encoded identifier of the repository
     * @return A map of all ids of the WebHooks to the URL they notify.
     * @throws GitlabException if the request to get the WebHooks failed
     */
    private Map<Integer, String> getExistingWebHooks(String urlEncodedIdentifier) {
        URI baseUri = buildUri(GITLAB_SERVER_URL + API_PATH + "projects/", urlEncodedIdentifier, "/hooks");
        HttpHeaders headers = HeaderUtil.createPrivateTokenAuthorization(GITLAB_PRIVATE_TOKEN);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<List> response;
        try {
            response = restTemplate.exchange(
                baseUri,
                HttpMethod.GET,
                entity,
                List.class);
        } catch (Exception e) {
            log.error("Error while getting existing WebHooks for  " + urlEncodedIdentifier);
            throw new GitlabException("Error while getting existing WebHooks", e);
        }

        Map<Integer, String> webHooks = new HashMap<>();

        if (response != null && response.getStatusCode().equals(HttpStatus.OK)) {

            // Check all WebHooks for same URL
            for (Map<String, Object> webHook : (List<Map<String, Object>>) response.getBody()) {
                webHooks.put((Integer) (webHook.get("id")), (String) webHook.get("url"));
            }
            return webHooks;
        }
        log.error("Error while getting existing WebHooks for {}: Invalid response", urlEncodedIdentifier);
        throw new GitlabException("Error while getting existing WebHooks: Invalid response");
    }

    private boolean webHookExists(URL repositoryUrl, String notificationUrl) {
        Map<Integer, String> webHooks = getExistingWebHooks(getURLEncodedIdentifier(repositoryUrl));
        return webHooks.values().contains(notificationUrl);
    }

    private void createWebHook(URL repositoryUrl, String notificationUrl) {
        log.debug("Creating WebHook for Repository {} and URL {}", getURLEncodedIdentifier(repositoryUrl), notificationUrl);
        HttpHeaders headers = HeaderUtil.createPrivateTokenAuthorization(GITLAB_PRIVATE_TOKEN);

        URI baseUri = buildUri(GITLAB_SERVER_URL + API_PATH + "projects/", getURLEncodedIdentifier(repositoryUrl), "/hooks");
        Map<String, Object> body = new HashMap<>();
        body.put("url", notificationUrl);
        body.put("push_events", true); // Inform about pushes
        //TODO: We might want to add a token to ensure the notification is valid


        HttpEntity<?> entity = new HttpEntity<>(body, headers);

        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(
                baseUri,
                HttpMethod.POST,
                entity,
                Map.class);
        } catch(HttpClientErrorException e) {
            log.error("Could not create WebHook (HTTP Error)", e);
            throw new GitlabException("Error while creating WebHook (HTTP Error)");
        } catch (Exception e) {
            log.error("Could not create WebHook {} ({})", getURLEncodedIdentifier(repositoryUrl), notificationUrl, e);
            throw new GitlabException("Error while creating WebHook");
        }
    }

    private void deleteWebHook(String urlEncodedIdentifier, Integer webHookId) {
        URI baseUri = buildUri(GITLAB_SERVER_URL + API_PATH + "projects/", urlEncodedIdentifier, "/hooks/" + webHookId);
        log.info("Delete WebHook {} on project {}", webHookId, urlEncodedIdentifier);
        HttpHeaders headers = HeaderUtil.createPrivateTokenAuthorization(GITLAB_PRIVATE_TOKEN);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(baseUri, HttpMethod.DELETE, entity, Map.class);
        } catch (Exception e) {
            log.error("Could not delete WebHook", e);
        }
    }

    private void deleteExistingWebHooks(String urlEncodedIdentifier) {
        Map<Integer, String> webHooks = getExistingWebHooks(urlEncodedIdentifier);
        for (Integer webHookId : webHooks.keySet()) {
            deleteWebHook(urlEncodedIdentifier, webHookId);
        }
    }

    /**
     * Deletes the given repository from Gitlab.
     *
     * @param repositoryUrl    The repository's URL.
     */
    private void deleteRepositoryImpl(URL repositoryUrl) {
        URI baseUri = buildUri(GITLAB_SERVER_URL + API_PATH + "projects/", getURLEncodedIdentifier(repositoryUrl));
        log.info("Delete repository " + baseUri);
        HttpHeaders headers = HeaderUtil.createPrivateTokenAuthorization(GITLAB_PRIVATE_TOKEN);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(baseUri, HttpMethod.DELETE, entity, Map.class);
        } catch (Exception e) {
            log.error("Could not delete repository", e);
        }
    }

    @Override
    public Boolean repositoryUrlIsValid(URL repositoryUrl) {
        String projectIdentifier;
        try {
            projectIdentifier = getURLEncodedIdentifier(repositoryUrl);
        } catch (GitlabException e) {
            return false;
        }
        HttpHeaders headers = HeaderUtil.createPrivateTokenAuthorization(GITLAB_PRIVATE_TOKEN);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(buildUri(GITLAB_SERVER_URL + API_PATH + "projects/", projectIdentifier), HttpMethod.GET, entity, Map.class);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public String getLastCommitHash(Object requestBody) throws GitlabException {
        // https://docs.gitlab.com/ee/user/project/integrations/webhooks.html#push-events
        try {
            Map<String, Object> requestBodyMap = (Map<String, Object>) requestBody;
            String hash = (String) requestBodyMap.get("after");

            return hash;
        } catch (Exception e) {
            log.error("Error when getting hash of last commit");
            throw new GitlabException("Could not get hash of last commit", e);
        }
    }

    @Override
    public void createTopLevelEntity(String entityName, String parentEntity) throws Exception {
        // TODO: Reimplement this correctly when https://gitlab.com/gitlab-org/gitlab-ce/issues/33054 is live
        /*
        Integer parentGroupId;
        try {
            parentGroupId = getNamespaceId(parentEntity);
        } catch (GitlabException e) {
            log.error("Error when getting id of group {}", parentEntity);
            throw new GitlabException("Error when getting id of group", e);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("name", entityName);
        body.put("path", entityName.toLowerCase());
        body.put("parent_id", parentGroupId);

        HttpHeaders headers = HeaderUtil.createPrivateTokenAuthorization(GITLAB_PRIVATE_TOKEN);
        HttpEntity<?> entity = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(
                GITLAB_SERVER_URL + API_PATH + "groups/",
                HttpMethod.POST,
                entity,
                Map.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().equals(HttpStatus.BAD_REQUEST)) {
                log.info("Subgroup already exists...");
            } else {
                throw e;
            }
        } catch (Exception e) {
            log.error("Could create SubGroup {} for group {} " + entityName, parentEntity, e);
            throw new GitlabException("Error while creating subgroup");
        }
        */
    }

    @Override
    public void createLowerLevelEntity(String name, String topLevelEntity, String parentEntity) throws Exception {
        // TODO: Reimplement this correctly when https://gitlab.com/gitlab-org/gitlab-ce/issues/33054 is live
        // TODO: we might also import a template
    }

    @Override
    public void grantInstructorPermission(String username, String topLevelEntity, String parentEntity) {
        // TODO: Reimplement this correctly when https://gitlab.com/gitlab-org/gitlab-ce/issues/33054 is live
        // giveGroupPermission(getURLEncodedIdentifier(parentEntity, topLevelEntity), username, 40);
    }

    @Override
    public void grantTutorPermission(String username, String topLevelEntity, String parentEntity) {
        // TODO: Reimplement this correctly when https://gitlab.com/gitlab-org/gitlab-ce/issues/33054 is live
        // giveGroupPermission(getURLEncodedIdentifier(parentEntity, topLevelEntity), username, 30);
    }

    @Override
    public String getTopLevelIdentifier(URL repositoryUrl) {
        return getNamespaceFromUrl(repositoryUrl);
    }

    @Override
    public String getLowerLevelIdentifier(URL repositoryUrl) {
        return getProjectNameFromUrl(repositoryUrl);
    }

    /**
     * Checks if an username exists on Gitlab
     *
     * @param username       The Gitlab username to check
     * @return true if it exists
     * @throws GitlabException if the user could not be requested
     */
    private Boolean userExists(String username) throws GitlabException {
        return getUserId(username, false) != USER_NOT_FOUND;
    }

    /**
     * Get the user id of the given user.
     *
     * @param username       The username
     * @param expectExistence Whether the user is expected to be existing.
     * @return The user id of the given user, or USER_NOT_FOUND if no user exists and expectMissingUser is false
     * @throws GitlabException if the user id could not be requested OR the user does not exist but is expected to be existing.
     */
    private Integer getUserId(String username, boolean expectExistence) throws GitlabException {
        HttpHeaders headers = HeaderUtil.createPrivateTokenAuthorization(GITLAB_PRIVATE_TOKEN);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<List> response;
        try {
            response = restTemplate.exchange(
                GITLAB_SERVER_URL + API_PATH + "users?username=" + username,
                HttpMethod.GET,
                entity,
                List.class);
        } catch (Exception e) {
            log.error("Error while getting user id of user " + username);
            throw new GitlabException("Error while getting user id", e);
        }

        if (response != null && response.getStatusCode().equals(HttpStatus.OK)) {
            if (response.getBody().size() == 0) {
                if (!expectExistence) { // User is not expected to be existing (this is for the userExists-method)
                    return USER_NOT_FOUND; // User is not existing -> return USER_NOT_FOUND
                }
                log.error("Error while getting user id of user {}: no user found", username);
                throw new GitlabException("Error while getting user id: No user found");
            }

            Map<String, Object> user = (Map<String, Object>) response.getBody().get(0);
            return (Integer) user.get("id");
        }
        log.error("Error while getting user id of user {}: Invalid response", username);
        throw new GitlabException("Error while getting user id: Invalid response");
    }

    /**
     * Get the namespace id of the given namespace.
     *
     * @param namespace      The namespace
     * @return The id of the namespace
     * @throws GitlabException if the namespace id could not be requested.
     */
    private Integer getNamespaceId(String namespace) throws GitlabException {
        HttpHeaders headers = HeaderUtil.createPrivateTokenAuthorization(GITLAB_PRIVATE_TOKEN);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response;
        try {
            /*
             * In Gitlab, a namespace can either be an user or a group. As all participations of a specified exercise
             * should be in the same namespace, only a group-namespace is applicable.
             */
            response = restTemplate.exchange(
                GITLAB_SERVER_URL + API_PATH + "groups/" + namespace,
                HttpMethod.GET,
                entity,
                Map.class);
        } catch (Exception e) {
            log.error("Error while getting namespace id of namespace " + namespace);
            throw new GitlabException("Error while getting namespace id", e);
        }
        if (response != null && response.getStatusCode().equals(HttpStatus.OK)) {
            return (int) response.getBody().get("id");
        }
        return null;
    }

    private URL buildCloneUrl(String namespace, String projectName, String username) {
        URL cloneUrl = null;
        try {
            cloneUrl = new URL(GITLAB_SERVER_URL.getProtocol() + "://" + username + "@" + GITLAB_SERVER_URL.getAuthority() + GITLAB_SERVER_URL.getPath() + "/" + namespace + "/" + projectName + ".git");
        } catch (MalformedURLException e) {
            log.error("Could not build clone URL", e);
        }
        return cloneUrl;
    }

    /**
     * Build an URI with already encoded components. No additional characters (like slashes are added in between the components.
     * This is needed as Gitlab uses URL-encoded namespaces, we therefor don't want the URL to be encoded twice.
     * This URI consists of 2 components, therefor the URL-encoded namespace is the last part of the URI.
     * Also see {@link #buildUri(String, String, String)}.
     *
     * @param url            The static part of the URI
     * @param parameter      The dynamic part of the URI
     * @return an URI containing url and parameter without additional encoding
     */
    private URI buildUri(String url, String parameter) {
        return buildUri(url, parameter, "");
    }

    /**
     * Build an URI with already encoded components. No additional characters (like slashes are added in between the components.
     * This is needed as Gitlab uses URL-encoded namespaces, we therefor don't want the URL to be encoded twice.
     * This URI consists of 3 components, therefor the URL-encoded namespace is the middle part of the URI,
     * and other static part is added afterwards. Also see {@link #buildUri(String, String)}.
     *
     * @param url            The static part of the URI
     * @param parameter      The dynamic part of the URI
     * @param staticParameter The additional static part after the dynamic parameter
     * @return an URI containing url, dynamic and static parameter without additional encoding
     */
    private URI buildUri(String url, String parameter, String staticParameter) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url).path(parameter).path(staticParameter);
        return builder.build(true).toUri();
    }
}
