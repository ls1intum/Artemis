package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Participation;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Profile("bitbucket")
public class BitbucketService implements VersionControlService {

    private final Logger log = LoggerFactory.getLogger(BitbucketService.class);

    @Value("${artemis.bitbucket.url}")
    private URL BITBUCKET_SERVER_URL;

    @Value("${artemis.bitbucket.user}")
    private String BITBUCKET_USER;

    @Value("${artemis.bitbucket.password}")
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

    private String getProjectKeyFromUrl(URL repositoryUrl) {
        // https://ga42xab@repobruegge.in.tum.de/scm/EIST2016RME/RMEXERCISE-ga42xab.git
        String[] urlParts = repositoryUrl.getFile().split("/");
        if (urlParts.length > 2) {
            return urlParts[2];
        }
        return "";
    }

    private String getRepositorySlugFromUrl(URL repositoryUrl) {
        // https://ga42xab@repobruegge.in.tum.de/scm/EIST2016RME/RMEXERCISE-ga42xab.git
        String[] urlParts = repositoryUrl.getFile().split("/");
        if (urlParts.length > 3) {
            String repositorySlug = urlParts[3];
            if (repositorySlug.endsWith(".git")) {
                repositorySlug = repositorySlug.substring(0, repositorySlug.length() - 4);
            }
            return repositorySlug;
        }
        return "";
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
        String projectKey = getProjectKeyFromUrl(repositoryUrl);
        String repositorySlug = getRepositorySlugFromUrl(repositoryUrl);
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

    private URL buildCloneUrl(String projectKey, String repositorySlug, String username) {
        URL cloneUrl = null;
        try {
            cloneUrl = new URL(BITBUCKET_SERVER_URL.getProtocol() + "://" + username + "@" + BITBUCKET_SERVER_URL.getAuthority() + BITBUCKET_SERVER_URL.getPath() + "/scm/" + projectKey + "/" + repositorySlug + ".git");
        } catch (MalformedURLException e) {
            log.error("Could not build clone URL", e);
        }
        return cloneUrl;
    }
}
