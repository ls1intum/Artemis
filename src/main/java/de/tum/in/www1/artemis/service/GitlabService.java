package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exception.GitlabException;
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
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Profile("gitlab")
public class GitlabService implements VersionControlService {

    private final Logger log = LoggerFactory.getLogger(GitlabService.class);

    @Value("${artemis.gitlab.url}")
    private URL GITLAB_SERVER_URL;

    @Value("${artemis.gitlab.user}")
    private String GITLAB_USER;

    @Value("${artemis.gitlab.private-token}")
    private String GITLAB_PRIVATE_TOKEN;

    @Value("${artemis.lti.user-prefix}")
    private String USER_PREFIX = "";

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


        if(username.startsWith(USER_PREFIX)) {
            // It is an automatically created user

            User user = userService.getUserByLogin(username).get();

            if (!userExists(username)) {
                log.debug("Gitlab user {} does not exist yet", username);
                String displayName = (user.getFirstName() + " " + user.getLastName()).trim();
                createUser(username, userService.decryptPasswordByLogin(username).get(), user.getEmail(), displayName);

                // addUserToGroups(username, user.getGroups()); // TODO: check if we need this

            } else {
                log.debug("Gitlab user {} already exists", username);
            }

        }

        giveWritePermission(repositoryUrl, username);
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

    private String getNamespaceFromUrl(URL repositoryUrl) {
        // https://ga42xab@gitlabbruegge.in.tum.de/EIST2016RME/RMEXERCISE-ga42xab.git
        return repositoryUrl.getFile().split("/")[1];
    }

    private String getProjectNameFromUrl(URL repositoryUrl) {
        // https://ga42xab@gitlabbruegge.in.tum.de/EIST2016RME/RMEXERCISE-ga42xab.git
        String repositoryName = repositoryUrl.getFile().split("/")[2];
        if (repositoryName.endsWith(".git")) {
            repositoryName = repositoryName.substring(0, repositoryName.length() - 4);
        }
        return repositoryName;
    }

    private String getURLEncodedIdentifier(URL repositoryUrl) {
        return getNamespaceFromUrl(repositoryUrl) + "%2F" + getProjectNameFromUrl(repositoryUrl);
    }

    /**
     * Uses the configured Gitlab account to create a new repository in the given namespace using the base repository.
     *
     * @param namespace          The namespace of the base project.
     * @param baseProjectName    The project name of the base repository.
     * @param username           The user for whom the repository is being forked.
     * @return The slug of the forked repository (i.e. its identifier).
     */
    private Map<String, String> createRepository(String namespace, String baseProjectName, String username) throws GitlabException {
        /*
         * In Gitlab, you cannot fork an existing repository with another name. Therefor we create a new repository and
         * specify the base repository as import_url. We authenticate with the private token and the according username.
         */
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
                GITLAB_SERVER_URL + "/api/v4/projects/",
                HttpMethod.POST,
                entity,
                Map.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().equals(HttpStatus.BAD_REQUEST)) {
                log.info("Repository already exists. Going to recover repository information...");
                Map<String, String> result = new HashMap<>();
                result.put("name", projectName);
                result.put("cloneUrl", buildCloneUrl(namespace, projectName, username).toString());
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
     * Creates an user on Gitlab
     *
     * @param username     The wanted Gitlab username
     * @param password     The wanted passowrd in clear text
     * @param emailAddress The eMail address for the user
     * @param displayName  The display name (full name)
     * @throws GitlabException
     */
    public void createUser(String username, String password, String emailAddress, String displayName) throws GitlabException {
        String baseUrl = GITLAB_SERVER_URL + "/api/v4/users";
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
     * Gives user write permissions for a repository.
     *
     * @param repositoryUrl  The repository's URL.
     * @param username       The user whom to give write permissions.
     * @throws GitlabException
     */
    private void giveWritePermission(URL repositoryUrl, String username) throws GitlabException {
        URI baseUri = buildUri(GITLAB_SERVER_URL + "/api/v4/projects/", getURLEncodedIdentifier(repositoryUrl), "/members");
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", getUserId(username));
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
     * Deletes the given repository from Gitlab.
     *
     * @param repositoryUrl    The repository's URL.
     */
    private void deleteRepositoryImpl(URL repositoryUrl) {
        URI baseUri = buildUri(GITLAB_SERVER_URL + "/api/v4/projects/", getURLEncodedIdentifier(repositoryUrl));
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

    /**
     *  Check if the given repository url is valid and accessible on Gitlab.
     * @param repositoryUrl
     * @return
     */
    @Override
    public Boolean repositoryUrlIsValid(URL repositoryUrl) {
        String projectIdentifier = getURLEncodedIdentifier(repositoryUrl);
        HttpHeaders headers = HeaderUtil.createPrivateTokenAuthorization(GITLAB_PRIVATE_TOKEN);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(buildUri(GITLAB_SERVER_URL + "/api/v4/projects/", projectIdentifier), HttpMethod.GET, entity, Map.class);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * Checks if an username exists on Gitlab
     *
     * @param username the Gitlab username to check
     * @return true if it exists
     * @throws GitlabException
     */
    private Boolean userExists(String username) throws GitlabException {
        HttpHeaders headers = HeaderUtil.createPrivateTokenAuthorization(GITLAB_PRIVATE_TOKEN);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<List> response;
        try {
            response = restTemplate.exchange(
                GITLAB_SERVER_URL + "/api/v4/users?username=" + username, // TODO: this seems to be undocumented, might break
                HttpMethod.GET,
                entity,
                List.class);
        } catch (HttpClientErrorException e) {
            log.error("Could not check if Gitlab user  " + username + " exists.", e);
            throw new GitlabException("Could not check if Gitlab user exists");
        }
        return !response.getBody().isEmpty();
    }

    /**
     *  Get the user id of the given user.
     * @param username The username
     * @return
     */
    private Integer getUserId(String username) {
        HttpHeaders headers = HeaderUtil.createPrivateTokenAuthorization(GITLAB_PRIVATE_TOKEN);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<List> response;
        try {
            response = restTemplate.exchange(
                GITLAB_SERVER_URL + "/api/v4/users?username=" + username,
                HttpMethod.GET,
                entity,
                List.class);
        } catch (Exception e) {
            log.error("Error while getting user id of user " + username);
            throw new GitlabException("Error while getting user id", e);
        }
        if (response != null && response.getStatusCode().equals(HttpStatus.OK)) {
            if (response.getBody().size() == 0) {
                log.error("Error while getting user id of user {}: no user found", username);
                throw new GitlabException("Error while getting user id: No user found");
            }
            // TODO: maybe parse this into an extra object?
            LinkedHashMap<String, Object> user = (LinkedHashMap<String, Object>) response.getBody().get(0);
            return (Integer) user.get("id");
        }
        return -1;
    }

    // TODO: check whether we have to support user-namespaces too
    /**
     *  Get the namespace id of the given namespace.
     * @param namespace The namespace
     * @return
     */
    private int getNamespaceId(String namespace) {
        HttpHeaders headers = HeaderUtil.createPrivateTokenAuthorization(GITLAB_PRIVATE_TOKEN);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(
                GITLAB_SERVER_URL + "/api/v4/groups/" + namespace,
                HttpMethod.GET,
                entity,
                Map.class);
        } catch (HttpClientErrorException e) {
                log.error("Error while getting namespace id of namespace " + namespace);
                throw new GitlabException("Error while getting namespace id", e);
        } catch (Exception e) {
            log.error("Error while getting namespace id of namespace " + namespace);
            throw new GitlabException("Error while getting namespace id", e);
        }
        if (response != null && response.getStatusCode().equals(HttpStatus.OK)) {
            return (int) response.getBody().get("id");
        }
        return -1;
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
     * Build an URI with already encoded components
     * @param url The static part of the URI
     * @param parameter The dynamic part of the URI
     * @return an URI containing url and parameter without additional encoding
     */
    private URI buildUri(String url, String parameter) {
        return buildUri(url, parameter, "");
    }

    /**
     * Build an URI with already encoded components
     * @param url The static part of the URI
     * @param parameter The dynamic part of the URI
     * @param staticParameter The additional static part after the dynamic parameter
     * @return an URI containing url, dynamic and static parameter without additional encoding
     */
    private URI buildUri(String url, String parameter, String staticParameter) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url).path(parameter).path(staticParameter);
        return builder.build(true).toUri();
    }
}
