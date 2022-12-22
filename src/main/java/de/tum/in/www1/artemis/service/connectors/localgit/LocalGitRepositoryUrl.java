package de.tum.in.www1.artemis.service.connectors.localgit;

import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;

public class LocalGitRepositoryUrl extends VcsRepositoryUrl {

    private String projectKey;

    private String courseShortName;

    private String repositorySlug;

    // Part of the repositorySlug, identifies the repository's type (exercise, solution, tests) or its owner (e.g. "artemis_test_user_1").
    private String repositoryTypeOrUserName;

    public LocalGitRepositoryUrl(String projectKey, String courseShortName, String repositorySlug) {
        this.projectKey = projectKey;
        this.courseShortName = courseShortName;
        this.repositorySlug = repositorySlug;
        this.repositoryTypeOrUserName = repositorySlug.toLowerCase().replace(projectKey.toLowerCase() + "-", "");
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
