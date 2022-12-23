package de.tum.in.www1.artemis.service.connectors.localvc;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.exception.LocalVCException;
import de.tum.in.www1.artemis.web.rest.util.StringUtil;

public class LocalVCRepositoryUrl extends VcsRepositoryUrl {

    private String localVCUrl;

    private String projectKey;

    private String courseShortName;

    private String repositorySlug;

    // Part of the repositorySlug, identifies the repository's type (exercise, solution, tests) or its owner (e.g. "artemis_test_user_1").
    private String repositoryTypeOrUserName;

    public LocalVCRepositoryUrl(URL localVCServerUrl, String projectKey, String courseShortName, String repositorySlug) {
        this.projectKey = StringUtil.stripIllegalCharacters(projectKey);
        this.courseShortName = StringUtil.stripIllegalCharacters(courseShortName);
        this.repositorySlug = StringUtil.stripIllegalCharacters(repositorySlug);
        this.repositoryTypeOrUserName = this.repositorySlug.toLowerCase().replace(this.projectKey.toLowerCase() + "-", "");
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

    public String getLocalPath(String localVCPath) {
        return localVCPath + File.separator + courseShortName + File.separator + projectKey + File.separator + repositorySlug + ".git";
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
