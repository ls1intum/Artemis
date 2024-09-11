package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;

import jakarta.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.cit.aet.artemis.domain.VcsRepositoryUri;

@Profile(PROFILE_CORE)
@Service
public abstract class InternalUrlService {

    protected final Optional<URL> internalCiUrl;

    protected final Optional<URL> internalVcsUrl;

    private static final Logger log = LoggerFactory.getLogger(InternalUrlService.class);

    public InternalUrlService(Optional<URL> internalCiUrl, Optional<URL> internalVcsUrl) {
        this.internalCiUrl = internalCiUrl;
        this.internalVcsUrl = internalVcsUrl;
    }

    /**
     * Replaces the url of the vcs repository uri to the internal url if it's
     * defined.
     *
     * @param vcsRepositoryUri the vcs repository uri
     * @return the vcs repository uri with the internal url
     */
    public VcsRepositoryUri toInternalVcsUrl(VcsRepositoryUri vcsRepositoryUri) {
        if (vcsRepositoryUri.getURI() == null) {
            log.warn("Cannot replace url to internal url {} because the url is null.", internalVcsUrl);
            return vcsRepositoryUri;
        }

        try {
            String newInternalUrl = toInternalVcsUrl(vcsRepositoryUri.getURI().toString());
            return new VcsRepositoryUri(newInternalUrl);
        }
        catch (URISyntaxException e) {
            log.warn("Cannot replace url {} to {}: {}. Falling back to original url.", vcsRepositoryUri, internalVcsUrl, e.getMessage());
            return vcsRepositoryUri;
        }
    }

    /**
     * Replaces the url of the vcs repository uri to the internal url if it's
     * defined.
     *
     * @param vcsRepositoryUri the vcs repository uri
     * @return the vcs repository uri with the internal url
     */
    public String toInternalVcsUrl(String vcsRepositoryUri) {
        if (internalVcsUrl.isEmpty()) {
            return vcsRepositoryUri;
        }

        if (vcsRepositoryUri == null) {
            log.warn("Cannot replace url to internal url {} because the url is null.", internalVcsUrl);
            return null;
        }

        return replaceUrl(vcsRepositoryUri, internalVcsUrl.get());
    }

    /**
     * Replaces the url of the ci url to the internal url if it's
     * defined.
     *
     * @param ciUrl the ci url
     * @return the ci url with the internal url
     */
    public String toInternalCiUrl(String ciUrl) {
        if (internalCiUrl.isPresent()) {
            return replaceUrl(ciUrl, internalCiUrl.get());
        }
        else {
            return ciUrl;
        }
    }

    /**
     * Replaces the host and port of the url with the ones defined
     * in internalUrl
     *
     * @param urlToReplace the url that will be manipulated
     * @param internalUrl  the internal url
     * @return the manipulated url
     */
    @Nullable
    public String replaceUrl(String urlToReplace, URL internalUrl) {
        if (urlToReplace == null) {
            return null;
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(urlToReplace);
        return builder.host(internalUrl.getHost()).port(internalUrl.getPort()).toUriString();
    }
}
