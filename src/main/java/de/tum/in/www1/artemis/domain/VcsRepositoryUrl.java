package de.tum.in.www1.artemis.domain;

import java.net.MalformedURLException;
import java.net.URL;

public abstract class VcsRepositoryUrl {

    protected String username;

    protected URL url;

    protected VcsRepositoryUrl() {
    }

    public VcsRepositoryUrl(String spec) throws MalformedURLException {
        this.url = new URL(spec);
    }

    public abstract VcsRepositoryUrl withUser(final String username);

    public URL getURL() {
        return this.url;
    }

    @Override
    public String toString() {
        return this.url.toString();
    }
}
