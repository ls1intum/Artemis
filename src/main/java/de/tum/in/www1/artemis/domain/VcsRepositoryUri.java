package de.tum.in.www1.artemis.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

public class VcsRepositoryUri {

    protected String username;

    protected URI uri;

    protected VcsRepositoryUri() {
        // NOTE: this constructor should not be used and only exists to prevent compile errors
    }

    // Create the url from a uriSpecString, e.g. https://username@artemistest2gitlab.ase.in.tum.de/FTCSCAGRADING1/ftcscagrading1-username
    public VcsRepositoryUri(String uriSpecString) throws URISyntaxException {
        this.uri = new URI(uriSpecString);
    }

    // Create the url from a file reference, e.g. C:/Users/Admin/AppData/Local/Temp/studentOriginRepo1644180397872264950
    public VcsRepositoryUri(java.io.File file) {
        this.uri = file.toURI();
    }

    public VcsRepositoryUri withUser(final String username) {
        this.username = username;
        return this;
    }

    public URI getURI() {
        return this.uri;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        // we explicitly allow subclasses (i.e. obj is a subclass of this) here (to avoid issues when comparing subclasses with the same url)
        // Note that this also includes the null check
        if (!(obj instanceof VcsRepositoryUri that)) {
            return false;
        }
        return Objects.equals(username, that.username) && Objects.equals(uri, that.uri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, uri);
    }

    @Override
    public String toString() {
        if (this.uri != null) {
            return this.uri.toString();
        }
        else {
            return "VcsRepositoryUri: empty";
        }
    }

    /**
     * Generates the unique local folder name for a given file or remote repository URI.
     * For file URIs, we take the last element of the path, which should be unique
     * For URLs pointing to remote git repositories, we use the whole path
     * <p>
     * Examples:
     * https://username@artemistest2gitlab.ase.in.tum.de/FTCSCAGRADING1/ftcscagrading1-username --> FTCSCAGRADING1/ftcscagrading1-username
     * file:C:/Users/Admin/AppData/Local/Temp/studentOriginRepo1644180397872264950 --> studentOriginRepo1644180397872264950
     * file:/var/folders/vc/sk85td_s54v7w9tjq07b0_q80000gn/T/studentTeamOriginRepo420037178325056205/ --> studentTeamOriginRepo420037178325056205
     *
     * @return the folderName as a string.
     */
    public String folderNameForRepositoryUri() {
        if ("file".equals(uri.getScheme())) {
            // Take the last element of the path
            final var segments = uri.getPath().split("/");
            return segments[segments.length - 1];
        }
        else { // e.g. http(s) or ssh
            String path = getURI().getPath();
            // remove .git (which might be used at the end)
            path = path.replaceAll("\\.git$", "");
            path = path.replaceAll("/$", "");
            path = path.replaceAll("^/.*scm", "");
            path = path.replaceAll("^.*?/git/", "/");
            return path;
        }
    }

    /**
     * Retrieves the repository name without the project key and the optional practice prefix from the URI.
     * <p>
     * Examples:
     * http://localhost:8080/git/GREAT/great-artemis_admin.git --> artemis_admin
     * http://localhost:8080/git/GREAT/great-practice-artemis_admin.git --> artemis_admin
     *
     * @return the repository name without the project key and the optional practice prefix.
     */
    public String repositoryNameWithoutProjectKey() {
        return repositorySlug().toLowerCase().replace(projectKey().toLowerCase() + "-", "").replace("practice-", "");
    }

    public String repositorySlug() {
        return this.uri.getPath().substring(this.uri.getPath().lastIndexOf('/') + 1).replace(".git", "");
    }

    private String projectKey() {
        return this.uri.getPath().substring(this.uri.getPath().lastIndexOf('/', this.uri.getPath().lastIndexOf('/') - 1) + 1, this.uri.getPath().lastIndexOf('/')).toLowerCase();
    }
}
