package de.tum.in.www1.artemis.service.connectors.localgit;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.exception.LocalGitException;
import de.tum.in.www1.artemis.web.rest.util.StringUtil;

public class LocalGitRepositoryUrl extends VcsRepositoryUrl {

    private String localGitUrl;

    private String projectKey;

    private String courseShortName;

    private String repositorySlug;

    // Part of the repositorySlug, identifies the repository's type (exercise, solution, tests) or its owner (e.g. "artemis_test_user_1").
    private String repositoryTypeOrUserName;

    public LocalGitRepositoryUrl(URL localGitServerUrl, String projectKey, String courseShortName, String repositorySlug) {
        this.projectKey = StringUtil.stripIllegalCharacters(projectKey);
        this.courseShortName = StringUtil.stripIllegalCharacters(courseShortName);
        this.repositorySlug = StringUtil.stripIllegalCharacters(repositorySlug);
        this.repositoryTypeOrUserName = this.repositorySlug.toLowerCase().replace(this.projectKey.toLowerCase() + "-", "");
        final String url = localGitServerUrl.getProtocol() + "://" + localGitServerUrl.getAuthority() + buildRepositoryPath();
        try {
            this.uri = new URI(url);
        }
        catch (URISyntaxException e) {
            throw new LocalGitException("Could not create Local Git Repository URL.", e);
        }
    }

    private String buildRepositoryPath() {
        return "/git/" + courseShortName + "/" + projectKey + "/" + repositorySlug + ".git";
    }

    public String getLocalPath(String localGitPath) {
        return localGitPath + File.separator + courseShortName + File.separator + projectKey + File.separator + repositorySlug + ".git";
    }

    public String getCourseShortName() {
        return courseShortName;
    }

    public void setCourseShortName(String courseShortName) {
        this.courseShortName = courseShortName;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public String getRepositorySlug() {
        return repositorySlug;
    }

    public void setRepositorySlug(String repositorySlug) {
        this.repositorySlug = repositorySlug;
    }

    public String getRepositoryTypeOrUserName() {
        return repositoryTypeOrUserName;
    }

    public void setRepositoryTypeOrUserName(String repositoryTypeOrUserName) {
        this.repositoryTypeOrUserName = repositoryTypeOrUserName;
    }
}
