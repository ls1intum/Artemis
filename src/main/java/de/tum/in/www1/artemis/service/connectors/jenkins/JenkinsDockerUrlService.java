package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.net.MalformedURLException;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;

@Profile("jenkins")
@Service
public class JenkinsDockerUrlService {

    @Value("${jenkins.docker.ci-url:#{null}}")
    private URL dockerCiUrl;

    @Value("${jenkins.docker.vcs-url:#{null}}")
    private URL dockerVcsUrl;

    private static final Logger log = LoggerFactory.getLogger(JenkinsDockerUrlService.class);

    /**
     * Returns the url pointing the the docker container hosting the vcs
     * if the vcs url is set. Returns the original url otherwise.
     * @param vcsRepositoryUrl the vcs repository url
     * @return the manipulated url
     */
    public VcsRepositoryUrl toDockerVcsUrl(VcsRepositoryUrl vcsRepositoryUrl) {
        if (dockerVcsUrl == null) {
            return vcsRepositoryUrl;
        }

        if (vcsRepositoryUrl.getURL() == null) {
            log.warn("Cannot replace url to docker url {} because the url is null.", dockerVcsUrl);
            return vcsRepositoryUrl;
        }

        try {
            String newDockerUrl = replaceUrl(vcsRepositoryUrl.getURL().toString(), dockerVcsUrl);
            return new VcsRepositoryUrl(newDockerUrl);
        }
        catch (MalformedURLException e) {
            log.warn("Cannot replace url {} to {}: {}. Falling back to original url.", vcsRepositoryUrl, dockerVcsUrl, e.getMessage());
            return vcsRepositoryUrl;
        }
    }

    /**
     * Returns the url pointing the the docker container of the ci
     * if the ci url is set. Returns the original url otherwise.
     * @param ciUrl The url of the ci
     * @return the manipulated url
     */
    public String toDockerCiUrl(String ciUrl) {
        if (dockerCiUrl != null) {
            return replaceUrl(ciUrl, dockerCiUrl);
        }
        else {
            return ciUrl;
        }
    }

    /**
     * Replaces the host and port of the url with the ones defined
     * in dockerUrl
     * @param urlToReplace the url that will be manipulated
     * @param dockerUrl the dokcer container url
     * @return the manipulated url
     */
    private String replaceUrl(String urlToReplace, URL dockerUrl) {
        if (urlToReplace == null) {
            return urlToReplace;
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(urlToReplace);
        return builder.host(dockerUrl.getHost()).port(dockerUrl.getPort()).toUriString();
    }
}
