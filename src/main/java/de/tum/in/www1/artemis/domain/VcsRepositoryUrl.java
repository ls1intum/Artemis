package de.tum.in.www1.artemis.domain;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class VcsRepositoryUrl {

    protected String username;

    protected URL url;

    protected VcsRepositoryUrl() {
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
    public String toString() {
        return this.url.toString();
    }
}
