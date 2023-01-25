package de.tum.in.www1.artemis.service.connectors.localvc;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.exception.LocalVCException;
import de.tum.in.www1.artemis.web.rest.util.StringUtil;

public class LocalVCRepositoryUrl extends VcsRepositoryUrl {

    final private String projectKey;

    final private String courseShortName;

    final private String repositorySlug;

    // Part of the repositorySlug, identifies the repository's type (exercise, solution, tests) or its owner (e.g. "artemis_test_user_1").
    final private String repositoryTypeOrUserName;

    final private boolean isTestRunRepository;

    public LocalVCRepositoryUrl(URL localVCServerUrl, String projectKey, String courseShortName, String repositorySlug) {
        this.projectKey = StringUtil.stripIllegalCharacters(projectKey);
        this.courseShortName = StringUtil.stripIllegalCharacters(courseShortName);
        this.repositorySlug = StringUtil.stripIllegalCharacters(repositorySlug);
        this.repositoryTypeOrUserName = this.repositorySlug.toLowerCase().replace(this.projectKey.toLowerCase() + "-", "");
        this.isTestRunRepository = this.repositorySlug.contains("-practice-");
        final String url = localVCServerUrl.getProtocol() + "://" + localVCServerUrl.getAuthority() + buildRepositoryPath();
        try {
            this.uri = new URI(url);
        }
        catch (URISyntaxException e) {
            throw new LocalVCException("Could not create Local Git Repository URL.", e);
        }
    }

    private String buildRepositoryPath() {
        return "/git/" + courseShortName + "/" + projectKey + "/" + repositorySlug + ".git";
    }

    public LocalVCRepositoryUrl(URL localVCServerUrl, String urlString) throws LocalVCException {

        if (!urlString.startsWith(localVCServerUrl.toString())) {
            throw new LocalVCException("Url does not start with the current server Url");
        }

        Path urlPath = Paths.get(urlString.replaceFirst(localVCServerUrl.toString(), ""));

        if (!urlPath.getName(0).toString().equals("git") || !urlPath.getName(3).toString().endsWith(".git")) {
            throw new LocalVCException("Invalid URL.");
        }

        projectKey = urlPath.getName(2).toString();
        courseShortName = urlPath.getName(1).toString();
        repositorySlug = urlPath.getName(3).toString().replace(".git", "");
        String repositoryTypeOrUserNameWithPracticePrefix = repositorySlug.toLowerCase().replace(projectKey.toLowerCase() + "-", "");
        isTestRunRepository = repositoryTypeOrUserNameWithPracticePrefix.startsWith("practice-");
        repositoryTypeOrUserName = repositoryTypeOrUserNameWithPracticePrefix.replace("practice-", "");

        // Project key should contain the course short name.
        if (!projectKey.toLowerCase().contains(courseShortName.toLowerCase())) {
            throw new LocalVCException("Badly formed Local Git URI: " + urlString + " Expected the repository name to start with the lower case course short name.");
        }

        try {
            this.uri = new URI(urlString);
        }
        catch (URISyntaxException e) {
            throw new LocalVCException("Could not create local VC Repository URL", e);
        }
    }

    public LocalVCRepositoryUrl(String localVCPath, Path repositoryFolderPath) {

        if (!repositoryFolderPath.getName(0).toString().equals(localVCPath)) {
            throw new LocalVCException("Repository folder path does not start with the current local VC path");
        }

        projectKey = repositoryFolderPath.getName(2).toString();
        courseShortName = repositoryFolderPath.getName(1).toString();
        repositorySlug = repositoryFolderPath.getName(3).toString().replace(".git", "");
        String repositoryTypeOrUserNameWithPracticePrefix = repositorySlug.toLowerCase().replace(projectKey.toLowerCase() + "-", "");
        isTestRunRepository = repositoryTypeOrUserNameWithPracticePrefix.startsWith("practice-");
        repositoryTypeOrUserName = repositoryTypeOrUserNameWithPracticePrefix.replace("practice-", "");

        // Project key should contain the course short name.
        if (!projectKey.toLowerCase().contains(courseShortName.toLowerCase())) {
            throw new LocalVCException("Badly formed Local Git Path: " + repositoryFolderPath + " Expected the repository name to start with the lower case course short name.");
        }
    }

    public Path getLocalPath(String localVCPath) {
        return Paths.get(localVCPath, courseShortName, projectKey, repositorySlug + ".git");
    }

    public String getProjectKey() {
        return projectKey;
    }

    public String getCourseShortName() {
        return courseShortName;
    }

    public String getRepositorySlug() {
        return repositorySlug;
    }

    public String getRepositoryTypeOrUserName() {
        return repositoryTypeOrUserName;
    }

    public boolean isTestRunRepository() {
        return isTestRunRepository;
    }
}
