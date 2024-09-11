package de.tum.cit.aet.artemis.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * Represents a Version Control System (VCS) repository URI with capabilities to manipulate and extract information from it.
 * This class supports handling both local file references and remote repository URIs.
 */
public class VcsRepositoryUri {

    /** The username associated with the VCS repository URI, if applicable. */
    protected String username;

    /** The URI representing either a local file path or a remote repository URL. */
    protected URI uri;

    /**
     * Default constructor, intended for internal use only to prevent compile errors.
     * This constructor is protected to discourage its use as it creates an uninitialized object.
     */
    protected VcsRepositoryUri() {
    }

    /**
     * Initializes a new instance of the {@link VcsRepositoryUri} class based on a specified URI string.
     * The URI string should represent a complete URI to a VCS repository, e.g. https://username@artemistest2gitlab.ase.in.tum.de/FTCSCAGRADING1/ftcscagrading1-username
     *
     * @param uriSpecString The URI string to be parsed into a URI.
     * @throws URISyntaxException If the provided string does not form a valid URI.
     */
    public VcsRepositoryUri(String uriSpecString) throws URISyntaxException {
        this.uri = new URI(uriSpecString);
    }

    /**
     * Initializes a new instance of the {@link VcsRepositoryUri} class from a file reference, e.g. C:/Users/Admin/AppData/Local/Temp/studentOriginRepo1644180397872264950
     * The file's URI is extracted and stored.
     *
     * @param file The file from which the URI will be created.
     */
    public VcsRepositoryUri(java.io.File file) {
        this.uri = file.toURI();
    }

    /**
     * Associates a username with the current VCS repository URI and returns the instance for chaining.
     *
     * @param username The username to be associated with the URI.
     * @return The instance of {@link VcsRepositoryUri} with the username set.
     */
    public VcsRepositoryUri withUser(final String username) {
        this.username = username;
        return this;
    }

    /**
     * Returns the URI associated with this VCS repository.
     *
     * @return The URI of the VCS repository.
     */
    public URI getURI() {
        return this.uri;
    }

    /**
     * Compares this instance with another object for equality, considering both the username and the URI.
     * This method also supports comparisons with subclasses of {@link VcsRepositoryUri}.
     *
     * @param obj The object to compare with this instance.
     * @return true if the provided object is equivalent to this instance; false otherwise.
     */
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
     * Generates a folder name from the URI to uniquely represent the repository locally.
     * It takes the last path segment of file URIs and the full path for remote repository URIs, excluding any .git suffix.
     * For file URIs, we take the last element of the path, which should be unique
     * For URLs pointing to remote git repositories, we use the whole path
     * <p>
     * Examples:
     * https://username@artemistest2gitlab.ase.in.tum.de/FTCSCAGRADING1/ftcscagrading1-username --> FTCSCAGRADING1/ftcscagrading1-username
     * file:C:/Users/Admin/AppData/Local/Temp/studentOriginRepo1644180397872264950 --> studentOriginRepo1644180397872264950
     * file:/var/folders/vc/sk85td_s54v7w9tjq07b0_q80000gn/T/studentTeamOriginRepo420037178325056205/ --> studentTeamOriginRepo420037178325056205
     *
     * @return A string representing the folder name derived from the URI.
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
     * Extracts and returns the repository name from the URI, removing any leading project key and optional "practice" prefix.
     * This method is useful for isolating the pure repository name in contexts where the higher-level organizational
     * identifiers (like project keys) are not needed.
     * <p>
     * The method performs the following transformations:
     * <ul>
     * <li>Converts the full repository URI path into its base name with the ".git" suffix removed.</li>
     * <li>Strips out the leading project key followed by a dash ("-").</li>
     * <li>Removes any "practice-" prefix used for practice repositories.</li>
     * </ul>
     * <p>
     * Examples:
     * <ul>
     * <li>http://localhost:8080/git/GREAT/great-artemis_admin.git --> artemis_admin</li>
     * <li>http://localhost:8080/git/GREAT/great-practice-artemis_admin.git --> artemis_admin</li>
     * </ul>
     *
     * @return The repository name without the project key and the practice prefix, in lowercase.
     */
    public String repositoryNameWithoutProjectKey() {
        return repositorySlug().toLowerCase().replace(projectKey().toLowerCase() + "-", "").replace("practice-", "");
    }

    /**
     * Retrieves the last part of the URI path, typically representing the repository slug or name.
     * This slug usually corresponds to the actual name of the repository in the version control system, without any path
     * prefixes or the ".git" file extension. This method is often used as a helper for other methods that need to process
     * or display the repository name.
     * <p>
     * Example:
     * <ul>
     * <li>From the URI "http://localhost:8080/git/GREAT/great-artemis_admin.git", it extracts "great-artemis_admin".</li>
     * </ul>
     *
     * @return The repository slug derived from the URI's path.
     */
    public String repositorySlug() {
        return this.uri.getPath().substring(this.uri.getPath().lastIndexOf('/') + 1).replace(".git", "");
    }

    /**
     * Extracts the project key from a VCS repository URI. The project key is assumed to be the second-to-last
     * segment of the path in the URI, which typically identifies the organizational or project-level identifier under
     * which the repository is categorized in the VCS.
     * <p>
     * This method is particularly useful when handling URIs where repository access is structured by project keys.
     * <p>
     * Example:
     * <ul>
     * <li>From the URI "http://localhost:8080/git/GREAT/great-artemis_admin.git", it extracts "GREAT".</li>
     * </ul>
     *
     * @return The project key in lowercase, as found in the URI's path.
     */
    private String projectKey() {
        return this.uri.getPath().substring(this.uri.getPath().lastIndexOf('/', this.uri.getPath().lastIndexOf('/') - 1) + 1, this.uri.getPath().lastIndexOf('/')).toLowerCase();
    }
}
