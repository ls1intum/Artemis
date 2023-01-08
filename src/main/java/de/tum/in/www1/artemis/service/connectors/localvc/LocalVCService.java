package de.tum.in.www1.artemis.service.connectors.localvc;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exception.LocalVCException;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.UrlService;
import de.tum.in.www1.artemis.service.connectors.AbstractVersionControlService;
import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.web.rest.util.StringUtil;

@Service
@Profile("localvc")
public class LocalVCService extends AbstractVersionControlService {

    private final Logger log = LoggerFactory.getLogger(LocalVCService.class);

    @Value("${artemis.version-control.url}")
    private URL localVCServerUrl;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private String localVCPath;

    @Value("${artemis.git.name}")
    private String artemisGitName;

    private final RestTemplate restTemplate;

    private final RestTemplate shortTimeoutRestTemplate;

    public LocalVCService(@Qualifier("localVCRestTemplate") RestTemplate restTemplate, UrlService urlService,
            @Qualifier("shortTimeoutLocalVCRestTemplate") RestTemplate shortTimeoutRestTemplate, GitService gitService, ApplicationContext applicationContext,
            ProgrammingExerciseStudentParticipationRepository studentParticipationRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            Environment environment) {
        super(applicationContext, gitService, urlService, studentParticipationRepository, programmingExerciseRepository, environment);
        this.restTemplate = restTemplate;
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
    }

    @Override
    public void configureRepository(ProgrammingExercise exercise, ProgrammingExerciseStudentParticipation participation, boolean allowAccess) {
        // For Bitbucket and GitLab, users are added to the respective repository to allow them to fetch from there and push to it
        // if the exercise allows for usage of an offline IDE.
        // For local VCS, users are allowed to access the repository by default if they have access to the repository URL.
        // Instead, the LocalVCFetchFilter and LocalVCPushFilter block requests if offline IDE usage is not allowed.
    }

    @Override
    public void addMemberToRepository(VcsRepositoryUrl repositoryUrl, User user) {
        // Members cannot be added to a local repository. Authenticated users have access by default and are authorized in the LocalVCFetchFilter and LocalVCPushFilter.
    }

    @Override
    public void removeMemberFromRepository(VcsRepositoryUrl repositoryUrl, User user) {
        // Members cannot be removed from a local repository.
        // Authorization is checked in the LocalVCFetchFilter and LocalVCPushFilter.
    }

    @Override
    protected void addWebHook(VcsRepositoryUrl repositoryUrl, String notificationUrl, String webHookName) {
        // Webhooks must not be added for the local git server. The LocalVCPushFilter notifies Artemis on every push.
    }

    @Override
    protected void addAuthenticatedWebHook(VcsRepositoryUrl repositoryUrl, String notificationUrl, String webHookName, String secretToken) {
        // Not needed for local VC.
        throw new UnsupportedOperationException("Authenticated webhooks with local VC are not supported!");
    }

    @Override
    public void deleteProject(String courseShortName, String projectKey) {
        try {
            String folderName = localVCPath + File.separator + courseShortName + File.separator + projectKey;
            FileUtils.deleteDirectory(new File(folderName));
        }
        catch (IOException e) {
            log.error("Could not delete project", e);
        }
    }

    @Override
    public void deleteRepository(VcsRepositoryUrl repositoryUrl) {
        try {
            LocalVCRepositoryUrl localVCRepositoryUrl = new LocalVCRepositoryUrl(localVCServerUrl, repositoryUrl.toString());
            Path localPath = localVCRepositoryUrl.getLocalPath(localVCPath);
            FileUtils.deleteDirectory(localPath.toFile());
        }
        catch (IOException e) {
            log.error("Could not delete repository", e);
        }
    }

    @Override
    public VcsRepositoryUrl getCloneRepositoryUrl(String projectKey, String courseShortName, String repositorySlug) {
        return new LocalVCRepositoryUrl(localVCServerUrl, projectKey, courseShortName, repositorySlug);
    }

    @Override
    public void setRepositoryPermissionsToReadOnly(VcsRepositoryUrl repositoryUrl, String projectKey, Set<User> users) throws LocalVCException {
        users.forEach(user -> setStudentRepositoryPermissionToReadOnly(repositoryUrl, projectKey, user.getLogin()));
    }

    /**
     * Set the permission of a student for a repository to read-only.
     *
     * @param repositoryUrl        The complete repository-url (including protocol,
     *                             host and the complete path)
     * @param projectKey           The project key of the repository's project.
     * @param username             The username of the user whom to assign a
     *                             permission level
     */
    private void setStudentRepositoryPermissionToReadOnly(VcsRepositoryUrl repositoryUrl, String projectKey, String username) throws LocalVCException {
        // VersionControlRepositoryPermission.REPO_READ
        // TODO: Probably create a new db table "participation_student_permission" which contains whether each student for one participation has read and/or write privileges.
    }

    /**
     * Get the default branch of the repository
     *
     * @param repositoryUrl The repository url to get the default branch for.
     * @return the name of the default branch, e.g. 'main'
     */
    @Override
    public String getDefaultBranchOfRepository(VcsRepositoryUrl repositoryUrl) throws LocalVCException {
        try {
            LocalVCRepositoryUrl localVCRepositoryUrl = new LocalVCRepositoryUrl(localVCServerUrl, repositoryUrl.toString());
            String localRepositoryPath = localVCRepositoryUrl.getLocalPath(localVCPath).toString();
            Map<String, Ref> remoteRepositoryRefs = Git.lsRemoteRepository().setRemote(localRepositoryPath).callAsMap();
            if (remoteRepositoryRefs.containsKey("HEAD")) {
                return remoteRepositoryRefs.get("HEAD").getTarget().getName();
            }

            throw new LocalVCException("Cannot get default branch of repository " + repositoryUrl.folderNameForRepositoryUrl() + ". ls-remote does not return a HEAD reference.");
        }
        catch (Exception e) {
            log.error("Unable to get default branch for repository {}", repositoryUrl.folderNameForRepositoryUrl(), e);
            throw new LocalVCException("Unable to get default branch for repository " + repositoryUrl.folderNameForRepositoryUrl(), e);
        }
    }

    @Override
    public void unprotectBranch(VcsRepositoryUrl repositoryUrl, String branch) throws VersionControlException {
        // Not implemented. It is not needed for local VC for the current use
        // case, because the main branch is unprotected by default
    }

    /**
     * Check if a project already exists in the file system to make sure the new projectKey is unique.
     *
     * @param projectKey  to check if a project with this unique key already exists
     * @param projectName to check if a project with the same name already exists
     * @return true or false depending
     */
    @Override
    public boolean checkIfProjectExists(String projectKey, String courseShortName, String projectName) {
        String projectKeyStripped = StringUtil.stripIllegalCharacters(projectKey);
        String courseShortNameStripped = StringUtil.stripIllegalCharacters(courseShortName);

        // Try to find the folder in the file system. If it is not found, return false.
        if (new File(localVCPath + File.separator + courseShortNameStripped + File.separator + projectKeyStripped).exists()) {
            log.warn("Local git project with key {} already exists: {}", projectKey, projectName);
            return true;
        }

        log.debug("Local git project {} does not exist", projectKey);
        return false;
    }

    /**
     * Create a new project
     *
     * @param programmingExercise the programming exercise for which the local git
     *                            Project should be created
     * @throws LocalVCException if the project could not be created
     */
    @Override
    public void createProjectForExercise(ProgrammingExercise programmingExercise) throws LocalVCException {
        String localVCPathResolved = localVCPath;
        String courseShortName = StringUtil.stripIllegalCharacters(programmingExercise.getCourseViaExerciseGroupOrCourseMember().getShortName());
        String projectKey = StringUtil.stripIllegalCharacters(programmingExercise.getProjectKey());

        log.debug("Creating folder for local git project at {}", localVCPathResolved + File.separator + courseShortName + File.separator + projectKey);

        try {
            // Instead of defining a project like would be done for GitLab or Bitbucket,
            // just define a directory that will contain all repositories.
            File localPath = new File(localVCPathResolved + File.separator + courseShortName + File.separator + projectKey);

            if (!localPath.mkdirs()) {
                throw new IOException("Could not create directory " + localPath.getPath());
            }
        }
        catch (Exception e) {
            log.error("Could not create local git project for key {} in course {}", projectKey, courseShortName, e);
            throw new LocalVCException("Error while creating local git project.");
        }
    }

    @Override
    public ConnectorHealth health() {
        ConnectorHealth health = null;

        // TODO: Check if servlet is running.
        health = new ConnectorHealth(true);

        health.setAdditionalInfo(Map.of("url", localVCServerUrl));
        return health;
    }

    @Override
    public void createRepository(String projectKey, String courseShortName, String repositorySlug, String parentProjectKey) {
        createRepository(projectKey, courseShortName, repositorySlug);
    }

    /**
     * Create a new repo
     *
     * @param projectKey      The project key of the parent project
     * @param courseShortName The short name of the course the repository belongs to
     * @param repositorySlug  The name for the new repository
     * @throws LocalVCException if the repo could not be created
     */
    private void createRepository(String projectKey, String courseShortName, String repositorySlug) throws LocalVCException {

        LocalVCRepositoryUrl localVCUrl = new LocalVCRepositoryUrl(localVCServerUrl, projectKey, courseShortName, repositorySlug);

        Path localFilePath = localVCUrl.getLocalPath(localVCPath);

        log.debug("Creating local git repo {} in folder {}", repositorySlug, localFilePath);

        try {
            File remoteDir = localFilePath.toFile();

            if (!remoteDir.mkdirs()) {
                throw new IOException("Could not create directory " + remoteDir.getPath());
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
            log.error("Could not create local git repo {} at location {}", repositorySlug, localFilePath, e);
            throw new LocalVCException("Error while creating local git project.");
        }
    }

    @Override
    public Boolean repositoryUrlIsValid(@Nullable VcsRepositoryUrl repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.getURI() == null) {
            return false;
        }
        try {
            new LocalVCRepositoryUrl(localVCServerUrl, repositoryUrl.toString());
        }
        catch (LocalVCException e) {
            return false;
        }
        return true;
    }

    @Override
    @NotNull
    public Commit getLastCommitDetails(Object requestBody) throws LocalVCException {
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
    public ZonedDateTime getPushDate(ProgrammingExerciseParticipation participation, String commitHash, Object eventObject) throws LocalVCException {
        // If the event object is supplied we try to retrieve the push date from there
        // to save one call
        // if (eventObject != null) {
        JsonNode node = new ObjectMapper().convertValue(eventObject, JsonNode.class);
        String dateString = node.get("date").asText(null);
        // if (dateString != null) {
        try {
            return ZonedDateTime.parse(dateString, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ"));
        }
        catch (DateTimeParseException e) {
            throw new LocalVCException("Unable to get the push date from participation.");
        }
        // }
        // }

        // boolean isLastPage = false;
        // final int perPage = 40;
        // int start = 0;
        // while (!isLastPage) {
        // try {
        // UriComponents builder = UriComponentsBuilder.fromUri(bitbucketServerUrl.toURI())
        // .pathSegment("rest", "api", "latest", "projects", participation.getProgrammingExercise().getProjectKey(), "repos",
        // urlService.getRepositorySlugFromRepositoryUrl(participation.getVcsRepositoryUrl()), "ref-change-activities")
        // .queryParam("start", start).queryParam("limit", perPage).queryParam("ref", "refs/heads/" + defaultBranch).build();
        // final var response = restTemplate.exchange(builder.toUri(), HttpMethod.GET, null, BitbucketChangeActivitiesDTO.class);
        // if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
        // throw new BitbucketException("Unable to get push date for participation " + participation.getId() + "\n" + response.getBody());
        // }
        // final var changeActivities = response.getBody().values();
        //
        // final var activityOfPush = changeActivities.stream().filter(activity -> commitHash.equals(activity.refChange().toHash())).findFirst();
        // if (activityOfPush.isPresent()) {
        // return Instant.ofEpochMilli(activityOfPush.get().createdDate()).atZone(ZoneOffset.UTC);
        // }
        // isLastPage = response.getBody().isLastPage();
        // start += perPage;
        // }
        // catch (URISyntaxException e) {
        // throw new BitbucketException("Unable to get push date for participation " + participation.getId(), e);
        // }
        // }
        // throw new BitbucketException("Unable to find push date result for participation " + participation.getId() + " and hash " + commitHash);
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
            // final var uriBuilder = UriComponentsBuilder.fromUri(bitbucketServerUrl.toURI())
            // .pathSegment("rest", "api", "1.0", "projects", projectKey, "repos", slug, "commits", hash).build();
            final JsonNode commitInfo = null; // restTemplate.exchange(uriBuilder.toUri(), HttpMethod.GET, null, JsonNode.class).getBody();
            if (commitInfo == null) {
                throw new LocalVCException("Unable to fetch commit info from local git for hash " + hash);
            }

            return commitInfo;
        }
        catch (Exception e) {
            log.warn("Cannot fetch commit info for hash {} due to error: {}", hash, e.getMessage());
        }
        return null;
    }

}
