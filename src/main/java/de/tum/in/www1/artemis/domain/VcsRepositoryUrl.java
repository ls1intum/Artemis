package de.tum.in.www1.artemis.domain;

import java.net.MalformedURLException;
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
