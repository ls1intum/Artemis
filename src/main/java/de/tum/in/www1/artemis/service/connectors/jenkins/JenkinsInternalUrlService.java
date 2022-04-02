package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;

@Profile("jenkins")
@Service
public class JenkinsInternalUrlService {

    @Value("${jenkins.internal-urls.ci-url:#{null}}")
    private Optional<URL> internalCiUrl;

    @Value("${jenkins.internal-urls.vcs-url:#{null}}")
    private Optional<URL> internalVcsUrl;

    private static final Logger log = LoggerFactory.getLogger(JenkinsInternalUrlService.class);

    /**
     * Replaces the url of the vcs repository url to the internal url if it's
     * defined.
     * @param vcsRepositoryUrl the vcs repository url
     * @return the vcs repository url with the internal url
     */
    public VcsRepositoryUrl toInternalVcsUrl(VcsRepositoryUrl vcsRepositoryUrl) {
        if (vcsRepositoryUrl.getURI() == null) {
            log.warn("Cannot replace url to internal url {} because the url is null.", internalVcsUrl);
            return vcsRepositoryUrl;
        }

        try {
            String newInternalUrl = toInternalVcsUrl(vcsRepositoryUrl.getURI().toString());
            return new VcsRepositoryUrl(newInternalUrl);
        }
        catch (URISyntaxException e) {
            log.warn("Cannot replace url {} to {}: {}. Falling back to original url.", vcsRepositoryUrl, internalVcsUrl, e.getMessage());
            return vcsRepositoryUrl;
        }
    }

    /**
     * Replaces the url of the vcs repository url to the internal url if it's
     * defined.
     * @param vcsRepositoryUrl the vcs repository url
     * @return the vcs repository url with the internal url
     */
    public String toInternalVcsUrl(String vcsRepositoryUrl) {
        if (internalVcsUrl.isEmpty()) {
            return vcsRepositoryUrl;
        }

        if (vcsRepositoryUrl == null) {
            log.warn("Cannot replace url to internal url {} because the url is null.", internalVcsUrl);
            return vcsRepositoryUrl;
        }

        return replaceUrl(vcsRepositoryUrl, internalVcsUrl.get());
    }

    /**
     * Replaces the url of the ci url to the internal url if it's
     * defined.
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
     * @param urlToReplace the url that will be manipulated
     * @param internalUrl the internal url
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
