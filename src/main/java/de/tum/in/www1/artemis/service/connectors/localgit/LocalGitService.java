package de.tum.in.www1.artemis.service.connectors.localgit;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exception.BitbucketException;
import de.tum.in.www1.artemis.exception.LocalGitException;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.UrlService;
import de.tum.in.www1.artemis.service.connectors.AbstractVersionControlService;
import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.VersionControlRepositoryPermission;
import de.tum.in.www1.artemis.service.connectors.bitbucket.BitbucketPermission;
import de.tum.in.www1.artemis.service.connectors.bitbucket.dto.*;
import de.tum.in.www1.artemis.service.connectors.localgit.dto.LocalGitProjectDTO;

@Service
@Profile("localgit")
public class LocalGitService extends AbstractVersionControlService {

    private static final int MAX_GIVE_PERMISSIONS_RETRIES = 5;

    private final Logger log = LoggerFactory.getLogger(LocalGitService.class);

    @Value("${artemis.user-management.external.admin-group-name}")
    private String adminGroupName;

    @Value("${artemis.version-control.url}")
    private URL localGitServerUrl;

    @Value("${artemis.local-git-server-path}")
    private String localGitPath;

    @Value("${artemis.git.name}")
    private String artemisGitName;

    private final UserRepository userRepository;

    private final RestTemplate restTemplate;

    private final RestTemplate shortTimeoutRestTemplate;

    public LocalGitService(@Qualifier("localGitRestTemplate") RestTemplate restTemplate, UserRepository userRepository, UrlService urlService,
                           @Qualifier("shortTimeoutLocalGitRestTemplate") RestTemplate shortTimeoutRestTemplate, GitService gitService, ApplicationContext applicationContext,
                           ProgrammingExerciseStudentParticipationRepository studentParticipationRepository, ProgrammingExerciseRepository programmingExerciseRepository) {
        super(applicationContext, gitService, urlService, studentParticipationRepository, programmingExerciseRepository);
        this.userRepository = userRepository;
        this.restTemplate = restTemplate;
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
    }

    @Override
    public void configureRepository(ProgrammingExercise exercise, ProgrammingExerciseStudentParticipation participation, boolean allowAccess) {
        for (User user : participation.getStudents()) {
            if (allowAccess && !Boolean.FALSE.equals(exercise.isAllowOfflineIde())) {
                // only add access to the repository if the offline IDE usage is NOT disallowed
                // NOTE: null values are interpreted as offline IDE is allowed
                addMemberToRepository(participation.getVcsRepositoryUrl(), user);
            }
        }
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
     * This method protects the repository on the Bitbucket server by using a
     * REST-call to setup branch protection.
     * The branch protection is applied to all branches and prevents rewriting the
     * history (force-pushes) and deletion of branches.
     *
     * projectKey     The project key of the repository that should be
     *                       protected
     * repositorySlug The slug of the repository that should be protected
     */
//    private void protectBranches(String projectKey, String repositorySlug) {
//        String baseUrl = bitbucketServerUrl + "/rest/branch-permissions/2.0/projects/" + projectKey + "/repos/" + repositorySlug + "/restrictions";
//        log.debug("Setting up branch protection for repository {}", repositorySlug);
//
//        // Payload according to
//        // https://docs.atlassian.com/bitbucket-server/rest/4.2.0/bitbucket-ref-restriction-rest.html
//        final var type = new BitbucketBranchProtectionDTO.TypeDTO("PATTERN", "Pattern");
//        // A wildcard (*) ist used to protect all branches
//        final var matcher = new BitbucketBranchProtectionDTO.MatcherDTO("*", "*", type, true);
//        // Prevent force-pushes
//        final var fastForwardOnlyProtection = new BitbucketBranchProtectionDTO("fast-forward-only", matcher);
//        // Prevent deletion of branches
//        final var noDeletesProtection = new BitbucketBranchProtectionDTO("no-deletes", matcher);
//        final var body = List.of(fastForwardOnlyProtection, noDeletesProtection);
//
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(new MediaType("application", "vnd.atl.bitbucket.bulk+json")); // Set content-type
//        // manually as required by
//        // Bitbucket
//        HttpEntity<?> entity = new HttpEntity<>(body, headers);
//        try {
//            restTemplate.exchange(baseUrl, HttpMethod.POST, entity, Object.class);
//        }
//        catch (Exception emAll) {
//            log.error("Exception occurred while protecting repository {}", repositorySlug, emAll);
//        }
//
//        log.debug("Branch protection for repository {} set up", repositorySlug);
//    }

    @Override
    protected void addWebHook(VcsRepositoryUrl repositoryUrl, String notificationUrl, String webHookName) {
        // Webhooks must not be added for the local git server. The JGitPushFilter notifies Artemis on every push.
        // Falls notwendig, kann ich hier einen Boolean für das Repository setzen "hasWebHook".
    }

    @Override
    protected void addAuthenticatedWebHook(VcsRepositoryUrl repositoryUrl, String notificationUrl, String webHookName, String secretToken) {
        // Not needed for Bitbucket
        throw new UnsupportedOperationException("Authenticated webhooks with Bitbucket are not supported!");
    }

    @Override
    public void deleteProject(String projectKey) {
        try {
            String folderName =  localGitPath + File.separator + projectKey;
            FileUtils.deleteDirectory(new File(folderName));
        }
        catch (IOException e) {
            log.error("Could not delete project", e);
        }
    }

    @Override
    public void deleteRepository(VcsRepositoryUrl repositoryUrl) {
        try {
            String folderName = localGitPath + repositoryUrl.folderNameForRepositoryUrl();
            FileUtils.deleteDirectory(new File(folderName));
        }
        catch (IOException e) {
            log.error("Could not delete repository", e);
        }
    }

    @Override
    public VcsRepositoryUrl getCloneRepositoryUrl(String projectKey, String repositorySlug) {
        return new LocalGitRepositoryUrl(projectKey, repositorySlug);
    }

    private LocalGitProjectDTO getLocalGitProject(String projectKey, String projectName) throws LocalGitException {

        // Try to find the folder in the file system. If it is not found, throw an exception.
        if (new File(localGitPath + "/" + projectKey).exists()) {
            return new LocalGitProjectDTO(projectKey, projectName);
        }
        else {
            throw new LocalGitException("Could not find local git project.");
        }
    }

    /**
     * Creates a user on Bitbucket
     *
     * @param username     The wanted Bitbucket username
     * @param password     The wanted password in clear text
     * @param emailAddress The eMail address for the user
     * @param displayName  The display name (full name)
     * @throws BitbucketException if the rest request to Bitbucket for creating the
     *                            user failed.
     */
    public void createUser(String username, String password, String emailAddress, String displayName) throws BitbucketException {
//        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(bitbucketServerUrl + "/rest/api/latest/admin/users").queryParam("name", username)
//            .queryParam("email", emailAddress).queryParam("emailAddress", emailAddress).queryParam("password", password).queryParam("displayName", displayName)
//            .queryParam("addToDefaultGroup", "true").queryParam("notify", "false");
//
//        log.debug("Creating Bitbucket user {} ({})", username, emailAddress);
//
//        try {
//            restTemplate.exchange(builder.build().encode().toUri(), HttpMethod.POST, null, Void.class);
//        }
//        catch (HttpClientErrorException e) {
//            log.error("Could not create Bitbucket user {}", username, e);
//            throw new BitbucketException("Error while creating user", e);
//        }
    }

    /**
     * Updates a user on Bitbucket
     *
     * @param username     The username of the user
     * @param emailAddress The new email address
     * @param displayName  The new display name
     * @throws BitbucketException the exception of the bitbucket system when updating the user does not work
     */
    public void updateUserDetails(String username, String emailAddress, String displayName) throws BitbucketException {
//        UriComponentsBuilder userDetailsBuilder = UriComponentsBuilder.fromHttpUrl(bitbucketServerUrl + "/rest/api/latest/admin/users");
//        Map<String, Object> userDetailsBody = new HashMap<>();
//        userDetailsBody.put("name", username);
//        userDetailsBody.put("email", emailAddress);
//        userDetailsBody.put("displayName", displayName);
//        HttpEntity<Map<String, Object>> userDetailsEntity = new HttpEntity<>(userDetailsBody, null);
//
//        log.debug("Updating Bitbucket user {} ({})", username, emailAddress);
//
//        try {
//            restTemplate.exchange(userDetailsBuilder.build().encode().toUri(), HttpMethod.PUT, userDetailsEntity, Void.class);
//        }
//        catch (HttpClientErrorException e) {
//            if (isUserNotFoundException(e)) {
//                log.warn("Bitbucket user {} does not exist.", username);
//                return;
//            }
//            log.error("Could not update Bitbucket user {}", username, e);
//            throw new BitbucketException("Error while updating user", e);
//        }
    }

    /**
     * Updates the password of a user on Bitbucket
     *
     * @param username The username of the user to update
     * @param password The new password
     * @throws BitbucketException the exception of the bitbucket system when updating the password does not work
     */
    public void updateUserPassword(String username, String password) throws BitbucketException {
//        UriComponentsBuilder passwordBuilder = UriComponentsBuilder.fromHttpUrl(bitbucketServerUrl + "/rest/api/latest/admin/users/credentials");
//        Map<String, Object> passwordBody = new HashMap<>();
//        passwordBody.put("name", username);
//        passwordBody.put("password", password);
//        passwordBody.put("passwordConfirm", password);
//        HttpEntity<Map<String, Object>> passwordEntity = new HttpEntity<>(passwordBody, null);
//
//        log.debug("Updating Bitbucket user password for user {}", username);
//
//        try {
//            restTemplate.exchange(passwordBuilder.build().encode().toUri(), HttpMethod.PUT, passwordEntity, Void.class);
//        }
//        catch (HttpClientErrorException e) {
//            if (isUserNotFoundException(e)) {
//                log.warn("Bitbucket user {} does not exist.", username);
//                return;
//            }
//            log.error("Could not update Bitbucket user password for user {}", username, e);
//            throw new BitbucketException("Error while updating user", e);
//        }
    }

    /**
     * Deletes a user from Bitbucket. It also updates all previous occurrences of
     * the username to a non-identifying username.
     *
     * @param username The user to delete
     */
    public void deleteAndEraseUser(String username) {
//        UriComponentsBuilder deleteBuilder = UriComponentsBuilder.fromHttpUrl(bitbucketServerUrl + "/rest/api/latest/admin/users").queryParam("name", username);
//        UriComponentsBuilder eraseBuilder = UriComponentsBuilder.fromHttpUrl(bitbucketServerUrl + "/rest/api/latest/admin/users/erasure").queryParam("name", username);
//
//        log.debug("Deleting Bitbucket user {}", username);
//        try {
//            restTemplate.exchange(deleteBuilder.build().encode().toUri(), HttpMethod.DELETE, null, Void.class);
//            restTemplate.exchange(eraseBuilder.build().encode().toUri(), HttpMethod.POST, null, Void.class);
//        }
//        catch (HttpClientErrorException e) {
//            if (isUserNotFoundException(e)) {
//                log.warn("Bitbucket user {} has already been deleted.", username);
//                return;
//            }
//            log.error("Could not delete Bitbucket user {}", username, e);
//            throw new BitbucketException("Error while updating user", e);
//        }
    }

    /**
     * Adds a Bitbucket user to (multiple) Bitbucket groups
     *
     * @param username The Bitbucket username
     * @param groups   Names of Bitbucket groups
     * @throws BitbucketException if the rest request to Bitbucket for adding the
     *                            user to the specified groups failed.
     */
    public void addUserToGroups(String username, Set<String> groups) throws BitbucketException {
//        final var body = new BitbucketUserDTO(username, groups);
//        HttpEntity<?> entity = new HttpEntity<>(body, null);
//
//        log.debug("Adding Bitbucket user {} to groups {}", username, groups);
//
//        try {
//            restTemplate.exchange(bitbucketServerUrl + "/rest/api/latest/admin/users/add-groups", HttpMethod.POST, entity, Void.class);
//        }
//        catch (HttpClientErrorException e) {
//            log.error("Could not add Bitbucket user {} to groups {}", username, groups, e);
//            throw new BitbucketException("Error while adding Bitbucket user to groups");
//        }
    }

    /**
     * Removes a Bitbucket user from (multiple) Bitbucket groups
     *
     * @param username The Bitbucket username
     * @param groups   Names of Bitbucket groups
     * @throws BitbucketException if the request to Bitbucket fails
     */
    public void removeUserFromGroups(String username, Set<String> groups) throws BitbucketException {
//        log.debug("Removing Bitbucket user {} from groups {}", username, groups);
//
//        try {
//            for (String group : groups) {
//                Map<String, Object> jsonObject = new HashMap<>();
//                jsonObject.put("context", username);
//                jsonObject.put("itemName", group);
//
//                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(jsonObject, null);
//                UriComponentsBuilder userDetailsBuilder = UriComponentsBuilder.fromHttpUrl(bitbucketServerUrl + "/rest/api/latest/admin/users/remove-group")
//                    .queryParam("context", username).queryParam("itemName", group);
//                restTemplate.exchange(userDetailsBuilder.build().toUri(), HttpMethod.POST, entity, Void.class);
//            }
//        }
//        catch (HttpClientErrorException e) {
//            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
//                log.warn("Could not remove Bitbucket user {} from groups {}. Either the user or the groups were not found or the user is not assigned to a group.", username,
//                    groups);
//                return;
//            }
//            log.error("Could not remove Bitbucket user {} from groups {}", username, groups, e);
//            throw new BitbucketException("Error while removing Bitbucket user from groups");
//        }
    }

    /**
     * Checks if a HTTP exception meets the requirements of a Bitbucket
     * UserNotFoundException
     *
     * @param exception The exception thrown by the HTTP Client
     * @return true if the exception meets the requirements
     */
    private boolean isUserNotFoundException(HttpClientErrorException exception) {
        return HttpStatus.NOT_FOUND.equals(exception.getStatusCode()) && exception.getMessage() != null
            && exception.getMessage().contains("com.atlassian.bitbucket.user.NoSuchUserException");
    }

    /**
     * Gives user write permissions for a repository.
     *
     * @param projectKey     The project key of the repository's project.
     * @param repositorySlug The repository's slug.
     * @param username       The user whom to give write permissions.
     */
    private void giveWritePermission(String projectKey, String repositorySlug, String username) throws BitbucketException {
        // Not implemented.
    }

    @Override
    public void setRepositoryPermissionsToReadOnly(VcsRepositoryUrl repositoryUrl, String projectKey, Set<User> users) throws BitbucketException {
        users.forEach(user -> setStudentRepositoryPermission(repositoryUrl, projectKey, user.getLogin(), VersionControlRepositoryPermission.REPO_READ));
    }

    /**
     * Get the default branch of the repository
     *
     * @param repositoryUrl The repository url to get the default branch for.
     * @return the name of the default branch, e.g. 'main'
     */
    @Override
    public String getDefaultBranchOfRepository(VcsRepositoryUrl repositoryUrl) throws LocalGitException {
        try {
            Map<String, Ref> remoteRepositoryRefs = Git.lsRemoteRepository().setRemote(localGitPath + repositoryUrl.folderNameForRepositoryUrl()).callAsMap();
            if (remoteRepositoryRefs.containsKey("HEAD")) {
                return remoteRepositoryRefs.get("HEAD").getTarget().getName();
            }

            throw new LocalGitException("Cannot get default branch of repository " + repositoryUrl.folderNameForRepositoryUrl() + ". ls-remote does not return a HEAD reference.");
        } catch (Exception e) {
            log.error("Unable to get default branch for repository {}", repositoryUrl.folderNameForRepositoryUrl(), e);
            throw new LocalGitException("Unable to get default branch for repository " + repositoryUrl.folderNameForRepositoryUrl(), e);
        }
    }

    @Override
    public void unprotectBranch(VcsRepositoryUrl repositoryUrl, String branch) throws VersionControlException {
        // Not implemented because it's not needed for local git for the current use
        // case, because the main branch is not protected by default
    }

    /**
     * Set the permission of a student for a repository
     *
     * @param repositoryUrl        The complete repository-url (including protocol,
     *                             host and the complete path)
     * @param projectKey           The project key of the repository's project.
     * @param username             The username of the user whom to assign a
     *                             permission level
     * @param repositoryPermission Repository permission to set for the user (e.g.
     *                             READ_ONLY, WRITE)
     */
    private void setStudentRepositoryPermission(VcsRepositoryUrl repositoryUrl, String projectKey, String username, VersionControlRepositoryPermission repositoryPermission)
        throws LocalGitException {
//        Not implemented.
    }

    /**
     * Remove all permissions of a student for a repository
     *
     * @param repositoryUrl The complete repository-url (including protocol, host
     *                      and the complete path)
     * @param projectKey    The project key of the repository's project.
     * @param username      The username of the user whom to remove access
     */
    private void removeStudentRepositoryAccess(VcsRepositoryUrl repositoryUrl, String projectKey, String username) throws BitbucketException {
//        String repositorySlug = urlService.getRepositorySlugFromRepositoryUrl(repositoryUrl);
//        String baseUrl = bitbucketServerUrl + "/rest/api/latest/projects/" + projectKey + "/repos/" + repositorySlug + "/permissions/users?name="; // NAME
//        String url = baseUrl + username;
//        try {
//            restTemplate.exchange(url, HttpMethod.DELETE, null, Void.class);
//        }
//        catch (Exception e) {
//            log.error("Could not remove repository access using {}", url, e);
//            throw new BitbucketException("Error while removing repository access", e);
//        }
    }

    @Override
    public boolean checkIfProjectExists(String projectKey, String projectName) {
        // Check if the folder already exists in the file system to make sure the new project key is unique.

        try {
            var project = getLocalGitProject(projectKey, projectName);
            log.warn("Local git project with key {} already exists: {}", projectKey, project.name());
            return true;
        }
        catch (LocalGitException e) {
            log.debug("Local git project {} does not exist", projectKey);
            return false;
        }
    }

    /**
     * Create a new project
     *
     * @param programmingExercise the programming exercise for which the local git
     *                            Project should be created
     * @throws LocalGitException if the project could not be created
     */
    @Override
    public void createProjectForExercise(ProgrammingExercise programmingExercise) throws LocalGitException {
        String projectKey = programmingExercise.getProjectKey();
        String projectName = programmingExercise.getProjectName();
        // String folderName = (projectKey + projectName).replaceAll("\\s", "");

        log.debug("Creating folder for local git project with key {}", projectKey);

        try {
            // Instead of defining a project like would be done for GitLab or Bitbucket,
            // just define a directory that will contain all repositories.
            // Ich probiere es erstmal hiermit und habe die Dateien so lokal bei mir und
            // kann sie dort anschauen.
            // Langfristig wird es wahrscheinlich eher sowas wie das hier:
            // https://spring.io/guides/gs/uploading-files/
            // Nachschauen wie langfristig die Dateien damit gespeichert sind!
            File localPath = new File(localGitPath + File.separator + projectKey);

            if(!localPath.mkdirs()) {
                throw new IOException("Could not create directory " + localPath);
            }
        } catch (Exception e) {
            log.error("Could not create local git project {} with key {}", projectName, projectKey, e);
            throw new LocalGitException("Error while creating local git project.");
        }
    }

    @Override
    public ConnectorHealth health() {
        ConnectorHealth health = null;
//        try {
//            final var status = shortTimeoutRestTemplate.getForObject(bitbucketServerUrl + "/status", JsonNode.class);
//            health = status.get("state").asText().equals("RUNNING") ? new ConnectorHealth(true) : new ConnectorHealth(false);
//        }
//        catch (Exception emAll) {
//            health = new ConnectorHealth(emAll);
//        }
//
//        health.setAdditionalInfo(Map.of("url", bitbucketServerUrl));
        return health;
    }

    /**
     * Create a new repo
     *
     * @param repoName   The name for the new repository
     * @param projectKey The project key of the parent project
     * @throws LocalGitException if the repo could not be created
     */
    private void createRepository(String projectKey, String repoName) throws LocalGitException {
        log.debug("Creating local git repo {} with parent key {}", repoName, projectKey);

        try {
            File remoteDir = new File(localGitPath + "/" + projectKey + "/" + repoName + ".git");

            if(!remoteDir.mkdirs()) {
                throw new IOException("Could not create directory " + remoteDir);
            }

            // Create a bare local repository with JGit
            Git git = Git.init().setDirectory(remoteDir).setBare(true).call();
            Repository repository = git.getRepository();
            RefUpdate refUpdate = repository.getRefDatabase().newUpdate(Constants.HEAD, false);
            refUpdate.setForceUpdate(true);
            refUpdate.link("refs/heads/" + defaultBranch);
            git.close();
        }
        catch (GitAPIException | IOException e) {
            log.error("Could not create local git repo {} with project key {}", repoName, projectKey, e);
            throw new LocalGitException("Error while creating local git project.");
        }
    }

    /**
     * Grants a permission to a group for a given project
     *
     * @param projectKey The project requested
     * @param groupName  The group
     * @param permission Set to null if permissions are revoked
     */

    public void grantGroupPermissionToProject(String projectKey, String groupName, BitbucketPermission permission) {
//        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(bitbucketServerUrl + "/rest/api/latest/projects/" + projectKey + "/permissions/groups").queryParam("name",
//            groupName);
//
//        if (permission != null) {
//            builder.queryParam("permission", permission);
//        }
//        try {
//            restTemplate.exchange(builder.build().toUri(), permission != null ? HttpMethod.PUT : HttpMethod.DELETE, null, Void.class);
//        }
//        catch (Exception e) {
//            log.error("Could not give project permission", e);
//            throw new BitbucketException("Error while giving project permissions", e);
//        }
    }

    @Override
    public Boolean repositoryUrlIsValid(@Nullable VcsRepositoryUrl repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.getURI() == null) {
            return false;
        }
        String projectKey;
        String repositorySlug;
        try {
            projectKey = urlService.getProjectKeyFromRepositoryUrl(repositoryUrl);
            repositorySlug = urlService.getRepositorySlugFromRepositoryUrl(repositoryUrl);
        }
        catch (LocalGitException e) {
            // Either the project Key or the repository slug could not be extracted,
            // therefore this can't be a valid URL
            return false;
        }

        try {
            //restTemplate.exchange(bitbucketServerUrl + "/rest/api/latest/projects/" + projectKey + "/repos/" + repositorySlug, HttpMethod.GET, null, Void.class);
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
        // {"eventKey":"...","date":"...","actor":{...},"repository":{...},"changes":[{"ref":{...},"refId":"refs/heads/main",
        // "fromHash":"5626436a443eb898a5c5f74b6352f26ea2b7c84e","toHash":"662868d5e16406d1dd4dcfa8ac6c46ee3d677924","type":"UPDATE"}]}
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

    @Override
    public ZonedDateTime getPushDate(ProgrammingExerciseParticipation participation, String commitHash, Object eventObject) throws LocalGitException {
        // If the event object is supplied we try to retrieve the push date from there
        // to save one call
        // if (eventObject != null) {
            JsonNode node = new ObjectMapper().convertValue(eventObject, JsonNode.class);
            String dateString = node.get("date").asText(null);
           //  if (dateString != null) {
                try {
                    return ZonedDateTime.parse(dateString, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ"));
                }
                catch (DateTimeParseException e) {
                    throw new LocalGitException("Unable to get the push date from participation.");
                }
            // }
        // }

//        boolean isLastPage = false;
//        final int perPage = 40;
//        int start = 0;
//        while (!isLastPage) {
//            try {
//                UriComponents builder = UriComponentsBuilder.fromUri(bitbucketServerUrl.toURI())
//                    .pathSegment("rest", "api", "latest", "projects", participation.getProgrammingExercise().getProjectKey(), "repos",
//                        urlService.getRepositorySlugFromRepositoryUrl(participation.getVcsRepositoryUrl()), "ref-change-activities")
//                    .queryParam("start", start).queryParam("limit", perPage).queryParam("ref", "refs/heads/" + defaultBranch).build();
//                final var response = restTemplate.exchange(builder.toUri(), HttpMethod.GET, null, BitbucketChangeActivitiesDTO.class);
//                if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
//                    throw new BitbucketException("Unable to get push date for participation " + participation.getId() + "\n" + response.getBody());
//                }
//                final var changeActivities = response.getBody().values();
//
//                final var activityOfPush = changeActivities.stream().filter(activity -> commitHash.equals(activity.refChange().toHash())).findFirst();
//                if (activityOfPush.isPresent()) {
//                    return Instant.ofEpochMilli(activityOfPush.get().createdDate()).atZone(ZoneOffset.UTC);
//                }
//                isLastPage = response.getBody().isLastPage();
//                start += perPage;
//            }
//            catch (URISyntaxException e) {
//                throw new BitbucketException("Unable to get push date for participation " + participation.getId(), e);
//            }
//        }
//        throw new BitbucketException("Unable to find push date result for participation " + participation.getId() + " and hash " + commitHash);
    }

    @Nullable
    private JsonNode fetchCommitInfo(JsonNode commitData, String hash) {
        try {
            var cloneLinks = commitData.get("repository").get("links").get("clone");
            VcsRepositoryUrl repositoryURL;
            // it might be the case that cloneLinks contains two URLs and the first one is
            // 'ssh'. Then we are interested in http
            // we use contains here, because it could be the case that https is used here as
            // well in the future.
            // It should not be possible that the cloneLinks array is empty.
            if (cloneLinks.size() > 1 && !cloneLinks.get(0).get("name").asText().contains("http")) {
                repositoryURL = new VcsRepositoryUrl(cloneLinks.get(1).get("href").asText());
            }
            else {
                repositoryURL = new VcsRepositoryUrl(cloneLinks.get(0).get("href").asText());
            }
            final var projectKey = urlService.getProjectKeyFromRepositoryUrl(repositoryURL);
            final var slug = urlService.getRepositorySlugFromRepositoryUrl(repositoryURL);
            //final var uriBuilder = UriComponentsBuilder.fromUri(bitbucketServerUrl.toURI())
             //   .pathSegment("rest", "api", "1.0", "projects", projectKey, "repos", slug, "commits", hash).build();
            final JsonNode commitInfo = null; //restTemplate.exchange(uriBuilder.toUri(), HttpMethod.GET, null, JsonNode.class).getBody();
            if (commitInfo == null) {
                throw new LocalGitException("Unable to fetch commit info from local git for hash " + hash);
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

    public final class LocalGitRepositoryUrl extends VcsRepositoryUrl {

        public LocalGitRepositoryUrl(String projectKey, String repositorySlug) {
            final var urlString = localGitServerUrl + buildRepositoryPath(projectKey, repositorySlug);
            try {
                this.uri = new URI(urlString);
            }
            catch (URISyntaxException e) {
                throw new LocalGitException("Could not create local git Repository URL", e);
            }
        }

        private LocalGitRepositoryUrl(String urlString) {
            try {
                this.uri = new URI(urlString);
            }
            catch (URISyntaxException e) {
                throw new LocalGitException("Could not create local git Repository URL", e);
            }
        }

        /*
         * @Override public VcsRepositoryUrl withUser(String username) { this.username = username; return new BitbucketRepositoryUrl(uri.toString().replaceAll("(https?://)(.*)",
         * "$1" + username + "@$2")); }
         */

        private String buildRepositoryPath(String projectKey, String repositorySlug) {
            return "/" + projectKey + "/" + repositorySlug;
        }
    }
}
