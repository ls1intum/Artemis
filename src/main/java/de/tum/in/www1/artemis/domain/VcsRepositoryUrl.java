package de.tum.in.www1.artemis.domain;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;

public class VcsRepositoryUrl {

    protected String username;

    protected URL url;

    protected VcsRepositoryUrl() {
        // NOTE: this constructor should not be used and only exists to prevent compile errors
    }

    public VcsRepositoryUrl(String spec) throws MalformedURLException {
        this.url = new URL(spec);
    }

    public VcsRepositoryUrl withUser(final String username) {
        this.username = username;
        return this;
    }

    public URL getURL() {
        return this.url;
    }

    public URI getSshUri() throws URISyntaxException {
        // ssh://git@bitbucket.ase.in.tum.de:7999/pgdp2021w07h02/pgdp2021w07h02-ga27yox.git
        // TODO: use ssh-template-clone-url from yml file
        var uri = url.toURI();
        var newUri = new URI("ssh", "git", uri.getHost(), 7999, uri.getPath().replace("/scm", ""), null, uri.getFragment());
        System.out.println("ssh uri: " + newUri);
        return newUri;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        // we explicitly allow subclasses (i.e. o is a subclass of this) here (to avoid issues when comparing sub classes with the same url)
        // Note that this also includes the null check
        if (!(o instanceof VcsRepositoryUrl)) {
            return false;
        }
        VcsRepositoryUrl that = (VcsRepositoryUrl) o;
        return Objects.equals(username, that.username) && Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, url);
    }

    @Override
    public String toString() {
        if (this.url != null) {
            return this.url.toString();
        }
        else {
            return "VcsRepositoryUrl: empty";
        }
    }
}
